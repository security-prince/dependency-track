/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) Steve Springett. All Rights Reserved.
 */
package org.dependencytrack.resources.v1;

import alpine.auth.LdapConnectionWrapper;
import alpine.auth.PermissionRequired;
import alpine.model.MappedLdapGroup;
import alpine.model.Team;
import alpine.resources.AlpineResource;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import org.dependencytrack.auth.Permissions;
import org.dependencytrack.persistence.QueryManager;
import org.dependencytrack.resources.v1.vo.MappedLdapGroupRequest;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.validation.Validator;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * JAX-RS resources for processing LDAP group mapping requests.
 *
 * @author Steve Springett
 * @since 3.3.0
 */
@Path("/v1/ldap")
@Api(value = "ldap", authorizations = @Authorization(value = "X-Api-Key"))
public class LdapResource extends AlpineResource {

    @GET
    @Path("/groups")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Returns the DNs of all accessible groups within the directory",
            response = String.class,
            responseContainer = "List",
            notes = "This API performs a pass-thru query to the configured LDAP server."
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized")
    })
    @PermissionRequired(Permissions.Constants.ACCESS_MANAGEMENT)
    public Response retrieveLdapGroups () {
        final LdapConnectionWrapper ldap = new LdapConnectionWrapper();
        DirContext dirContext = null;
        try {
            dirContext = ldap.createDirContext();
            List<String> groups = ldap.getGroups(dirContext);
            return Response.ok(groups).build();
        } catch (NamingException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            ldap.closeQuietly(dirContext);
        }
    }

    @GET
    @Path("/team/{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Returns the DNs of all groups mapped to the specified team",
            response = String.class,
            responseContainer = "List"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "The UUID of the team could not be found"),
    })
    @PermissionRequired(Permissions.Constants.ACCESS_MANAGEMENT)
    public Response retrieveLdapGroups (@ApiParam(value = "The UUID of the team to retrieve mappings for", required = true)
                                        @PathParam("uuid") String uuid) {
        try (QueryManager qm = new QueryManager()) {
            final Team team = qm.getObjectByUuid(Team.class, uuid);
            if (team != null) {
                final List<MappedLdapGroup> mappings = qm.getMappedLdapGroups(team);
                return Response.ok(mappings).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("The UUID of the team could not be found.").build();
            }
        }
    }

    @PUT
    @Path("/mapping")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Adds a mapping",
            response = MappedLdapGroup.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "The UUID of the team could not be found"),
            @ApiResponse(code = 409, message = "A mapping with the same team and dn already exists")
    })
    @PermissionRequired(Permissions.Constants.ACCESS_MANAGEMENT)
    public Response addMapping(MappedLdapGroupRequest request) {
        final Validator validator = super.getValidator();
        failOnValidationError(
                validator.validateProperty(request, "team"),
                validator.validateProperty(request, "dn")
        );
        try (QueryManager qm = new QueryManager()) {
            Team team = qm.getObjectByUuid(Team.class, request.getTeam());
            if (team != null) {
                if (!qm.isMapped(team, request.getDn())) {
                    final MappedLdapGroup mapping = qm.createMappedLdapGroup(team, request.getDn());
                    return Response.ok(mapping).build();
                } else {
                    return Response.status(Response.Status.CONFLICT).entity("A mapping with the same team and dn already exists.").build();
                }
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("The UUID of the team could not be found.").build();
            }
        }
    }

    @DELETE
    @Path("/mapping/{uuid}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(
            value = "Removes a mapping",
            response = MappedLdapGroup.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 401, message = "Unauthorized"),
            @ApiResponse(code = 404, message = "The UUID of the mapping could not be found"),
    })
    @PermissionRequired(Permissions.Constants.ACCESS_MANAGEMENT)
    public Response deleteMapping(
            @ApiParam(value = "The UUID of the mapping to delete", required = true)
            @PathParam("uuid") String uuid) {
        try (QueryManager qm = new QueryManager()) {
            final MappedLdapGroup mapping = qm.getObjectByUuid(MappedLdapGroup.class, uuid);
            if (mapping != null) {
                qm.delete(mapping);
                return Response.status(Response.Status.NO_CONTENT).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).entity("The UUID of the mapping could not be found.").build();
            }
        }
    }
}

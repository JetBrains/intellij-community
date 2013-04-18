package com.intellij.openapi.externalSystem.model.project.id;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityType;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represent unique identifier object for any external system or ide project structure entity (module, library, library dependency etc).
 * <p/>
 * We need an entity id, for example, at the <code>'sync project structure'</code> tree - the model needs to keep mappings
 * between the existing tree nodes and corresponding project structure entities. However, we can't keep an entity as is because
 * it may cause memory leak and be not safe (the entity's hash code may be changed).
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/14/12 12:26 PM
 */
public interface ProjectEntityId {

  @NotNull
  ProjectEntityType getType();

  @NotNull
  ProjectSystemId getOwner();

  void setOwner(@NotNull ProjectSystemId owner);

  @Nullable
  Object mapToEntity(@NotNull ProjectStructureServices services, @NotNull Project ideProject);
}

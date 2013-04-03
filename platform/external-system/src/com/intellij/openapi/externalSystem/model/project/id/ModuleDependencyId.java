package com.intellij.openapi.externalSystem.model.project.id;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityType;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2/14/12 2:06 PM
 */
public class ModuleDependencyId extends AbstractExternalDependencyId {

  public ModuleDependencyId(@NotNull ProjectSystemId owner, @NotNull String ownerModuleName, @NotNull String dependencyModuleName) {
    super(ProjectEntityType.MODULE_DEPENDENCY, owner, ownerModuleName, dependencyModuleName);
  }

  @Nullable
  @Override
  public Object mapToEntity(@NotNull ProjectStructureServices services, @NotNull Project ideProject) {
    return services.getProjectStructureHelper().findModuleDependency(getOwnerModuleName(), getDependencyName(), getOwner(), ideProject);
  }

  @Override
  public String toString() {
    return String.format("module dependency:owner module='%s'|dependency module='%s'", getOwnerModuleName(), getDependencyName());
  }
}

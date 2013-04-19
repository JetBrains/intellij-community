package com.intellij.openapi.externalSystem.model.project.id;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityType;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2/14/12 1:59 PM
 */
public class LibraryDependencyId extends AbstractExternalDependencyId {

  public LibraryDependencyId(@NotNull ProjectSystemId owner, @NotNull String moduleName, @NotNull String libraryName) {
    super(ProjectEntityType.LIBRARY_DEPENDENCY, owner, moduleName, libraryName);
  }

  @Nullable
  @Override
  public Object mapToEntity(@NotNull ProjectStructureServices services, @NotNull Project ideProject) {
    return services.getProjectStructureHelper().findLibraryDependency(getOwnerModuleName(), getDependencyName(), getOwner(), ideProject);
  }

  @NotNull
  public LibraryId getLibraryId() {
    return new LibraryId(getOwner(), getDependencyName());
  }
  
  @Override
  public String toString() {
    return String.format("library dependency:owner module='%s'|library='%s'", getOwnerModuleName(), getDependencyName());
  }
}

package com.intellij.openapi.externalSystem.model.project.id;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityType;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2/14/12 1:55 PM
 */
public class ModuleId extends AbstractExternalEntityId {

  @NotNull private final String myModuleName;
  
  public ModuleId(@NotNull ProjectSystemId owner, @NotNull String moduleName) {
    super(ProjectEntityType.MODULE, owner);
    myModuleName = moduleName;
  }

  @Nullable
  @Override
  public Object mapToEntity(@NotNull ProjectStructureServices services, @NotNull Project ideProject) {
    return services.getProjectStructureHelper().findModule(myModuleName, getOwner(), ideProject);
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myModuleName.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    ModuleId that = (ModuleId)o;
    return myModuleName.equals(that.myModuleName);
  }

  @Override
  public String toString() {
    return "module '" + myModuleName + "'";
  }
}

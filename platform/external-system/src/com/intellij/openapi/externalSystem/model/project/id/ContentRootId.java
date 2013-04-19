package com.intellij.openapi.externalSystem.model.project.id;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityType;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 2/16/12 12:22 PM
 */
public class ContentRootId extends AbstractExternalEntityId {
  
  @NotNull private final String myModuleName;
  @NotNull private final String myRootPath;
  
  public ContentRootId(@NotNull ProjectSystemId owner, @NotNull String moduleName, @NotNull String rootPath) {
    super(ProjectEntityType.CONTENT_ROOT, owner);
    myModuleName = moduleName;
    myRootPath = rootPath;
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public String getRootPath() {
    return myRootPath;
  }

  @NotNull
  public ModuleId getModuleId() {
    return new ModuleId(getOwner(), myModuleName);
  }

  @Nullable
  @Override
  public Object mapToEntity(@NotNull ProjectStructureServices services, @NotNull Project ideProject) {
    return services.getProjectStructureHelper().findContentRoot(this, getOwner(), ideProject);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myModuleName.hashCode();
    result = 31 * result + myRootPath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    ContentRootId that = (ContentRootId)o;

    if (!myModuleName.equals(that.myModuleName)) return false;
    if (!myRootPath.equals(that.myRootPath)) return false;

    return true;
  }

  @Override
  public String toString() {
    return "content root '" + myRootPath + "'";
  }
}

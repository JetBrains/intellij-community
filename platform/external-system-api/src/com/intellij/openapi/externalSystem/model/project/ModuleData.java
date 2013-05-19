package com.intellij.openapi.externalSystem.model.project;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 8/8/11 12:11 PM
 */
public class ModuleData extends AbstractNamedData implements Named, ExternalConfigPathAware {

  private static final long serialVersionUID = 1L;

  @NotNull private final Map<ExternalSystemSourceType, String> myCompileOutputPaths = ContainerUtilRt.newHashMap();

  @NotNull private final String myModuleTypeId;
  @NotNull private final String myExternalConfigPath;
  @NotNull private String myModuleFilePath;
  private boolean myInheritProjectCompileOutputPath = true;

  public ModuleData(@NotNull ProjectSystemId owner,
                    @NotNull String typeId,
                    @NotNull String name,
                    @NotNull String moduleFileDirectoryPath,
                    @NotNull String externalConfigPath)
  {
    super(owner, name);
    myModuleTypeId = typeId;
    myExternalConfigPath = externalConfigPath;
    setModuleFileDirectoryPath(moduleFileDirectoryPath);
  }

  @NotNull
  public String getModuleTypeId() {
    return myModuleTypeId;
  }

  @NotNull
  @Override
  public String getLinkedExternalProjectPath() {
    return myExternalConfigPath;
  }

  @NotNull
  public String getModuleFilePath() {
    return myModuleFilePath;
  }

  public void setModuleFileDirectoryPath(@NotNull String path) {
    myModuleFilePath = ExternalSystemApiUtil.toCanonicalPath(path + "/" + getName() + ModuleFileType.DOT_DEFAULT_EXTENSION);
  }

  public boolean isInheritProjectCompileOutputPath() {
    return myInheritProjectCompileOutputPath;
  }

  public void setInheritProjectCompileOutputPath(boolean inheritProjectCompileOutputPath) {
    myInheritProjectCompileOutputPath = inheritProjectCompileOutputPath;
  }

  /**
   * Allows to get file system path of the compile output of the source of the target type.
   *
   * @param type  target source type
   * @return      file system path to use for compile output for the target source type;
   *              {@link JavaProjectData#getCompileOutputPath() project compile output path} should be used if current module
   *              doesn't provide specific compile output path
   */
  @Nullable
  public String getCompileOutputPath(@NotNull ExternalSystemSourceType type) {
    return myCompileOutputPaths.get(type);
  }

  public void setCompileOutputPath(@NotNull ExternalSystemSourceType type, @Nullable String path) {
    if (path == null) {
      myCompileOutputPaths.remove(type);
      return;
    }
    myCompileOutputPaths.put(type, ExternalSystemApiUtil.toCanonicalPath(path));
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myModuleTypeId.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    return myModuleTypeId.equals(((ModuleData)o).myModuleTypeId);
  }

  @Override
  public String toString() {
    return String.format("module '%s'", getName());
  }
}

package com.intellij.openapi.externalSystem.model.project;

import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 8/8/11 12:11 PM
 */
public class ModuleData extends AbstractNamedData implements Named, ExternalConfigPathAware, Identifiable {

  private static final long serialVersionUID = 1L;

  @NotNull private final Map<ExternalSystemSourceType, String> myCompileOutputPaths = ContainerUtil.newHashMap();
  @NotNull private final String myId;
  @NotNull private final String myModuleTypeId;
  @NotNull private final String myExternalConfigPath;
  @NotNull private String myModuleFilePath;
  @Nullable private String group;
  @Nullable private String version;
  @NotNull private List<File> myArtifacts;

  private boolean myInheritProjectCompileOutputPath = true;

  @Deprecated
  public ModuleData(@NotNull ProjectSystemId owner,
                    @NotNull String typeId,
                    @NotNull String name,
                    @NotNull String moduleFileDirectoryPath,
                    @NotNull String externalConfigPath) {
    this("", owner, typeId, name, moduleFileDirectoryPath, externalConfigPath);
  }

  public ModuleData(@NotNull String id,
                    @NotNull ProjectSystemId owner,
                    @NotNull String typeId,
                    @NotNull String name,
                    @NotNull String moduleFileDirectoryPath,
                    @NotNull String externalConfigPath) {
    super(owner, name, name.replaceAll("(/|\\\\)", "_"));
    myId = id;
    myModuleTypeId = typeId;
    myExternalConfigPath = externalConfigPath;
    myArtifacts = Collections.emptyList();
    setModuleFileDirectoryPath(moduleFileDirectoryPath);
  }

  @NotNull
  @Override
  public String getId() {
    return myId;
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
    myModuleFilePath = ExternalSystemApiUtil.toCanonicalPath(path + "/" + getInternalName() + ModuleFileType.DOT_DEFAULT_EXTENSION);
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

  @Nullable
  public String getGroup() {
    return group;
  }

  public void setGroup(@Nullable String group) {
    this.group = group;
  }

  @Nullable
  public String getVersion() {
    return version;
  }

  public void setVersion(@Nullable String version) {
    this.version = version;
  }

  @NotNull
  public List<File> getArtifacts() {
    return myArtifacts;
  }

  public void setArtifacts(@NotNull List<File> artifacts) {
    myArtifacts = artifacts;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ModuleData)) return false;
    if (!super.equals(o)) return false;

    ModuleData that = (ModuleData)o;

    if (group != null ? !group.equals(that.group) : that.group != null) return false;
    if (!myModuleTypeId.equals(that.myModuleTypeId)) return false;
    if (version != null ? !version.equals(that.version) : that.version != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myModuleTypeId.hashCode();
    result = 31 * result + (group != null ? group.hashCode() : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return String.format("module '%s:%s:%s'",
                         group == null ? "" : group,
                         getExternalName(),
                         version == null ? "" : version);
  }
}

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
  @NotNull private String myModuleFileDirectoryPath;
  @Nullable private String myGroup;
  @Nullable private String myVersion;
  @Nullable private String myDescription;
  @NotNull private List<File> myArtifacts;
  @Nullable private String[] myIdeModuleGroup;
  @Nullable  private String mySourceCompatibility;
  @Nullable private String myTargetCompatibility;
  @Nullable private String mySdkName;
  @Nullable private String myProductionModuleId;

  private boolean myInheritProjectCompileOutputPath = true;

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
    myModuleFileDirectoryPath = moduleFileDirectoryPath;
  }

  protected ModuleData(@NotNull String id,
                       @NotNull ProjectSystemId owner,
                       @NotNull String typeId,
                       @NotNull String externalName,
                       @NotNull String internalName,
                       @NotNull String moduleFileDirectoryPath,
                       @NotNull String externalConfigPath) {
    super(owner, externalName, internalName);
    myId = id;
    myModuleTypeId = typeId;
    myExternalConfigPath = externalConfigPath;
    myArtifacts = Collections.emptyList();
    myModuleFileDirectoryPath = moduleFileDirectoryPath;
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
    return ExternalSystemApiUtil
      .toCanonicalPath(myModuleFileDirectoryPath + "/" + getInternalName() + ModuleFileType.DOT_DEFAULT_EXTENSION);
  }

  @NotNull
  public String getModuleFileDirectoryPath() {
    return myModuleFileDirectoryPath;
  }

  public void setModuleFileDirectoryPath(@NotNull String path) {
    myModuleFileDirectoryPath = path;
  }

  /**
   * @return an internal id of production module corresponding to a test-only module, this information is used to populate
   * {@link com.intellij.openapi.roots.TestModuleProperties}
   */
  @Nullable
  public String getProductionModuleId() {
    return myProductionModuleId;
  }

  public void setProductionModuleId(@Nullable String productionModuleId) {
    myProductionModuleId = productionModuleId;
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
    return myGroup;
  }

  public void setGroup(@Nullable String group) {
    this.myGroup = group;
  }

  @Nullable
  public String getVersion() {
    return myVersion;
  }

  public void setVersion(@Nullable String version) {
    this.myVersion = version;
  }

  @Nullable
  public String getDescription() {
    return myDescription;
  }

  public void setDescription(@Nullable String description) {
    this.myDescription = description;
  }

  @NotNull
  public List<File> getArtifacts() {
    return myArtifacts;
  }

  public void setArtifacts(@NotNull List<File> artifacts) {
    myArtifacts = artifacts;
  }

  @Nullable
  public String[] getIdeModuleGroup() {
    return myIdeModuleGroup;
  }

  public void setIdeModuleGroup(@Nullable String[] ideModuleGroup) {
    this.myIdeModuleGroup = ideModuleGroup;
  }

  @Nullable
  public String getSourceCompatibility() {
    return mySourceCompatibility;
  }

  public void setSourceCompatibility(@Nullable String sourceCompatibility) {
    mySourceCompatibility = sourceCompatibility;
  }

  @Nullable
  public String getTargetCompatibility() {
    return myTargetCompatibility;
  }

  public void setTargetCompatibility(@Nullable String targetCompatibility) {
    myTargetCompatibility = targetCompatibility;
  }

  @Nullable
  public String getSdkName() {
    return mySdkName;
  }

  public void setSdkName(@Nullable String sdkName) {
    mySdkName = sdkName;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ModuleData)) return false;
    if (!super.equals(o)) return false;

    ModuleData that = (ModuleData)o;

    if (!myId.equals(that.myId)) return false;
    if (myGroup != null ? !myGroup.equals(that.myGroup) : that.myGroup != null) return false;
    if (!myModuleTypeId.equals(that.myModuleTypeId)) return false;
    if (myVersion != null ? !myVersion.equals(that.myVersion) : that.myVersion != null) return false;
    if (myDescription != null ? !myDescription.equals(that.myDescription) : that.myDescription != null) return false;
    if (mySdkName != null ? !mySdkName.equals(that.mySdkName) : that.mySdkName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myId.hashCode();
    result = 31 * result + myModuleTypeId.hashCode();
    result = 31 * result + (myGroup != null ? myGroup.hashCode() : 0);
    result = 31 * result + (myVersion != null ? myVersion.hashCode() : 0);
    result = 31 * result + (myDescription != null ? myDescription.hashCode() : 0);
    result = 31 * result + (mySdkName != null ? mySdkName.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return getId();
  }
}

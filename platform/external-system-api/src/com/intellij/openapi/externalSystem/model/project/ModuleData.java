// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.serialization.Property;
import com.intellij.serialization.PropertyMapping;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.openapi.util.text.StringUtil.nullize;

/**
 * @author Denis Zhdanov
 */
@SuppressWarnings("JavadocReference")
public class ModuleData extends AbstractNamedData implements Named, ExternalConfigPathAware, Identifiable {
  @NotNull private final Map<ExternalSystemSourceType, String> compileOutputPaths = new HashMap<>();
  @NotNull private final Map<ExternalSystemSourceType, String> externalCompilerOutputPaths = new HashMap<>();
  @Nullable private Map<String, String> properties;
  @NotNull private final String id;
  @NotNull private final String moduleTypeId;
  @NotNull private final String externalConfigPath;
  @NotNull private final String moduleFileDirectoryPath;
  @Nullable private String group;
  @Nullable private String version;
  @Nullable private String description;
  @NotNull private List<File> artifacts;
  private String @Nullable [] ideModuleGroup;

  @Nullable private String sourceCompatibility;
  @Nullable private String targetCompatibility;
  @Nullable private String sdkName;

  private boolean isSetSourceCompatibility = false;
  private boolean isSetTargetCompatibility = false;
  private boolean isSetSdkName = false;

  @Nullable private String productionModuleId;
  @NotNull private String moduleName;

  @Nullable
  @Property(allowedTypes = {LibraryData.class, ProjectId.class})
  private ProjectCoordinate publication;

  private boolean inheritProjectCompileOutputPath = true;
  private boolean useExternalCompilerOutput;

  @PropertyMapping({"id", "owner", "moduleTypeId", "externalName", "moduleFileDirectoryPath", "externalConfigPath"})
  public ModuleData(@NotNull String id,
                    @NotNull ProjectSystemId owner,
                    @NotNull String moduleTypeId,
                    @NotNull String externalName,
                    @NotNull String moduleFileDirectoryPath,
                    @NotNull String externalConfigPath) {
    super(owner, externalName, externalName.replaceAll("(/|\\\\)", "_"));
    this.id = id;
    this.moduleTypeId = moduleTypeId;
    this.externalConfigPath = externalConfigPath;
    artifacts = Collections.emptyList();
    this.moduleFileDirectoryPath = moduleFileDirectoryPath;
    this.moduleName = externalName;
  }

  protected ModuleData(@NotNull String id,
                       @NotNull ProjectSystemId owner,
                       @NotNull String typeId,
                       @NotNull String externalName,
                       @NotNull String internalName,
                       @NotNull String moduleFileDirectoryPath,
                       @NotNull String externalConfigPath) {
    super(owner, externalName, internalName);
    this.id = id;
    moduleTypeId = typeId;
    this.externalConfigPath = externalConfigPath;
    artifacts = Collections.emptyList();
    this.moduleFileDirectoryPath = moduleFileDirectoryPath;
    this.moduleName = externalName;
  }

  @NotNull
  @Override
  public @NlsSafe String getId() {
    return id;
  }

  @NotNull
  public String getModuleTypeId() {
    return moduleTypeId;
  }

  @NotNull
  @Override
  public String getLinkedExternalProjectPath() {
    return externalConfigPath;
  }

  @NotNull
  public String getModuleFileDirectoryPath() {
    return moduleFileDirectoryPath;
  }

  /**
   * @return an internal id of production module corresponding to a test-only module, this information is used to populate
   * {@link com.intellij.openapi.roots.TestModuleProperties}
   */
  @Nullable
  public String getProductionModuleId() {
    return productionModuleId;
  }

  public void setProductionModuleId(@Nullable String productionModuleId) {
    this.productionModuleId = productionModuleId;
  }

  public boolean isInheritProjectCompileOutputPath() {
    return inheritProjectCompileOutputPath;
  }

  public void setInheritProjectCompileOutputPath(boolean inheritProjectCompileOutputPath) {
    this.inheritProjectCompileOutputPath = inheritProjectCompileOutputPath;
  }

  /**
   * Allows to get file system path of the compile output of the source of the target type.
   *
   * @param type  target source type
   * @return      file system path to use for compile output for the target source type;
   *              {@link com.intellij.externalSystem.JavaProjectData#getCompileOutputPath() project compile output path} should be used if current module
   *              doesn't provide specific compile output path
   */
  @Nullable
  public String getCompileOutputPath(@NotNull ExternalSystemSourceType type) {
    //noinspection ConstantConditions
    return useExternalCompilerOutput && externalCompilerOutputPaths != null
           ? externalCompilerOutputPaths.get(type)
           : compileOutputPaths.get(type);
  }

  public void setCompileOutputPath(@NotNull ExternalSystemSourceType type, @Nullable String path) {
    updatePath(compileOutputPaths, type, path);
  }

  public void setExternalCompilerOutputPath(@NotNull ExternalSystemSourceType type, @Nullable String path) {
    updatePath(externalCompilerOutputPaths, type, path);
  }

  public void useExternalCompilerOutput(boolean useExternalCompilerOutput) {
    this.useExternalCompilerOutput = useExternalCompilerOutput;
  }

  @Nullable
  public String getGroup() {
    return group;
  }

  public void setGroup(@Nullable String group) {
    this.group = group;
  }

  @Nullable
  public ProjectCoordinate getPublication() {
    return publication;
  }

  public void setPublication(@Nullable ProjectCoordinate publication) {
    this.publication = publication;
  }

  @Nullable
  public String getVersion() {
    return version;
  }

  public void setVersion(@Nullable String version) {
    this.version = version;
  }

  @Nullable
  public @NlsSafe String getDescription() {
    return description;
  }

  public void setDescription(@Nullable String description) {
    this.description = description;
  }

  @NotNull
  public List<File> getArtifacts() {
    return artifacts;
  }

  public void setArtifacts(@NotNull List<File> artifacts) {
    this.artifacts = artifacts;
  }

  public String @Nullable [] getIdeModuleGroup() {
    return ideModuleGroup;
  }

  public void setIdeModuleGroup(String @Nullable [] ideModuleGroup) {
    this.ideModuleGroup = ideModuleGroup;
  }

  /**
   * @deprecated use {@link JavaModuleData#getLanguageLevel} instead
   */
  @Nullable
  @Deprecated(forRemoval = true)
  public String getSourceCompatibility() {
    return sourceCompatibility;
  }

  /**
   * @deprecated use {@link JavaModuleData#setLanguageLevel} instead
   */
  @Deprecated(forRemoval = true)
  public void setSourceCompatibility(@Nullable String sourceCompatibility) {
    this.isSetSourceCompatibility = true;
    this.sourceCompatibility = sourceCompatibility;
  }

  /**
   * @deprecated use {@link JavaModuleData#getTargetBytecodeVersion} instead
   */
  @Nullable
  @Deprecated(forRemoval = true)
  public String getTargetCompatibility() {
    return targetCompatibility;
  }

  /**
   * @deprecated use {@link JavaModuleData#setTargetBytecodeVersion} instead
   */
  @Deprecated(forRemoval = true)
  public void setTargetCompatibility(@Nullable String targetCompatibility) {
    this.isSetTargetCompatibility = true;
    this.targetCompatibility = targetCompatibility;
  }

  /**
   * @deprecated use {@link ModuleSdkData#getSdkName} instead
   */
  @Nullable
  @Deprecated(forRemoval = true)
  public String getSdkName() {
    return sdkName;
  }

  /**
   * @deprecated use {@link ModuleSdkData#setSdkName} instead
   */
  @Deprecated(forRemoval = true)
  public void setSdkName(@Nullable String sdkName) {
    this.isSetSdkName = true;
    this.sdkName = sdkName;
  }

  // Remove it in version 2021.1
  //<editor-fold desc="Backward compatibility preserving methods">
  @Deprecated(forRemoval = true)
  @SuppressWarnings({"MissingDeprecatedAnnotation", "DeprecatedIsStillUsed"})
  public void internalSetSourceCompatibility(@Nullable String sourceCompatibility) {
    this.sourceCompatibility = sourceCompatibility;
  }

  @Deprecated(forRemoval = true)
  @SuppressWarnings({"MissingDeprecatedAnnotation", "DeprecatedIsStillUsed"})
  public void internalSetTargetCompatibility(@Nullable String targetCompatibility) {
    this.targetCompatibility = targetCompatibility;
  }

  @Deprecated(forRemoval = true)
  @SuppressWarnings({"MissingDeprecatedAnnotation", "DeprecatedIsStillUsed"})
  public void internalSetSdkName(@Nullable String sdkName) {
    this.sdkName = sdkName;
  }

  @Deprecated(forRemoval = true)
  @SuppressWarnings("MissingDeprecatedAnnotation")
  public boolean isSetSourceCompatibility() {
    return isSetSourceCompatibility;
  }

  @Deprecated(forRemoval = true)
  @SuppressWarnings("MissingDeprecatedAnnotation")
  public boolean isSetTargetCompatibility() {
    return isSetTargetCompatibility;
  }

  @Deprecated(forRemoval = true)
  @SuppressWarnings({"MissingDeprecatedAnnotation", "DeprecatedIsStillUsed"})
  public boolean isSetSdkName() {
    return isSetSdkName;
  }
  //</editor-fold>

  @Nullable
  public String getProperty(String key) {
    return properties != null ? properties.get(key) : null;
  }

  public void setProperty(String key, String value) {
    if (properties == null) {
      properties = new HashMap<>();
    }
    properties.put(key, value);
  }

  @NotNull
  public String getModuleName() {
    return moduleName;
  }

  public void setModuleName(@NotNull String moduleName) {
    this.moduleName = moduleName;
  }

  @NotNull
  public String getIdeGrouping() {
    if (ideModuleGroup != null) {
      return join(ideModuleGroup, ".");
    } else {
      return getInternalName();
    }
  }

  @Nullable
  public String getIdeParentGrouping() {
    if (ideModuleGroup != null) {
      return nullize(join(ArrayUtil.remove(ideModuleGroup, ideModuleGroup.length - 1), "."));
    } else {
      final String name = getInternalName();
      int i = name.lastIndexOf("." + moduleName);
      if (i > -1) {
        return name.substring(0, i);
      } else {
        return null;
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ModuleData)) return false;
    if (!super.equals(o)) return false;

    ModuleData that = (ModuleData)o;

    if (!id.equals(that.id)) return false;
    if (!externalConfigPath.equals(that.externalConfigPath)) return false;
    if (group != null ? !group.equals(that.group) : that.group != null) return false;
    if (!moduleTypeId.equals(that.moduleTypeId)) return false;
    if (version != null ? !version.equals(that.version) : that.version != null) return false;
    if (description != null ? !description.equals(that.description) : that.description != null) return false;
    if (sdkName != null ? !sdkName.equals(that.sdkName) : that.sdkName != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + id.hashCode();
    result = 31 * result + externalConfigPath.hashCode();
    result = 31 * result + moduleTypeId.hashCode();
    result = 31 * result + (group != null ? group.hashCode() : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    result = 31 * result + (description != null ? description.hashCode() : 0);
    result = 31 * result + (sdkName != null ? sdkName.hashCode() : 0);
    return result;
  }

  @Override
  public @NlsSafe String toString() {
    return getId();
  }

  private static void updatePath(Map<ExternalSystemSourceType, String> paths,
                                 @NotNull ExternalSystemSourceType type,
                                 @Nullable String path) {
    if (paths == null) return;
    if (path == null) {
      paths.remove(type);
      return;
    }
    paths.put(type, ExternalSystemApiUtil.toCanonicalPath(path));
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public class ModuleData extends AbstractNamedData implements Named, ExternalConfigPathAware, Identifiable {
  private final @NotNull Map<ExternalSystemSourceType, String> compileOutputPaths = new HashMap<>();
  private final @NotNull Map<ExternalSystemSourceType, String> externalCompilerOutputPaths = new HashMap<>();
  private @Nullable Map<String, String> properties;
  private final @NotNull String id;
  private final @NotNull String moduleTypeId;
  private final @NotNull String externalConfigPath;
  private final @NotNull String moduleFileDirectoryPath;
  private @Nullable String group;
  private @Nullable String version;
  private @Nullable String description;
  private @NotNull List<File> artifacts;
  private String @Nullable [] ideModuleGroup;

  private @Nullable String productionModuleId;
  private @NotNull String moduleName;

  @Property(allowedTypes = {LibraryData.class, ProjectId.class}) private @Nullable ProjectCoordinate publication;

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

  @Override
  public @NotNull @NlsSafe String getId() {
    return id;
  }

  public @NotNull String getModuleTypeId() {
    return moduleTypeId;
  }

  @Override
  public @NotNull String getLinkedExternalProjectPath() {
    return externalConfigPath;
  }

  public @NotNull String getModuleFileDirectoryPath() {
    return moduleFileDirectoryPath;
  }

  /**
   * @return an internal id of production module corresponding to a test-only module, this information is used to populate
   * {@link com.intellij.openapi.roots.TestModuleProperties}
   */
  public @Nullable String getProductionModuleId() {
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
  public @Nullable String getCompileOutputPath(@NotNull ExternalSystemSourceType type) {
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

  public @Nullable String getGroup() {
    return group;
  }

  public void setGroup(@Nullable String group) {
    this.group = group;
  }

  public @Nullable ProjectCoordinate getPublication() {
    return publication;
  }

  public void setPublication(@Nullable ProjectCoordinate publication) {
    this.publication = publication;
  }

  public @Nullable String getVersion() {
    return version;
  }

  public void setVersion(@Nullable String version) {
    this.version = version;
  }

  public @Nullable @NlsSafe String getDescription() {
    return description;
  }

  public void setDescription(@Nullable String description) {
    this.description = description;
  }

  public @NotNull List<File> getArtifacts() {
    return artifacts;
  }

  public void setArtifacts(@NotNull List<File> artifacts) {
    this.artifacts = artifacts;
  }

  public String @Nullable [] getIdeModuleGroup() {
    return ideModuleGroup;
  }

  /**
   * Set or remove explicit module group for this module.
   *
   * @deprecated explicit module groups are replaced by automatic module grouping accordingly to qualified names of modules
   * ([IDEA-166061](https://youtrack.jetbrains.com/issue/IDEA-166061) for details), so this method must not be used anymore, group names
   * must be prepended to the module name, separated by dots, instead.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public void setIdeModuleGroup(String @Nullable [] ideModuleGroup) {
    this.ideModuleGroup = ideModuleGroup;
  }

  public @Nullable String getProperty(String key) {
    return properties != null ? properties.get(key) : null;
  }

  public void setProperty(String key, String value) {
    if (properties == null) {
      properties = new HashMap<>();
    }
    properties.put(key, value);
  }

  public @NotNull String getModuleName() {
    return moduleName;
  }

  public void setModuleName(@NotNull String moduleName) {
    this.moduleName = moduleName;
  }

  public @NotNull String getIdeGrouping() {
    if (ideModuleGroup != null) {
      return join(ideModuleGroup, ".");
    } else {
      return getInternalName();
    }
  }

  public @Nullable String getIdeParentGrouping() {
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
    if (!(o instanceof ModuleData that)) return false;
    if (!super.equals(o)) return false;

    if (!id.equals(that.id)) return false;
    if (!externalConfigPath.equals(that.externalConfigPath)) return false;
    if (group != null ? !group.equals(that.group) : that.group != null) return false;
    if (!moduleTypeId.equals(that.moduleTypeId)) return false;
    if (version != null ? !version.equals(that.version) : that.version != null) return false;
    if (description != null ? !description.equals(that.description) : that.description != null) return false;

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

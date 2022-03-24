// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.externalSystem;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.externalSystem.model.project.ProjectSdkData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaProjectData extends AbstractExternalEntityData {
  public static final Key<JavaProjectData> KEY = Key.create(JavaProjectData.class, ProjectKeys.PROJECT.getProcessingWeight() + 1);

  private static final Logger LOG = Logger.getInstance(JavaProjectData.class);

  private static final Pattern JDK_VERSION_PATTERN = Pattern.compile(".*1.(\\d+).*");

  private boolean isSetJdkVersion = false;
  @NotNull private JavaSdkVersion jdkVersion;

  @NotNull private String compileOutputPath;
  @NotNull private LanguageLevel languageLevel;
  @Nullable private String targetBytecodeVersion;

  public JavaProjectData(@NotNull ProjectSystemId owner, @NotNull String compileOutputPath) {
    this(owner, compileOutputPath, null, null);
  }

  @PropertyMapping({"owner", "compileOutputPath", "languageLevel", "targetBytecodeVersion"})
  public JavaProjectData(
    @NotNull ProjectSystemId owner,
    @NotNull String compileOutputPath,
    @Nullable LanguageLevel languageLevel,
    @Nullable String targetBytecodeVersion
  ) {
    this(owner, compileOutputPath, null, languageLevel, targetBytecodeVersion);
  }

  /**
   * @deprecated use {@link JavaProjectData#JavaProjectData(ProjectSystemId, String, LanguageLevel, String)} instead
   */
  @NotNull
  @Deprecated(forRemoval = true)
  @SuppressWarnings("DeprecatedIsStillUsed")
  public JavaProjectData(
    @NotNull ProjectSystemId owner,
    @NotNull String compileOutputPath,
    @Nullable JavaSdkVersion jdkVersion,
    @Nullable LanguageLevel languageLevel,
    @Nullable String targetBytecodeVersion
  ) {
    super(owner);

    this.compileOutputPath = compileOutputPath;
    this.jdkVersion = jdkVersion != null ? jdkVersion : JavaSdkVersion.fromLanguageLevel(LanguageLevel.HIGHEST);
    this.languageLevel = languageLevel != null ? languageLevel : LanguageLevel.HIGHEST;
    this.targetBytecodeVersion = targetBytecodeVersion;
  }

  @NotNull
  public String getCompileOutputPath() {
    return compileOutputPath;
  }

  public void setCompileOutputPath(@NotNull String compileOutputPath) {
    this.compileOutputPath = ExternalSystemApiUtil.toCanonicalPath(compileOutputPath);
  }

  /**
   * @deprecated use {@link ProjectSdkData#getSdkName()} instead
   */
  @NotNull
  @Deprecated(forRemoval = true)
  public JavaSdkVersion getJdkVersion() {
    return jdkVersion;
  }

  @ApiStatus.Internal
  public boolean isSetJdkVersion() {
    return isSetJdkVersion;
  }

  /**
   * @deprecated needed to support backward compatibility
   */
  @Nullable
  @Deprecated(forRemoval = true)
  public static JavaSdkVersion resolveSdkVersion(@Nullable String jdk) {
    if (jdk == null) {
      return null;
    }
    try {
      int version = Integer.parseInt(jdk.trim());
      JavaSdkVersion sdkVersion = resolveSdkVersion(version);
      if (sdkVersion != null) {
        return sdkVersion;
      }
    }
    catch (NumberFormatException e) {
      // Ignore.
    }

    Matcher matcher = JDK_VERSION_PATTERN.matcher(jdk);
    if (!matcher.matches()) {
      return null;
    }
    String versionAsString = matcher.group(1);
    try {
      return resolveSdkVersion(Integer.parseInt(versionAsString));
    }
    catch (NumberFormatException e) {
      // Ignore.
    }
    return null;
  }

  @Nullable
  private static JavaSdkVersion resolveSdkVersion(int version) {
    if (version < 0 || version >= JavaSdkVersion.values().length) {
      LOG.warn(String.format(
        "Unsupported jdk version detected (%d). Expected to get number from range [0; %d]", version, JavaSdkVersion.values().length
      ));
      return null;
    }
    for (JavaSdkVersion sdkVersion : JavaSdkVersion.values()) {
      if (sdkVersion.ordinal() == version) {
        return sdkVersion;
      }
    }
    assert false : version + ", max value: " + JavaSdkVersion.values().length;
    return null;
  }

  @NotNull
  public LanguageLevel getLanguageLevel() {
    return languageLevel;
  }

  public void setLanguageLevel(@NotNull LanguageLevel level) {
    languageLevel = level;
  }

  public void setLanguageLevel(@Nullable String languageLevel) {
    LanguageLevel level = LanguageLevel.parse(languageLevel);
    if (level != null) {
      this.languageLevel = level;
    }
  }

  @Nullable
  public String getTargetBytecodeVersion() {
    return targetBytecodeVersion;
  }

  public void setTargetBytecodeVersion(@Nullable String targetBytecodeVersion) {
    this.targetBytecodeVersion = targetBytecodeVersion;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Objects.hashCode(jdkVersion);
    result = 31 * result + languageLevel.hashCode();
    result = 31 * result + Objects.hashCode(targetBytecodeVersion);
    result = 31 * result + compileOutputPath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    JavaProjectData project = (JavaProjectData)o;

    if (!compileOutputPath.equals(project.compileOutputPath)) return false;
    if (Objects.equals(jdkVersion, project.jdkVersion)) return false;
    if (Objects.equals(languageLevel, project.languageLevel)) return false;
    if (Objects.equals(targetBytecodeVersion, project.targetBytecodeVersion)) return false;

    return true;
  }

  @Override
  public String toString() {
    return "java project";
  }
}

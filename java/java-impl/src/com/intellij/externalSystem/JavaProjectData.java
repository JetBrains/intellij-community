// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalSystem;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.externalSystem.model.project.ProjectSdkData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.JavaRelease;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.serialization.PropertyMapping;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaProjectData extends AbstractExternalEntityData {
  public static final Key<JavaProjectData> KEY = Key.create(JavaProjectData.class, ProjectKeys.PROJECT.getProcessingWeight() + 1);

  private static final Logger LOG = Logger.getInstance(JavaProjectData.class);

  private static final Pattern JDK_VERSION_PATTERN = Pattern.compile(".*1.(\\d+).*");

  private @Nullable JavaSdkVersion jdkVersion;

  private @NotNull String compileOutputPath;
  private @Nullable LanguageLevel languageLevel;
  private @Nullable String targetBytecodeVersion;

  private @NotNull List<String> compilerArguments;

  /**
   * @deprecated use {@link #JavaProjectData(ProjectSystemId, String, LanguageLevel, String, List)} instead
   */
  @Deprecated
  public JavaProjectData(
    @NotNull ProjectSystemId owner,
    @NotNull String compileOutputPath,
    @Nullable LanguageLevel languageLevel,
    @Nullable String targetBytecodeVersion
  ) {
    this(owner, compileOutputPath, languageLevel, targetBytecodeVersion, Collections.emptyList());
  }

  @PropertyMapping({"owner", "compileOutputPath", "languageLevel", "targetBytecodeVersion", "compilerArguments"})
  public JavaProjectData(
    @NotNull ProjectSystemId owner,
    @NotNull String compileOutputPath,
    @Nullable LanguageLevel languageLevel,
    @Nullable String targetBytecodeVersion,
    @NotNull List<String> compilerArguments
  ) {
    super(owner);

    this.compileOutputPath = compileOutputPath;
    this.languageLevel = languageLevel;
    this.targetBytecodeVersion = targetBytecodeVersion;
    this.compilerArguments = compilerArguments;
  }

  public @NotNull String getCompileOutputPath() {
    return compileOutputPath;
  }

  public void setCompileOutputPath(@NotNull String compileOutputPath) {
    this.compileOutputPath = ExternalSystemApiUtil.toCanonicalPath(compileOutputPath);
  }

  /**
   * @deprecated use {@link ProjectSdkData#getSdkName()} instead
   */
  @Deprecated(forRemoval = true) // used externally
  public @NotNull JavaSdkVersion getJdkVersion() {
    return ObjectUtils.notNull(jdkVersion, JavaSdkVersion.fromLanguageLevel(JavaRelease.getHighest()));
  }

  /**
   * @deprecated needed to support backward compatibility
   */
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  @SuppressWarnings("DeprecatedIsStillUsed")
  public void setJdkName(@Nullable String jdk) {
    jdkVersion = resolveSdkVersion(jdk);
  }

  /**
   * @deprecated needed to support backward compatibility
   */
  @Deprecated(forRemoval = true)
  private static @Nullable JavaSdkVersion resolveSdkVersion(@Nullable String jdk) {
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

  /**
   * @deprecated needed to support backward compatibility
   */
  @Deprecated(forRemoval = true)
  private static @Nullable JavaSdkVersion resolveSdkVersion(int version) {
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

  public @NotNull LanguageLevel getLanguageLevel() {
    return ObjectUtils.notNull(languageLevel, JavaRelease.getHighest());
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

  public @Nullable String getTargetBytecodeVersion() {
    return targetBytecodeVersion;
  }

  public void setTargetBytecodeVersion(@Nullable String targetBytecodeVersion) {
    this.targetBytecodeVersion = targetBytecodeVersion;
  }

  public @NotNull List<String> getCompilerArguments() {
    return compilerArguments;
  }

  public void setCompilerArguments(@NotNull List<String> compilerArguments) {
    this.compilerArguments = compilerArguments;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Objects.hashCode(jdkVersion);
    result = 31 * result + Objects.hashCode(languageLevel);
    result = 31 * result + Objects.hashCode(targetBytecodeVersion);
    result = 31 * result + compileOutputPath.hashCode();
    result = 31 * result + compilerArguments.hashCode();
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
    if (Objects.equals(compilerArguments, project.compilerArguments)) return false;

    return true;
  }

  @Override
  public String toString() {
    return "java project";
  }
}

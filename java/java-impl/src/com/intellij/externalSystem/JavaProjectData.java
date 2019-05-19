// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.externalSystem;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaProjectData extends AbstractExternalEntityData {
  public static final Key<JavaProjectData> KEY = Key.create(JavaProjectData.class, ProjectKeys.PROJECT.getProcessingWeight() + 1);

  private static final Logger LOG = Logger.getInstance(JavaProjectData.class);

  private static final LanguageLevel  DEFAULT_LANGUAGE_LEVEL = LanguageLevel.JDK_1_6;
  private static final JavaSdkVersion DEFAULT_JDK_VERSION    = JavaSdkVersion.JDK_1_6;
  private static final Pattern        JDK_VERSION_PATTERN    = Pattern.compile(".*1.(\\d+).*");

  @NotNull private JavaSdkVersion jdkVersion = DEFAULT_JDK_VERSION;
  @NotNull private LanguageLevel languageLevel = DEFAULT_LANGUAGE_LEVEL;

  @NotNull private String compileOutputPath;

  @PropertyMapping({"owner", "compileOutputPath"})
  public JavaProjectData(@NotNull ProjectSystemId owner, @NotNull String compileOutputPath) {
    super(owner);

    this.compileOutputPath = compileOutputPath;
  }

  @NotNull
  public String getCompileOutputPath() {
    return compileOutputPath;
  }

  public void setCompileOutputPath(@NotNull String compileOutputPath) {
    this.compileOutputPath = ExternalSystemApiUtil.toCanonicalPath(compileOutputPath);
  }

  @NotNull
  public JavaSdkVersion getJdkVersion() {
    return jdkVersion;
  }

  public void setJdkVersion(@NotNull JavaSdkVersion jdkVersion) {
    this.jdkVersion = jdkVersion;
  }

  public void setJdkVersion(@Nullable String jdk) {
    if (jdk == null) {
      return;
    }
    try {
      int version = Integer.parseInt(jdk.trim());
      if (applyJdkVersion(version)) {
        return;
      }
    }
    catch (NumberFormatException e) {
      // Ignore.
    }

    Matcher matcher = JDK_VERSION_PATTERN.matcher(jdk);
    if (!matcher.matches()) {
      return;
    }
    String versionAsString = matcher.group(1);
    try {
      applyJdkVersion(Integer.parseInt(versionAsString));
    }
    catch (NumberFormatException e) {
      // Ignore.
    }
  }

  public boolean applyJdkVersion(int version) {
    if (version < 0 || version >= JavaSdkVersion.values().length) {
      LOG.warn(String.format(
        "Unsupported jdk version detected (%d). Expected to get number from range [0; %d]", version, JavaSdkVersion.values().length
      ));
      return false;
    }
    for (JavaSdkVersion sdkVersion : JavaSdkVersion.values()) {
      if (sdkVersion.ordinal() == version) {
        jdkVersion = sdkVersion;
        return true;
      }
    }
    assert false : version + ", max value: " + JavaSdkVersion.values().length;
    return false;
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

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + jdkVersion.hashCode();
    result = 31 * result + languageLevel.hashCode();
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
    if (jdkVersion != project.jdkVersion) return false;
    if (languageLevel != project.languageLevel) return false;

    return true;
  }

  @Override
  public String toString() {
    return "java project";
  }
}

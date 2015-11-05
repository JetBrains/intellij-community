/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.externalSystem;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Denis Zhdanov
 * @since 4/12/13 12:27 PM
 */
public class JavaProjectData extends AbstractExternalEntityData {

  public static final Key<JavaProjectData> KEY = Key.create(JavaProjectData.class, ProjectKeys.PROJECT.getProcessingWeight() + 1);

  private static final Logger LOG = Logger.getInstance("#" + JavaProjectData.class.getName());

  private static final long serialVersionUID = 1L;

  private static final LanguageLevel  DEFAULT_LANGUAGE_LEVEL = LanguageLevel.JDK_1_6;
  private static final JavaSdkVersion DEFAULT_JDK_VERSION    = JavaSdkVersion.JDK_1_6;
  private static final Pattern        JDK_VERSION_PATTERN    = Pattern.compile(".*1.(\\d+).*");

  @NotNull private JavaSdkVersion myJdkVersion    = DEFAULT_JDK_VERSION;
  @NotNull private LanguageLevel  myLanguageLevel = DEFAULT_LANGUAGE_LEVEL;

  @NotNull private String myCompileOutputPath;

  public JavaProjectData(@NotNull ProjectSystemId owner, @NotNull String compileOutputPath) {
    super(owner);
    myCompileOutputPath = compileOutputPath;
  }

  @NotNull
  public String getCompileOutputPath() {
    return myCompileOutputPath;
  }

  public void setCompileOutputPath(@NotNull String compileOutputPath) {
    myCompileOutputPath = ExternalSystemApiUtil.toCanonicalPath(compileOutputPath);
  }

  @NotNull
  public JavaSdkVersion getJdkVersion() {
    return myJdkVersion;
  }

  public void setJdkVersion(@NotNull JavaSdkVersion jdkVersion) {
    myJdkVersion = jdkVersion;
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
        myJdkVersion = sdkVersion;
        return true;
      }
    }
    assert false : version + ", max value: " + JavaSdkVersion.values().length;
    return false;
  }

  @NotNull
  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  public void setLanguageLevel(@NotNull LanguageLevel level) {
    myLanguageLevel = level;
  }

  public void setLanguageLevel(@Nullable String languageLevel) {
    LanguageLevel level = LanguageLevel.parse(languageLevel);
    if (level != null) {
      myLanguageLevel = level;
    }
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myJdkVersion.hashCode();
    result = 31 * result + myLanguageLevel.hashCode();
    result = 31 * result + myCompileOutputPath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    JavaProjectData project = (JavaProjectData)o;

    if (!myCompileOutputPath.equals(project.myCompileOutputPath)) return false;
    if (myJdkVersion != project.myJdkVersion) return false;
    if (myLanguageLevel != project.myLanguageLevel) return false;

    return true;
  }

  @Override
  public String toString() {
    return "java project";
  }
}

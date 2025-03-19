
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.ex;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.rt.execution.CommandLineWrapper;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class JavaSdkUtil {
  private static final String IDEA_PREPEND_RT_JAR = "idea.prepend.rtjar";

  public static void addRtJar(@NotNull PathsList pathsList) {
    String ideaRtJarPath = getIdeaRtJarPath();
    if (Boolean.getBoolean(IDEA_PREPEND_RT_JAR)) {
      pathsList.addFirst(ideaRtJarPath);
    }
    else {
      pathsList.addTail(ideaRtJarPath);
    }
  }

  /// Returns the expected path to JUnit 4.13.2 location inside `.m2`.
  /// @deprecated JUnit 4 is not included in the IDEA distribution anymore
  @Deprecated
  public static @NotNull String getJunit4JarPath() {
    return Path.of(SystemProperties.getUserHome(), ".m2/repository/junit/junit/4.13.2/junit-4.13.2.jar").toAbsolutePath().toString();
  }

  /// Returns the path for JUnit 3 jar, or the expected path to JUnit 3.8.2 location inside `.m2`.
  /// @deprecated JUnit 3 is not included in the IDEA distribution anymore
  @Deprecated
  public static @NotNull String getJunit3JarPath() {
    try {
      return PathUtil.getJarPathForClass(ReflectionUtil.forName("junit.runner.TestSuiteLoader")); //junit3 specific class
    }
    catch (Exception e) {
      //IDEA started from sources won't have JUnit3 library in classpath, so let's take it from Maven repository
      return Path.of(SystemProperties.getUserHome(), ".m2/repository/junit/junit/3.8.2/junit-3.8.2.jar").toAbsolutePath().toString();
    }
  }

  public static @NotNull String getIdeaRtJarPath() {
    return PathUtil.getJarPathForClass(CommandLineWrapper.class);
  }

  public static @NotNull List<String> getJUnit4JarPaths() {
    return Arrays.asList(getJunit4JarPath(),
                         PathUtil.getJarPathForClass(ReflectionUtil.forName("org.hamcrest.Matcher")));
  }

  public static boolean isLanguageLevelAcceptable(@NotNull Project project, @NotNull Module module, @NotNull LanguageLevel level) {
    return isJdkSupportsLevel(getRelevantJdk(project, module), level);
  }

  private static boolean isJdkSupportsLevel(@Nullable Sdk jdk, @NotNull LanguageLevel level) {
    if (jdk == null) return true;
    JavaSdkVersion version = JavaSdkVersionUtil.getJavaSdkVersion(jdk);
    JavaSdkVersion required = JavaSdkVersion.fromLanguageLevel(level);
    return version != null && (level.isPreview() ? version.equals(required) : version.isAtLeast(required));
  }

  private static @Nullable Sdk getRelevantJdk(@NotNull Project project, @NotNull Module module) {
    Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
    Sdk moduleJdk = ModuleRootManager.getInstance(module).getSdk();
    return moduleJdk == null ? projectJdk : moduleJdk;
  }

  @Contract("null, _ -> true")
  public static boolean isJdkAtLeast(@Nullable Sdk jdk, @NotNull JavaSdkVersion expected) {
    return JavaSdkVersionUtil.isAtLeast(jdk, expected);
  }

  public static void applyJdkToProject(@NotNull Project project, @NotNull Sdk jdk) {
    ProjectRootManagerEx rootManager = ProjectRootManagerEx.getInstanceEx(project);
    rootManager.setProjectSdk(jdk);

    JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
    if (version != null) {
      LanguageLevel maxLevel = version.getMaxLanguageLevel();
      LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(ProjectManager.getInstance().getDefaultProject());
      LanguageLevelProjectExtension ext = LanguageLevelProjectExtension.getInstance(project);
      if (extension.isDefault() || maxLevel.compareTo(ext.getLanguageLevel()) < 0) {
        ext.setLanguageLevel(maxLevel);
        ext.setDefault(true);
      }
    }
  }
}
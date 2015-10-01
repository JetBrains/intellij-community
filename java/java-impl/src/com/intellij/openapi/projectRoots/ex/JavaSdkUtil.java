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
package com.intellij.openapi.projectRoots.ex;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkVersionUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.rt.compiler.JavacRunner;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class JavaSdkUtil {
  @NonNls public static final String IDEA_PREPEND_RTJAR = "idea.prepend.rtjar";

  public static void addRtJar(PathsList pathsList) {
    final String ideaRtJarPath = getIdeaRtJarPath();
    if (Boolean.getBoolean(IDEA_PREPEND_RTJAR)) {
      pathsList.addFirst(ideaRtJarPath);
    }
    else {
      pathsList.addTail(ideaRtJarPath);
    }
  }


  public static String getJunit4JarPath() {
    return PathUtil.getJarPathForClass(ReflectionUtil.forName("org.junit.Test"));
  }

  public static String getJunit3JarPath() {
    return PathUtil.getJarPathForClass(ReflectionUtil.forName("junit.runner.TestSuiteLoader")); //junit3 specific class
  }

  public static String getIdeaRtJarPath() {
    return PathUtil.getJarPathForClass(JavacRunner.class);
  }

  @NotNull
  public static List<String> getJUnit4JarPaths() {
    return Arrays.asList(getJunit4JarPath(),
                         PathUtil.getJarPathForClass(ReflectionUtil.forName("org.hamcrest.Matcher")));
  }

  public static boolean isLanguageLevelAcceptable(@NotNull Project project, @NotNull Module module, @NotNull LanguageLevel level) {
    return isJdkSupportsLevel(getRelevantJdk(project, module), level);
  }

  private static boolean isJdkSupportsLevel(@Nullable final Sdk jdk, @NotNull LanguageLevel level) {
    if (jdk == null) return true;
    String versionString = jdk.getVersionString();
    JavaSdkVersion version = versionString == null ? null : JdkVersionUtil.getVersion(versionString);
    return version != null && version.getMaxLanguageLevel().isAtLeast(level);
  }

  @Nullable
  private static Sdk getRelevantJdk(@NotNull Project project, @NotNull Module module) {
    Sdk projectJdk = ProjectRootManager.getInstance(project).getProjectSdk();
    Sdk moduleJdk = ModuleRootManager.getInstance(module).getSdk();
    return moduleJdk == null ? projectJdk : moduleJdk;
  }
}

/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class ExternalSystemJdkUtil {

  @NonNls public static final String USE_INTERNAL_JAVA = "#JAVA_INTERNAL";
  @NonNls public static final String USE_PROJECT_JDK = "#USE_PROJECT_JDK";
  @NonNls public static final String USE_JAVA_HOME = "#JAVA_HOME";

  @Nullable
  public static Sdk getJdk(@Nullable Project project, @Nullable String jdkName) throws ExternalSystemJdkException {
    if (jdkName == null) return null;

    if (USE_INTERNAL_JAVA.equals(jdkName)) {
      return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    }

    if (USE_PROJECT_JDK.equals(jdkName)) {
      if (project != null) {
        Sdk res = ProjectRootManager.getInstance(project).getProjectSdk();
        if (res != null) return res;

        Module[] modules = ModuleManager.getInstance(project).getModules();
        for (Module module : modules) {
          Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
          if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) return sdk;
        }
      }

      if (project == null) {
        Sdk recent = ProjectJdkTable.getInstance().findMostRecentSdkOfType(JavaSdk.getInstance());
        if (recent != null) return recent;

        return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
      }

      throw new ProjectJdkNotFoundException();
    }

    if (USE_JAVA_HOME.equals(jdkName)) {
      final String javaHome = System.getenv("JAVA_HOME");
      if (StringUtil.isEmptyOrSpaces(javaHome)) {
        throw new UndefinedJavaHomeException();
      }

      if (JdkUtil.checkForJdk(new File(javaHome)) || JdkUtil.checkForJre(javaHome)) {
        final Sdk jdk = JavaSdk.getInstance().createJdk("", javaHome);
        if (jdk == null) {
          throw new InvalidJavaHomeException(javaHome);
        }
        return jdk;
      }
      else {
        throw new InvalidJavaHomeException(javaHome);
      }
    }

    for (Sdk projectJdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (projectJdk.getName().equals(jdkName)) {
        if (projectJdk.getHomePath() != null &&
            (JdkUtil.checkForJdk(new File(projectJdk.getHomePath())) || JdkUtil.checkForJre(projectJdk.getHomePath()))) {
          return projectJdk;
        }
        else {
          throw new InvalidSdkException(projectJdk.getHomePath());
        }
      }
    }

    return null;
  }

  @Nullable
  public static Pair<String, Sdk> getAvailableJdk(@Nullable Project project) throws ExternalSystemJdkException {

    if (project != null) {
      Sdk res = ProjectRootManager.getInstance(project).getProjectSdk();
      if (res != null) return Pair.create(USE_PROJECT_JDK, res);

      Module[] modules = ModuleManager.getInstance(project).getModules();
      for (Module module : modules) {
        Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) return Pair.create(USE_PROJECT_JDK, sdk);
      }
    }

    final String javaHome = System.getenv("JAVA_HOME");
    if (!StringUtil.isEmptyOrSpaces(javaHome) && (JdkUtil.checkForJdk(new File(javaHome)) || JdkUtil.checkForJre(javaHome))) {
      final Sdk sdk = JavaSdk.getInstance().createJdk("", javaHome);
      if (sdk != null) {
        return Pair.create(USE_JAVA_HOME, sdk);
      }
    }

    for (Sdk projectJdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (projectJdk.getHomePath() != null &&
          (JdkUtil.checkForJdk(new File(projectJdk.getHomePath())) || JdkUtil.checkForJre(projectJdk.getHomePath()))) {
        return Pair.create(projectJdk.getName(), projectJdk);
      }
    }

    final Sdk internalJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    if (internalJdk != null) {
      return Pair.create(USE_INTERNAL_JAVA, internalJdk);
    }

    return null;
  }

  public static boolean checkForJdk(@NotNull Project project, @Nullable String jdkName) {
    try {
      final Sdk sdk = getJdk(project, jdkName);
      return sdk != null && sdk.getHomePath() != null && JdkUtil.checkForJdk(new File(sdk.getHomePath()));
    }
    catch (ExternalSystemJdkException ignore) {
    }
    return false;
  }
}

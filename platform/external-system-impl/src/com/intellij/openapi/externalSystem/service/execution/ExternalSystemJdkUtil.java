/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExternalSystemJdkUtil {
  public static final String USE_INTERNAL_JAVA = "#JAVA_INTERNAL";
  public static final String USE_PROJECT_JDK = "#USE_PROJECT_JDK";
  public static final String USE_JAVA_HOME = "#JAVA_HOME";

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

      if (project == null || project.isDefault()) {
        Sdk recent = ProjectJdkTable.getInstance().findMostRecentSdkOfType(JavaSdk.getInstance());
        return recent != null ? recent : JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
      }

      throw new ProjectJdkNotFoundException();
    }

    if (USE_JAVA_HOME.equals(jdkName)) {
      String javaHome = EnvironmentUtil.getEnvironmentMap().get("JAVA_HOME");
      if (StringUtil.isEmptyOrSpaces(javaHome)) throw new UndefinedJavaHomeException();
      if (!isValidJdk(javaHome)) throw new InvalidJavaHomeException(javaHome);
      return JavaSdk.getInstance().createJdk("", javaHome);
    }

    Sdk projectJdk = ProjectJdkTable.getInstance().findJdk(jdkName);
    if (projectJdk != null) {
      String homePath = projectJdk.getHomePath();
      if (!isValidJdk(homePath)) throw new InvalidSdkException(homePath);
      return projectJdk;
    }

    return null;
  }

  @NotNull
  public static Pair<String, Sdk> getAvailableJdk(@Nullable Project project) throws ExternalSystemJdkException {
    Condition<Sdk> sdkCondition = sdk -> sdk != null && sdk.getSdkType() == JavaSdk.getInstance() && isValidJdk(sdk.getHomePath());
    if (project != null) {
      Sdk res = ProjectRootManager.getInstance(project).getProjectSdk();
      if (sdkCondition.value(res)) return Pair.create(USE_PROJECT_JDK, res);

      Module[] modules = ModuleManager.getInstance(project).getModules();
      for (Module module : modules) {
        Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        if (sdkCondition.value(res)) {
          return Pair.create(USE_PROJECT_JDK, sdk);
        }
      }
    }

    Sdk mostRecentSdk = ProjectJdkTable.getInstance().findMostRecentSdk(sdkCondition);
    if (mostRecentSdk != null) {
      return Pair.create(mostRecentSdk.getName(), mostRecentSdk);
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      String javaHome = EnvironmentUtil.getEnvironmentMap().get("JAVA_HOME");
      if (isValidJdk(javaHome)) {
        return Pair.create(USE_JAVA_HOME, JavaSdk.getInstance().createJdk("", javaHome));
      }
    }

    return Pair.create(USE_INTERNAL_JAVA, JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk());
  }

  /** @deprecated trivial (to be removed in IDEA 2019) */
  public static boolean checkForJdk(@NotNull Project project, @Nullable String jdkName) {
    try {
      final Sdk sdk = getJdk(project, jdkName);
      return sdk != null && sdk.getHomePath() != null && JdkUtil.checkForJdk(sdk.getHomePath());
    }
    catch (ExternalSystemJdkException ignore) { }
    return false;
  }

  public static boolean isValidJdk(@Nullable String homePath) {
    return !StringUtil.isEmptyOrSpaces(homePath) && (JdkUtil.checkForJdk(homePath) || JdkUtil.checkForJre(homePath));
  }
}
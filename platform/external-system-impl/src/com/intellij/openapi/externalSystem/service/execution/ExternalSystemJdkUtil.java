// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

import static com.intellij.openapi.util.Pair.pair;

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

      SimpleJavaSdkType sdkType = SimpleJavaSdkType.getInstance();
      String sdkName = sdkType.suggestSdkName(null, javaHome);
      return sdkType.createJdk(sdkName, javaHome);
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
    JavaSdk javaSdkType = JavaSdk.getInstance();

    if (project != null) {
      Stream<Sdk> projectSdks = Stream.concat(
        Stream.of(ProjectRootManager.getInstance(project).getProjectSdk()),
        Stream.of(ModuleManager.getInstance(project).getModules()).map(module -> ModuleRootManager.getInstance(module).getSdk()));
      Sdk projectSdk = projectSdks
        .filter(sdk -> sdk != null && sdk.getSdkType() == javaSdkType && isValidJdk(sdk.getHomePath()))
        .findFirst().orElse(null);
      if (projectSdk != null) {
        return pair(USE_PROJECT_JDK, projectSdk);
      }
    }

    List<Sdk> allJdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdkType);
    Sdk mostRecentSdk = allJdks.stream().filter(sdk -> isValidJdk(sdk.getHomePath())).max(javaSdkType.versionComparator()).orElse(null);
    if (mostRecentSdk != null) {
      return pair(mostRecentSdk.getName(), mostRecentSdk);
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      String javaHome = EnvironmentUtil.getEnvironmentMap().get("JAVA_HOME");
      if (isValidJdk(javaHome)) {
        return pair(USE_JAVA_HOME, javaSdkType.createJdk("", javaHome));
      }
    }

    return pair(USE_INTERNAL_JAVA, JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk());
  }

  /** @deprecated trivial (to be removed in IDEA 2019) */
  @Deprecated
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
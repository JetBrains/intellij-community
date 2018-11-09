// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.EnvironmentUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
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
      return getInternalJdk();
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
        SdkType javaSdk = getJavaSdk();
        Sdk recent = javaSdk == null ? null : ProjectJdkTable.getInstance().findMostRecentSdkOfType(javaSdk);
        return recent != null ? recent : getInternalJdk();
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
    SdkType javaSdkType = getJavaSdkType();

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
        SimpleJavaSdkType simpleJavaSdkType = SimpleJavaSdkType.getInstance();
        String sdkName = simpleJavaSdkType.suggestSdkName(null, javaHome);
        return pair(USE_JAVA_HOME, simpleJavaSdkType.createJdk(sdkName, javaHome));
      }
    }

    return pair(USE_INTERNAL_JAVA, getInternalJdk());
  }

  @NotNull
  public static Collection<String> suggestJdkHomePaths() {
    return getJavaSdkType().suggestHomePaths();
  }

  @NotNull
  public static SdkType getJavaSdkType() {
    // JavaSdk.getInstance() can be null for non-java IDE
    SdkType javaSdk = getJavaSdk();
    return javaSdk == null ? SimpleJavaSdkType.getInstance() : javaSdk;
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
    return !StringUtil.isEmptyOrSpaces(homePath) && JdkUtil.checkForJdk(homePath) && JdkUtil.checkForJre(homePath);
  }

  @NotNull
  public static Sdk addJdk(String homePath) {
    Sdk jdk;
    if (isJavaSdkPresent()) {
      jdk = ExternalSystemJavaUtil.tryAddJdk(homePath);
      if (jdk != null) {
        return jdk;
      }
    }
    SimpleJavaSdkType simpleJavaSdkType = SimpleJavaSdkType.getInstance();
    jdk = simpleJavaSdkType.createJdk(simpleJavaSdkType.suggestSdkName(null, homePath), homePath);
    SdkConfigurationUtil.addSdk(jdk);
    return jdk;
  }

  @Nullable
  private static SdkType getJavaSdk() {
    if (isJavaSdkPresent()) {
      return ExternalSystemJavaUtil.getJavaSdk();
    }
    return null;
  }

  @NotNull
  private static Sdk getInternalJdk() {
    if (isJavaSdkPresent()) {
      Sdk internalJdk = ExternalSystemJavaUtil.getInternalJdk();
      if (internalJdk != null) return internalJdk;
    }
    final String jdkHome = SystemProperties.getJavaHome();
    SimpleJavaSdkType simpleJavaSdkType = SimpleJavaSdkType.getInstance();
    return simpleJavaSdkType.createJdk(simpleJavaSdkType.suggestSdkName(null, jdkHome), jdkHome);
  }

  // todo [Vlad, IDEA-187832]: extract to `external-system-java` module
  private static boolean isJavaSdkPresent() {
    try {
      Class.forName("com.intellij.openapi.projectRoots.impl.JavaSdkImpl");
      return true;
    }
    catch (Throwable ignore) {
      return false;
    }
  }
}
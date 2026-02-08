// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.execution;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.util.environment.Environment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.openapi.projectRoots.impl.DependentSdkType;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.SdkLookupBuilder;
import com.intellij.openapi.roots.ui.configuration.SdkLookupDecision;
import com.intellij.openapi.roots.ui.configuration.SdkLookupUtil;
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.intellij.openapi.util.Pair.pair;

public final class ExternalSystemJdkUtil {
  public static final String JAVA_HOME = "JAVA_HOME";

  public static final String USE_INTERNAL_JAVA = "#JAVA_INTERNAL";
  public static final String USE_PROJECT_JDK = "#USE_PROJECT_JDK";
  public static final String USE_JAVA_HOME = "#JAVA_HOME";

  @Contract("_, null -> null")
  public static @Nullable Sdk getJdk(@Nullable Project project, @Nullable String jdkName) throws ExternalSystemJdkException {
    return resolveJdkName(getProjectJdk(project), jdkName);
  }

  @Contract("_, null -> null")
  public static @Nullable Sdk resolveJdkName(@Nullable Sdk projectSdk, @Nullable String jdkName) throws ExternalSystemJdkException {
    return switch (jdkName) {
      case null -> null;
      case USE_INTERNAL_JAVA -> getInternalJdk();
      case USE_PROJECT_JDK -> {
        if (projectSdk == null) {
          throw new ProjectJdkNotFoundException();
        }
        yield resolveDependentJdk(projectSdk);
      }
      case USE_JAVA_HOME -> getJavaHomeJdk();
      default -> getJdk(jdkName);
    };
  }

  private static @NotNull Sdk getProjectJdk(@Nullable Project project) {
    if (project != null) {
      Sdk res = ProjectRootManager.getInstance(project).getProjectSdk();
      if (res != null) return res;

      Module[] modules = ModuleManager.getInstance(project).getModules();
      for (Module module : modules) {
        Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) return sdk;
      }
    }

    // Workaround for projects without project Jdk
    SdkType jdkType = getJavaSdkType();
    var jdkTable = project == null ? ProjectJdkTable.getInstance() : ProjectJdkTable.getInstance(project);
    return jdkTable
      .getSdksOfType(jdkType).stream()
      .filter(it -> isValidJdk(it))
      .max(jdkType.versionComparator())
      .orElseGet(ExternalSystemJdkUtil::getInternalJdk);
  }

  private static @NotNull Sdk getJavaHomeJdk() {
    String javaHome = getJavaHome();
    if (StringUtil.isEmptyOrSpaces(javaHome)) {
      throw new UndefinedJavaHomeException();
    }
    Sdk sdk = findJdkInSdkTableByPath(javaHome);
    if (sdk != null) {
      return sdk;
    }
    if (!isValidJdk(javaHome)) {
      throw new InvalidJavaHomeException(javaHome);
    }
    return ExternalSystemJdkProvider.getInstance().createJdk(null, javaHome);
  }

  public static @Nullable String getJavaHome() {
    return Environment.getVariable(JAVA_HOME);
  }

  private static @Nullable Sdk getJdk(@NotNull String jdkName) {
    Sdk jdk = ProjectJdkTable.getInstance().findJdk(jdkName);
    if (jdk == null) return null;
    String homePath = jdk.getHomePath();
    if (!isValidJdk(jdk)) throw new InvalidSdkException(homePath);
    return jdk;
  }

  public static @NotNull Pair<String, Sdk> getAvailableJdk(@Nullable Project project) throws ExternalSystemJdkException {
    SdkType javaSdkType = getJavaSdkType();

    if (project != null) {
      Sdk projectJdk = findProjectJDK(project, javaSdkType);
      if (projectJdk != null) {
        return pair(USE_PROJECT_JDK, projectJdk);
      }

      Sdk referencedJdk = findReferencedJdk(project);
      if (referencedJdk != null) {
        return pair(USE_PROJECT_JDK, referencedJdk);
      }
    }

    List<Sdk> allJdks = ProjectJdkTable.getInstance().getSdksOfType(javaSdkType);
    Sdk mostRecentSdk = allJdks.stream().filter(sdk -> isValidJdk(sdk)).max(javaSdkType.versionComparator()).orElse(null);
    if (mostRecentSdk != null) {
      return pair(mostRecentSdk.getName(), mostRecentSdk);
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      String javaHome = getJavaHome();
      if (isValidJdk(javaHome)) {
        SimpleJavaSdkType simpleJavaSdkType = SimpleJavaSdkType.getInstance();
        String sdkName = simpleJavaSdkType.suggestSdkName(null, javaHome);
        return pair(USE_JAVA_HOME, simpleJavaSdkType.createJdk(sdkName, javaHome));
      }
    }

    return pair(USE_INTERNAL_JAVA, getInternalJdk());
  }

  private static @Nullable Sdk findProjectJDK(@NotNull Project project, @NotNull SdkType javaSdkType) {
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    Stream<Sdk> projectSdks = Stream.concat(Stream.of(projectSdk),
                                            Stream.of(ModuleManager.getInstance(project).getModules()).map(module -> ModuleRootManager
                                              .getInstance(module).getSdk()));
    return projectSdks
      .filter(sdk -> sdk != null && sdk.getSdkType() == javaSdkType && isValidJdk(sdk) && JdkUtil.isCompatible(sdk, project))
      .findFirst()
      .orElse(null);
  }

  @Contract("_, null -> null")
  private static @Nullable Sdk findReferencedJdk(@Nullable Project project, @Nullable Sdk projectSdk) {
    if (projectSdk != null
        && projectSdk.getSdkType() instanceof DependentSdkType
        && projectSdk.getSdkType() instanceof JavaSdkType sdkType) {
      String sdkBinPath = sdkType.getBinPath(projectSdk);
      if (sdkBinPath == null) {
        return null;
      }
      final String jdkPath = FileUtil.toSystemIndependentName(new File(sdkBinPath).getParent());
      var jdkTable = project == null ? ProjectJdkTable.getInstance() : ProjectJdkTable.getInstance(project);
      return ContainerUtil.find(jdkTable.getAllJdks(), sdk -> {
        final String homePath = sdk.getHomePath();
        return homePath != null && FileUtil.toSystemIndependentName(homePath).equals(jdkPath);
      });
    } else {
      return null;
    }
  }

  private static @Nullable Sdk findReferencedJdk(@NotNull Project project) {
    Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
    return findReferencedJdk(project, projectSdk);
  }

  public static @NotNull Sdk resolveDependentJdk(@NotNull Sdk sdk) {
    Sdk parentSdk = findReferencedJdk(null, sdk);
    if (parentSdk == null) return sdk;
    return parentSdk;
  }

  /**
   * @deprecated use {@link #suggestJdkHomePaths()} instead.
   */
  @Deprecated
  public static @NotNull Collection<String> suggestJdkHomePaths() {
    return getJavaSdkType().suggestHomePaths();
  }

  public static @NotNull Collection<String> suggestJdkHomePaths(@NotNull Project project) {
    return getJavaSdkType().suggestHomePaths(project);
  }

  public static @NotNull SdkType getJavaSdkType() {
    return ExternalSystemJdkProvider.getInstance().getJavaSdkType();
  }

  /**
   * Resolves, version for Java that located in {@code javaHome} directory.
   * <p>
   * Note: This method cannot resolve a Java version for the Java on WSL. In this case, it returns {@code null}
   */
  public static @Nullable JavaVersion getJavaVersion(@NotNull String javaHome) {
    var javaSdkType = getJavaSdkType();
    var javaVersionString = javaSdkType.getVersionString(javaHome);
    return JavaVersion.tryParse(javaVersionString);
  }

  @Contract("null -> false")
  public static boolean isValidJdk(@Nullable Sdk jdk) {
    if (jdk == null) return false;
    if (!(jdk.getSdkType() instanceof JavaSdkType)) return false;
    if (SdkDownloadTracker.getInstance().isDownloading(jdk)) return true;
    return isValidJdk(jdk.getHomePath());
  }

  @Contract("null -> false")
  public static boolean isValidJdk(@Nullable String homePath) {
    if (StringUtil.isEmptyOrSpaces(homePath)) {
      return false;
    }
    try {
      Path path = Path.of(homePath);
      return JdkUtil.checkForJdk(path) && JdkUtil.checkForJre(path);
    }
    catch (InvalidPathException exception) {
      return false;
    }
  }

  public static @NotNull Sdk addJdk(@NotNull String homePath) {
    ExternalSystemJdkProvider jdkProvider = ExternalSystemJdkProvider.getInstance();
    List<Sdk> sdks = Arrays.asList(ProjectJdkTable.getInstance().getAllJdks());
    String name = SdkConfigurationUtil.createUniqueSdkName(jdkProvider.getJavaSdkType(), homePath, sdks);
    Sdk jdk = jdkProvider.createJdk(name, homePath);
    SdkConfigurationUtil.addSdk(jdk);
    return jdk;
  }

  private static @NotNull Sdk getInternalJdk() {
    return ExternalSystemJdkProvider.getInstance().getInternalJdk();
  }

  @Contract("null -> false")
  public static boolean isJdk9orLater(@Nullable String javaHome) {
    JdkVersionDetector.JdkVersionInfo jdkVersionInfo =
      javaHome == null ? null : JdkVersionDetector.getInstance().detectJdkVersionInfo(javaHome);
    return jdkVersionInfo != null && jdkVersionInfo.version.isAtLeast(9);
  }

  @ApiStatus.Internal
  public static @Nullable Sdk lookup(@NotNull Project project, @NotNull Function<SdkLookupBuilder, SdkLookupBuilder> customizer) {
    return SdkLookupUtil.lookupSdkBlocking(((builder) -> {
      return customizer.apply(
        builder.withProject(project)
          .withSdkType(getJavaSdkType())
          .onDownloadableSdkSuggested(__ -> SdkLookupDecision.STOP)
      );
    }));
  }

  @ApiStatus.Internal
  public static @NotNull Sdk lookupJdkByPath(@NotNull Project project, @NotNull String sdkHome) {
    var jdk = findJdkInSdkTableByPath(project, sdkHome);
    if (jdk != null) {
      return jdk;
    }
    return WriteAction.computeAndWait(() -> {
      var effectiveJdk = findJdkInSdkTableByPath(project, sdkHome);
      if (effectiveJdk != null) {
        return effectiveJdk;
      }
      return createJdk(project, sdkHome);
    });
  }

  @ApiStatus.Internal
  public static boolean matchJavaVersion(@NotNull JavaVersion versionRequirement, @Nullable String versionString) {
    var version = JavaVersion.tryParse(versionString);
    return version != null &&
           version.compareTo(versionRequirement) >= 0 &&
           version.feature == versionRequirement.feature;
  }

  @RequiresWriteLock
  private static @NotNull Sdk createJdk(@NotNull Project project, @NotNull String sdkHome) {
    var sdkTable = ProjectJdkTable.getInstance(project);
    var jdkProvider = ExternalSystemJdkProvider.getInstance();
    var jdkType = jdkProvider.getJavaSdkType();
    var jdkName = jdkType.suggestSdkName(null, sdkHome);
    var allJdks = sdkTable.getSdksOfType(getJavaSdkType());
    var uniqueJdkName = SdkConfigurationUtil.createUniqueSdkName(jdkName, allJdks);
    var jdk = jdkProvider.createJdk(uniqueJdkName, sdkHome);
    sdkTable.addJdk(jdk);
    return jdk;
  }

  @ApiStatus.Internal
  public static @Nullable Sdk findJdkInSdkTableByPath(@NotNull String jdkHome) {
    return findJdkInSdkTableByPath(null, jdkHome);
  }

  @ApiStatus.Internal
  public static @Nullable Sdk findJdkInSdkTableByPath(@Nullable Project project, @NotNull String jdkHome) {
    var sdkTable = project == null ? ProjectJdkTable.getInstance() : ProjectJdkTable.getInstance(project);
    var allJdks = sdkTable.getSdksOfType(getJavaSdkType());
    return ContainerUtil.find(allJdks, it -> FileUtil.pathsEqual(jdkHome, it.getHomePath()));
  }
}
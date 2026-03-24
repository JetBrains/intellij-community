// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetProgressIndicator;
import com.intellij.execution.target.TargetedCommandLineBuilder;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.JarUtil;
import com.intellij.platform.eel.provider.EelProviderUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.Attributes;

import static com.intellij.openapi.projectRoots.JavaNameKt.getJavaFileName;

public final class JdkUtil {
  public static final Key<Map<String, String>> COMMAND_LINE_CONTENT = Key.create("command.line.content");
  public static final Key<String> AGENT_RUNTIME_CLASSPATH = Key.create("command.line.agent.classpath");

  /// The VM property is needed to workaround incorrect escaped URLs handling in WebSphere,
  /// see [IDEA-126859](https://youtrack.jetbrains.com/issue/IDEA-126859#comment=27-778948) for additional details
  public static final String PROPERTY_DO_NOT_ESCAPE_CLASSPATH_URL = "idea.do.not.escape.classpath.url";

  private JdkUtil() { }

  public static @Nullable String suggestJdkName(@Nullable String versionString) {
    var version = JavaVersion.tryParse(versionString);
    return version == null ? null : suggestJdkName(version, null);
  }

  public static @NotNull String suggestJdkName(@NotNull JavaVersion version, @Nullable String vendorPrefix) {
    var suggested = new StringBuilder();
    if (vendorPrefix != null) suggested.append(vendorPrefix).append('-');
    if (version.feature < 9) suggested.append("1.");
    suggested.append(version.feature);
    if (version.ea) suggested.append("-ea");
    return suggested.toString();
  }

  /// Check the JDK bundle.
  ///
  /// @param homePath path to a directory with JDK.
  /// @return if the JDK can be run on this machine and contains all the necessary components for an execution.
  @RequiresBackgroundThread(generateAssertion = false)
  public static boolean checkForJdk(@NotNull Path homePath) {
    return checkForJdkOrJre(homePath, CheckFor.JDK) && (
      isModularRuntime(homePath) ||                             // Jigsaw JDK/JRE
      Files.exists(homePath.resolve("jre/lib/rt.jar")) ||       // pre-modular JDK
      Files.isDirectory(homePath.resolve("classes")) ||         // custom build
      Files.exists(homePath.resolve("jre/lib/vm.jar")) ||       // IBM JDK
      Files.exists(homePath.resolve("../Classes/classes.jar"))  // Apple JDK
    );
  }

  /// Check compatibility between a Project and a JDK.
  ///
  /// @return if the JDK can be run on this machine.
  public static boolean isCompatible(@NotNull Sdk sdk, @NotNull Project project) {
    var sdkHome = sdk.getHomePath();
    if (sdkHome == null) return false;
    var sdkHomePath = Path.of(sdkHome);
    return isCompatible(sdkHomePath, project);
  }

  /// Check compatibility between a Project and a JDK located on the path.
  ///
  /// @param jdkHomePath path to a directory with JDK.
  /// @return if the JDK can be run on this machine.
  public static boolean isCompatible(@NotNull Path jdkHomePath, @NotNull Project project) {
    return EelProviderUtil.getEelMachine(project).ownsPath(jdkHomePath);
  }

  /// Returns `true` if the given directory hosts a JRE.
  @RequiresBackgroundThread(generateAssertion = false)
  public static boolean checkForJre(@NotNull Path homePath) {
    return checkForJdkOrJre(homePath, CheckFor.JRE);
  }

  public static boolean isModularRuntime(@NotNull String homePath) {
    return isModularRuntime(Path.of(homePath));
  }

  public static boolean isModularRuntime(@NotNull Path homePath) {
    return Files.isRegularFile(homePath.resolve("lib/jrt-fs.jar")) || isExplodedModularRuntime(homePath);
  }

  public static boolean isExplodedModularRuntime(@NotNull Path homePath) {
    return Files.isDirectory(homePath.resolve("modules/java.base"));
  }

  @ApiStatus.Internal
  public static @NotNull TargetedCommandLineBuilder setupJVMCommandLine(
    @NotNull SimpleJavaParameters javaParameters,
    @NotNull TargetEnvironmentRequest request
  ) throws CantRunException {
    var setup = new JdkCommandLineSetup(request);
    setup.setupJavaExePath(javaParameters);
    setup.setupCommandLine(javaParameters);
    return setup.getCommandLine();
  }

  public static @NotNull GeneralCommandLine setupJVMCommandLine(@NotNull SimpleJavaParameters javaParameters) throws CantRunException {
    var request = new LocalTargetEnvironmentRequest();
    var builder = setupJVMCommandLine(javaParameters, request);
    LocalTargetEnvironment environment;
    try {
      environment = request.prepareEnvironment(TargetProgressIndicator.EMPTY);
    }
    catch (ExecutionException e) {
      throw new CantRunException(e.getMessage(), e);
    }
    return environment.createGeneralCommandLine(builder.build());
  }

  public static boolean useDynamicClasspath(@Nullable Project project) {
    var hasDynamicProperty = Boolean.parseBoolean(System.getProperty("idea.dynamic.classpath", "false"));
    return project != null
           ? PropertiesComponent.getInstance(project).getBoolean(ExecutionUtil.PROPERTY_DYNAMIC_CLASSPATH, hasDynamicProperty)
           : hasDynamicProperty;
  }

  public static boolean useDynamicVMOptions() {
    return PropertiesComponent.getInstance().getBoolean("idea.dynamic.vmoptions", true);
  }

  public static boolean useDynamicParameters() {
    return PropertiesComponent.getInstance().getBoolean("idea.dynamic.parameters", true);
  }

  public static boolean useClasspathJar() {
    return PropertiesComponent.getInstance().getBoolean("idea.dynamic.classpath.jar", true);
  }

  @RequiresBackgroundThread(generateAssertion = false)
  private static boolean checkForJdkOrJre(@NotNull Path homePath, @NotNull CheckFor checkFor) {
    return Files.exists(homePath.resolve("bin").resolve(getJavaFileName(homePath, checkFor)));
  }

  //<editor-fold desc="Deprecated stuff.">
  /// @deprecated outdated, please use [JdkVersionDetector] instead
  @Deprecated(forRemoval = true)
  public static @Nullable String getJdkMainAttribute(@NotNull Sdk jdk, @NotNull Attributes.Name attribute) {
    var homePath = jdk.getHomePath();
    if (homePath != null) {
      var signatureJar = FileUtil.findFirstThatExist(
        homePath + "/jre/lib/rt.jar",
        homePath + "/lib/rt.jar",
        homePath + "/lib/jrt-fs.jar",
        homePath + "/jre/lib/vm.jar",
        homePath + "/../Classes/classes.jar");
      if (signatureJar != null) {
        return JarUtil.getJarAttribute(signatureJar, attribute);
      }
    }

    return null;
  }

  /// @deprecated use [#checkForJdk(Path)]
  @Deprecated(forRemoval = true)
  @RequiresBackgroundThread(generateAssertion = false)
  public static boolean checkForJdk(@NotNull String homePath) {
    try {
      return checkForJdk(Path.of(homePath));
    }
    catch (InvalidPathException e) {
      return false;
    }
  }

  /// @deprecated use [#checkForJre(Path)]
  @RequiresBackgroundThread(generateAssertion = false)
  @Deprecated(forRemoval = true)
  public static boolean checkForJre(@NotNull String homePath) {
    return checkForJre(Path.of(homePath));
  }
  //</editor-fold>
}

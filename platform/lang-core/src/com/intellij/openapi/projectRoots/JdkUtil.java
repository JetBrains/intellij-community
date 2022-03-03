// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
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
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.Attributes;

public final class JdkUtil {
  public static final Key<Map<String, String>> COMMAND_LINE_CONTENT = Key.create("command.line.content");

  public static final Key<String> AGENT_RUNTIME_CLASSPATH = Key.create("command.line.agent.classpath");

  /**
   * The VM property is needed to workaround incorrect escaped URLs handling in WebSphere,
   * see <a href="https://youtrack.jetbrains.com/issue/IDEA-126859#comment=27-778948">IDEA-126859</a> for additional details
   */
  public static final String PROPERTY_DO_NOT_ESCAPE_CLASSPATH_URL = "idea.do.not.escape.classpath.url";
  /**
   * @deprecated Please use ExecutionUtil.PROPERTY_DYNAMIC_CLASSPATH
   */
  @Deprecated
  public static final String PROPERTY_DYNAMIC_CLASSPATH = ExecutionUtil.PROPERTY_DYNAMIC_CLASSPATH;

  private JdkUtil() { }

  /** @deprecated outdated, please use {@link org.jetbrains.jps.model.java.JdkVersionDetector} instead */
  @Deprecated
  public static @Nullable String getJdkMainAttribute(@NotNull Sdk jdk, @NotNull Attributes.Name attribute) {
    String homePath = jdk.getHomePath();
    if (homePath != null) {
      File signatureJar = FileUtil.findFirstThatExist(
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

  public static @Nullable String suggestJdkName(@Nullable String versionString) {
    JavaVersion version = JavaVersion.tryParse(versionString);
    return version == null ? null : suggestJdkName(version, null);
  }

  public static @NotNull String suggestJdkName(@NotNull JavaVersion version, @Nullable String vendorPrefix) {
    StringBuilder suggested = new StringBuilder();
    if (vendorPrefix != null) suggested.append(vendorPrefix).append('-');
    if (version.feature < 9) suggested.append("1.");
    suggested.append(version.feature);
    if (version.ea) suggested.append("-ea");
    return suggested.toString();
  }

  public static boolean checkForJdk(@NotNull String homePath) {
    return checkForJdk(Path.of(homePath));
  }

  public static boolean checkForJdk(@NotNull Path homePath) {
    return (Files.exists(homePath.resolve("bin/javac")) || Files.exists(homePath.resolve("bin/javac.exe"))) &&
           (isModularRuntime(homePath) ||                               // Jigsaw JDK/JRE
            Files.exists(homePath.resolve("jre/lib/rt.jar")) ||         // pre-modular JDK
            Files.isDirectory(homePath.resolve("classes")) ||           // custom build
            Files.exists(homePath.resolve("jre/lib/vm.jar")) ||         // IBM JDK
            Files.exists(homePath.resolve("../Classes/classes.jar")));  // Apple JDK
  }

  public static boolean checkForJre(@NotNull String homePath) {
    return checkForJre(Path.of(homePath));
  }

  public static boolean checkForJre(@NotNull Path homePath) {
    return Files.exists(homePath.resolve("bin/java")) || Files.exists(homePath.resolve("bin/java.exe"));
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
  public static @NotNull TargetedCommandLineBuilder setupJVMCommandLine(@NotNull SimpleJavaParameters javaParameters,
                                                                        @NotNull TargetEnvironmentRequest request)
    throws CantRunException {

    JdkCommandLineSetup setup = new JdkCommandLineSetup(request);
    setup.setupJavaExePath(javaParameters);
    setup.setupCommandLine(javaParameters);
    return setup.getCommandLine();
  }

  public static @NotNull GeneralCommandLine setupJVMCommandLine(@NotNull SimpleJavaParameters javaParameters) throws CantRunException {
    LocalTargetEnvironmentRequest request = new LocalTargetEnvironmentRequest();
    TargetedCommandLineBuilder builder = setupJVMCommandLine(javaParameters, request );
    LocalTargetEnvironment environment = request.prepareEnvironment(TargetProgressIndicator.EMPTY);
    return environment.createGeneralCommandLine(builder.build());
  }

  public static boolean useDynamicClasspath(@Nullable Project project) {
    boolean hasDynamicProperty = Boolean.parseBoolean(System.getProperty("idea.dynamic.classpath", "false"));
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

  //<editor-fold desc="Deprecated stuff.">

  private static void setupCommandLine(GeneralCommandLine commandLine, SimpleJavaParameters javaParameters) throws CantRunException {
    LocalTargetEnvironmentRequest request = new LocalTargetEnvironmentRequest();
    JdkCommandLineSetup setup = new JdkCommandLineSetup(request);
    setup.setupCommandLine(javaParameters);

    LocalTargetEnvironment environment = request.prepareEnvironment(TargetProgressIndicator.EMPTY);
    GeneralCommandLine generalCommandLine = environment.createGeneralCommandLine(setup.getCommandLine().build());
    commandLine.withParentEnvironmentType(javaParameters.isPassParentEnvs() ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE);
    commandLine.getParametersList().addAll(generalCommandLine.getParametersList().getList());
    commandLine.getEnvironment().putAll(generalCommandLine.getEnvironment());
  }
  //</editor-fold>
}

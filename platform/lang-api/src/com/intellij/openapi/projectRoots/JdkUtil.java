// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetedCommandLineBuilder;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentFactory;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.JarUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Attributes;

public final class JdkUtil {
  public static final Key<Map<String, String>> COMMAND_LINE_CONTENT = Key.create("command.line.content");

  public static final Key<JdkCommandLineSetup> COMMAND_LINE_SETUP_KEY = Key.create("com.intellij.openapi.projectRoots.JdkCommandLineSetup");

  /**
   * The VM property is needed to workaround incorrect escaped URLs handling in WebSphere,
   * see <a href="https://youtrack.jetbrains.com/issue/IDEA-126859#comment=27-778948">IDEA-126859</a> for additional details
   */
  public static final String PROPERTY_DO_NOT_ESCAPE_CLASSPATH_URL = "idea.do.not.escape.classpath.url";

  private JdkUtil() { }

  /**
   * Returns the specified attribute of the JDK (examines 'rt.jar'), or {@code null} if cannot determine the value.
   */
  public static @Nullable String getJdkMainAttribute(@NotNull Sdk jdk, @NotNull Attributes.Name attribute) {
    if (attribute == Attributes.Name.IMPLEMENTATION_VERSION) {
      // optimization: JDK version string is cached
      String versionString = jdk.getVersionString();
      if (versionString != null) {
        int start = versionString.indexOf('"'), end = versionString.lastIndexOf('"');
        if (start >= 0 && end > start) {
          return versionString.substring(start + 1, end);
        }
      }
    }

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
    if (version == null) return null;

    StringBuilder suggested = new StringBuilder();
    if (version.feature < 9) suggested.append("1.");
    suggested.append(version.feature);
    if (version.ea) suggested.append("-ea");
    return suggested.toString();
  }

  public static boolean checkForJdk(@NotNull String homePath) {
    return checkForJdk(new File(FileUtil.toSystemDependentName(homePath)));
  }

  public static boolean checkForJdk(@NotNull File homePath) {
    return (new File(homePath, "bin/javac").isFile() || new File(homePath, "bin/javac.exe").isFile()) &&
           checkForRuntime(homePath.getAbsolutePath());
  }

  public static boolean checkForJre(@NotNull String homePath) {
    return checkForJre(new File(FileUtil.toSystemDependentName(homePath)));
  }

  public static boolean checkForJre(@NotNull File homePath) {
    return new File(homePath, "bin/java").isFile() || new File(homePath, "bin/java.exe").isFile();
  }

  public static boolean checkForRuntime(@NotNull String homePath) {
    return new File(homePath, "jre/lib/rt.jar").exists() ||          // JDK
           new File(homePath, "lib/rt.jar").exists() ||              // JRE
           isModularRuntime(homePath) ||                             // Jigsaw JDK/JRE
           new File(homePath, "../Classes/classes.jar").exists() ||  // Apple JDK
           new File(homePath, "jre/lib/vm.jar").exists() ||          // IBM JDK
           new File(homePath, "classes").isDirectory();              // custom build
  }

  public static boolean isModularRuntime(@NotNull String homePath) {
    return isModularRuntime(new File(FileUtil.toSystemDependentName(homePath)));
  }

  public static boolean isModularRuntime(@NotNull File homePath) {
    return new File(homePath, "lib/jrt-fs.jar").isFile() || isExplodedModularRuntime(homePath.getPath());
  }

  public static boolean isExplodedModularRuntime(@NotNull String homePath) {
    return new File(homePath, "modules/java.base").isDirectory();
  }

  @ApiStatus.Internal
  public static @NotNull TargetedCommandLineBuilder setupJVMCommandLine(@NotNull SimpleJavaParameters javaParameters,
                                                                        @NotNull TargetEnvironmentRequest request,
                                                                        @Nullable TargetEnvironmentConfiguration targetConfiguration)
    throws CantRunException {

    JdkCommandLineSetup setup = new JdkCommandLineSetup(request, targetConfiguration);
    setup.setupJavaExePath(javaParameters);
    setup.setupCommandLine(javaParameters);
    return setup.getCommandLine();
  }

  public static @NotNull GeneralCommandLine setupJVMCommandLine(@NotNull SimpleJavaParameters javaParameters) throws CantRunException {
    LocalTargetEnvironmentFactory environmentFactory = new LocalTargetEnvironmentFactory();
    TargetEnvironmentRequest request = environmentFactory.createRequest();
    TargetedCommandLineBuilder builder = setupJVMCommandLine(javaParameters, request, null);
    LocalTargetEnvironment environment = environmentFactory.prepareRemoteEnvironment(request, new EmptyProgressIndicator());
    Objects.requireNonNull(builder.getUserData(COMMAND_LINE_SETUP_KEY)).provideEnvironment(environment, new EmptyProgressIndicator());
    return environment.createGeneralCommandLine(builder.build());
  }

  public static boolean useDynamicClasspath(@Nullable Project project) {
    boolean hasDynamicProperty = Boolean.parseBoolean(System.getProperty("idea.dynamic.classpath", "false"));
    return project != null
           ? PropertiesComponent.getInstance(project).getBoolean("dynamic.classpath", hasDynamicProperty)
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

  /**
   * @deprecated use {@link SimpleJavaParameters#toCommandLine()}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  @Deprecated
  public static GeneralCommandLine setupJVMCommandLine(String exePath, SimpleJavaParameters javaParameters, boolean forceDynamicClasspath) {
    try {
      javaParameters.setUseDynamicClasspath(forceDynamicClasspath);
      GeneralCommandLine commandLine = new GeneralCommandLine(exePath);
      setupCommandLine(commandLine, javaParameters);
      return commandLine;
    }
    catch (CantRunException e) {
      throw new RuntimeException(e);
    }
  }

  private static void setupCommandLine(GeneralCommandLine commandLine, SimpleJavaParameters javaParameters) throws CantRunException {
    LocalTargetEnvironmentFactory environmentFactory = new LocalTargetEnvironmentFactory();
    TargetEnvironmentRequest request = environmentFactory.createRequest();
    JdkCommandLineSetup setup = new JdkCommandLineSetup(request, null);
    setup.setupCommandLine(javaParameters);

    LocalTargetEnvironment environment = environmentFactory.prepareRemoteEnvironment(request, new EmptyProgressIndicator());
    GeneralCommandLine generalCommandLine = environment.createGeneralCommandLine(setup.getCommandLine().build());
    commandLine.withParentEnvironmentType(javaParameters.isPassParentEnvs() ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE);
    commandLine.getParametersList().addAll(generalCommandLine.getParametersList().getList());
    commandLine.getEnvironment().putAll(generalCommandLine.getEnvironment());
  }
  //</editor-fold>
}
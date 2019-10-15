// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.execution.CantRunException;
import com.intellij.execution.CommandLineWrapperUtil;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.remote.IR;
import com.intellij.execution.remote.RemoteTargetConfiguration;
import com.intellij.execution.remote.java.JavaLanguageRuntimeConfiguration;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.JarUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.lang.UrlClassLoader;
import gnu.trove.THashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * @author max
 */
public class JdkUtil {
  public static final Key<Map<String, String>> COMMAND_LINE_CONTENT = Key.create("command.line.content");

  /**
   * The VM property is needed to workaround incorrect escaped URLs handling in WebSphere,
   * see <a href="https://youtrack.jetbrains.com/issue/IDEA-126859#comment=27-778948">IDEA-126859</a> for additional details
   */
  public static final String PROPERTY_DO_NOT_ESCAPE_CLASSPATH_URL = "idea.do.not.escape.classpath.url";

  private static final String WRAPPER_CLASS = "com.intellij.rt.execution.CommandLineWrapper";
  private static final String JAVAAGENT = "-javaagent";

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
  @NotNull
  public static IR.NewCommandLine setupJVMCommandLine(@NotNull SimpleJavaParameters javaParameters,
                                                      @NotNull IR.RemoteEnvironmentRequest request,
                                                      @Nullable RemoteTargetConfiguration targetConfiguration)
    throws CantRunException {
    IR.NewCommandLine commandLine = new IR.NewCommandLine();
    JavaLanguageRuntimeConfiguration javaConfiguration = targetConfiguration != null
                                                         ? targetConfiguration.getRuntimes().findByType(JavaLanguageRuntimeConfiguration.class)
                                                         : null;
    if (request instanceof IR.LocalRunner.LocalEnvironmentRequest || targetConfiguration == null) {
      Sdk jdk = javaParameters.getJdk();
      if (jdk == null) throw new CantRunException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
      SdkTypeId type = jdk.getSdkType();
      if (!(type instanceof JavaSdkType)) throw new CantRunException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
      String exePath = ((JavaSdkType)type).getVMExecutablePath(jdk);
      if (exePath == null) throw new CantRunException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"));
      commandLine.setExePath(exePath);
    }
    else {
      if (javaConfiguration == null) {
        throw new CantRunException("Cannot find Java configuration in " + targetConfiguration.getDisplayName() + " target");
      }
      Platform platform = request.getRemotePlatform().getPlatform();
      String java = platform == Platform.WINDOWS ? "java.exe" : "java";
      commandLine.setExePath(StringUtil.join(new String[]{javaConfiguration.getHomePath(), "bin", java},
                                             String.valueOf(platform.fileSeparator)));
    }
    setupCommandLine(commandLine, request, javaParameters, javaConfiguration);
    return commandLine;
  }

  @NotNull
  public static GeneralCommandLine setupJVMCommandLine(@NotNull SimpleJavaParameters javaParameters) throws CantRunException {
    IR.LocalRunner runner = new IR.LocalRunner();
    IR.RemoteEnvironmentRequest request = runner.createRequest();
    return runner.prepareRemoteEnvironment(request, new EmptyProgressIndicator())
      .createGeneralCommandLine(setupJVMCommandLine(javaParameters, request, null));
  }

  private static void setupCommandLine(@NotNull IR.NewCommandLine commandLine,
                                       @NotNull IR.RemoteEnvironmentRequest request,
                                       @NotNull SimpleJavaParameters javaParameters,
                                       @Nullable JavaLanguageRuntimeConfiguration runtimeConfiguration) throws CantRunException {
    commandLine.setWorkingDirectory(request.createUpload(javaParameters.getWorkingDirectory()));
    javaParameters.getEnv().forEach((key, value) -> commandLine.addEnvironmentVariable(key, value));

    if (request instanceof IR.LocalRunner.LocalEnvironmentRequest) {
      ParentEnvironmentType type = javaParameters.isPassParentEnvs() ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE;
      ((IR.LocalRunner.LocalEnvironmentRequest)request).setParentEnvironmentType(type);
    }

    ParametersList vmParameters = javaParameters.getVMParametersList();
    boolean dynamicClasspath = javaParameters.isDynamicClasspath();
    boolean dynamicVMOptions = dynamicClasspath && javaParameters.isDynamicVMOptions() && useDynamicVMOptions();
    boolean dynamicParameters = dynamicClasspath && javaParameters.isDynamicParameters() && useDynamicParameters();
    boolean dynamicMainClass = false;

    // copies 'javaagent' .jar files to the beginning of the classpath to load agent classes faster
    if (isUrlClassloader(vmParameters)) {
      if (!(request instanceof IR.LocalRunner.LocalEnvironmentRequest)) {
        throw new CantRunException("Cannot run application with UrlClassPath on the remote target.");
      }
      for (String parameter : vmParameters.getParameters()) {
        if (parameter.startsWith(JAVAAGENT)) {
          int agentArgsIdx = parameter.indexOf("=", JAVAAGENT.length());
          javaParameters.getClassPath()
            .addFirst(parameter.substring(JAVAAGENT.length() + 1, agentArgsIdx > -1 ? agentArgsIdx : parameter.length()));
        }
      }
    }

    if (dynamicClasspath) {
      Charset cs = StandardCharsets.UTF_8;  // todo detect JNU charset from VM options?
      Class<?> commandLineWrapper;
      if (javaParameters.isArgFile()) {
        setArgFileParams(commandLine, request, runtimeConfiguration, javaParameters, vmParameters, dynamicVMOptions, dynamicParameters, cs);
        dynamicMainClass = dynamicParameters;
      }
      else if (!explicitClassPath(vmParameters) &&
               javaParameters.getJarPath() == null &&
               (commandLineWrapper = getCommandLineWrapperClass()) != null) {
        if (javaParameters.isUseClasspathJar()) {
          setClasspathJarParams(commandLine, request, runtimeConfiguration, javaParameters, vmParameters, commandLineWrapper,
                                dynamicVMOptions, dynamicParameters);
        }
        else if (javaParameters.isClasspathFile()) {
          setCommandLineWrapperParams(commandLine, request, runtimeConfiguration, javaParameters,
                                      vmParameters, commandLineWrapper, dynamicVMOptions, dynamicParameters, cs);
        }
      }
      else {
        dynamicClasspath = dynamicParameters = false;
      }
    }

    if (!dynamicClasspath) {
      appendParamsEncodingClasspath(commandLine, request, runtimeConfiguration, javaParameters, vmParameters);
    }

    if (!dynamicMainClass) {
      for (IR.RemoteValue<String> parameter : getMainClassParams(javaParameters, request)) {
        commandLine.addParameter(parameter);
      }
    }

    if (!dynamicParameters) {
      for (String parameter : javaParameters.getProgramParametersList().getList()) {
        commandLine.addParameter(parameter);
      }
    }
  }

  private static void setupCommandLine(@NotNull GeneralCommandLine commandLine, @NotNull SimpleJavaParameters javaParameters)
    throws CantRunException {
    IR.NewCommandLine newCommandLine = new IR.NewCommandLine();
    IR.LocalRunner runner = new IR.LocalRunner();
    IR.RemoteEnvironmentRequest request = runner.createRequest();
    setupCommandLine(newCommandLine, request, javaParameters, null);
    IR.LocalRemoteEnvironment environment = runner.prepareRemoteEnvironment(request, new EmptyProgressIndicator());
    GeneralCommandLine generalCommandLine = environment.createGeneralCommandLine(newCommandLine);
    commandLine.withParentEnvironmentType(javaParameters.isPassParentEnvs() ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE);
    commandLine.getParametersList().addAll(generalCommandLine.getParametersList().getList());
    commandLine.getEnvironment().putAll(generalCommandLine.getEnvironment());
  }

  private static boolean isUrlClassloader(ParametersList vmParameters) {
    return UrlClassLoader.class.getName().equals(vmParameters.getPropertyValue("java.system.class.loader"));
  }

  private static boolean explicitClassPath(ParametersList vmParameters) {
    return vmParameters.hasParameter("-cp") || vmParameters.hasParameter("-classpath") || vmParameters.hasParameter("--class-path");
  }

  private static boolean explicitModulePath(ParametersList vmParameters) {
    return vmParameters.hasParameter("-p") || vmParameters.hasParameter("--module-path");
  }

  private static void setArgFileParams(@NotNull IR.NewCommandLine commandLine,
                                       @NotNull IR.RemoteEnvironmentRequest request,
                                       @Nullable JavaLanguageRuntimeConfiguration runtimeConfiguration,
                                       @NotNull SimpleJavaParameters javaParameters,
                                       @NotNull ParametersList vmParameters,
                                       boolean dynamicVMOptions,
                                       boolean dynamicParameters,
                                       Charset cs) throws CantRunException {
    try {
      String pathSeparator = String.valueOf(request.getRemotePlatform().getPlatform().pathSeparator);
      Collection<Promise<IR.RemoteValue<String>>> promises = new ArrayList<>();
      IR.RemoteValue<String> classPathParameter;
      PathsList classPath = javaParameters.getClassPath();
      if (!classPath.isEmpty() && !explicitClassPath(vmParameters)) {
        List<IR.RemoteValue<String>> pathValues = getClassPathValues(request, runtimeConfiguration, javaParameters);
        classPathParameter = new IR.CompositeValue<>(pathValues, values -> StringUtil.join(values, pathSeparator));
        promises.add(classPathParameter.promise());
      }
      else {
        classPathParameter = null;
      }

      IR.RemoteValue<String> modulePathParameter;
      PathsList modulePath = javaParameters.getModulePath();
      if (!modulePath.isEmpty() && !explicitModulePath(vmParameters)) {
        List<IR.RemoteValue<String>> pathValues = getClassPathValues(request, runtimeConfiguration, javaParameters);
        modulePathParameter = new IR.CompositeValue<>(pathValues, values -> StringUtil.join(values, pathSeparator));
        promises.add(modulePathParameter.promise());
      }
      else {
        modulePathParameter = null;
      }

      List<IR.RemoteValue<String>> mainClassParameters = dynamicParameters ? getMainClassParams(javaParameters, request)
                                                                           : Collections.emptyList();

      promises.addAll(ContainerUtil.map(mainClassParameters, IR.RemoteValue::promise));

      File argFile = FileUtil.createTempFile("idea_arg_file" + new Random().nextInt(Integer.MAX_VALUE), null);
      Promises.collectResults(promises).onSuccess(__ -> {
        List<String> fileArgs = new ArrayList<>();
          if (dynamicVMOptions) {
            fileArgs.addAll(vmParameters.getList());
              
          }
          else {
            appendVmParameters(commandLine, request, vmParameters);
          }
          if (classPathParameter != null) {
            fileArgs.add("-classpath");
            fileArgs.add(classPathParameter.getRemoteValue());
            
          }
          if (modulePathParameter != null) {
            fileArgs.add("-p");
            fileArgs.add(modulePathParameter.getRemoteValue());
            
          }

          for (IR.RemoteValue<String> mainClassParameter : mainClassParameters) {
            fileArgs.add(mainClassParameter.getRemoteValue());
          }
          if (dynamicParameters) {
            fileArgs.addAll(javaParameters.getProgramParametersList().getList());
          }
          CommandLineWrapperUtil.writeArgumentsFile(argFile, fileArgs, cs);
        }
      });

      appendEncoding(javaParameters, commandLine, vmParameters);
      IR.RemoteValue<String> argFileParameter = request.createUpload(argFile.getAbsolutePath());
      commandLine.addParameter(new IR.MapValue<>(argFileParameter, s -> "@" + s));
      //todo[remoteServers]: support COMMAND_LINE_CONTENT
      //commandLine.putUserData(COMMAND_LINE_CONTENT, ContainerUtil.stringMap(argFile.getAbsolutePath(), FileUtil.loadFile(argFile)));

      //todo[remoteServers]: support deleting files on termination
      //OSProcessHandler.deleteFileOnTermination(commandLine, argFile);
    }
    catch (IOException e) {
      throwUnableToCreateTempFile(e);
    }
  }

  private static void setCommandLineWrapperParams(@NotNull IR.NewCommandLine commandLine,
                                                  @NotNull IR.RemoteEnvironmentRequest request,
                                                  @Nullable JavaLanguageRuntimeConfiguration runtimeConfiguration,
                                                  @NotNull SimpleJavaParameters javaParameters,
                                                  @NotNull ParametersList vmParameters,
                                                  @NotNull Class<?> commandLineWrapper,
                                                  boolean dynamicVMOptions,
                                                  boolean dynamicParameters,
                                                  Charset cs) throws CantRunException {
    try {
      int pseudoUniquePrefix = new Random().nextInt(Integer.MAX_VALUE);
      File vmParamsFile = null;
      if (dynamicVMOptions) {
        List<String> toWrite = new ArrayList<>();
        for (String param : vmParameters.getList()) {
          if (isUserDefinedProperty(param)) {
            toWrite.add(param);
          }
          else {
            appendVmParameter(commandLine, request, param);
            }
          }if (!toWrite.isEmpty()) {
          vmParamsFile = FileUtil.createTempFile("idea_vm_params" + pseudoUniquePrefix, null);
          CommandLineWrapperUtil.writeWrapperFile(vmParamsFile, toWrite, cs);
        }
      }
      else {
        appendVmParameters(commandLine, request, vmParameters);
      }

      appendEncoding(javaParameters, commandLine, vmParameters);

      File appParamsFile = null;
      if (dynamicParameters) {
        appParamsFile = FileUtil.createTempFile("idea_app_params" + pseudoUniquePrefix, null);
        CommandLineWrapperUtil.writeWrapperFile(appParamsFile, javaParameters.getProgramParametersList().getList(), cs);
      }

      File classpathFile = FileUtil.createTempFile("idea_classpath" + pseudoUniquePrefix, null);
      Collection<IR.RemoteValue<String>> classPathParameters = getClassPathValues(request, runtimeConfiguration, javaParameters);
      Promises.collectResults(ContainerUtil.map(classPathParameters, IR.RemoteValue::promise)).onSuccess(__ -> {
        List<String> pathList = new ArrayList<>();
        for (IR.RemoteValue<String> parameter : classPathParameters) {
          pathList.add(parameter.getRemoteValue());
        }
        CommandLineWrapperUtil.writeWrapperFile(classpathFile, pathList, cs);
      });

      Map<String, String> map = new HashMap<>();
      //Map<String, String> map = ContainerUtil.stringMap(classpathFile.getAbsolutePath(), classPath.getPathsString());
      //todo[remoteServers]: support COMMAND_LINE_CONTENT
      //commandLine.putUserData(COMMAND_LINE_CONTENT, map);

      Set<IR.RemoteValue<String>> classpath = new LinkedHashSet<>();
      classpath.add(request.createUpload(PathUtil.getJarPathForClass(commandLineWrapper)));
      if (isUrlClassloader(vmParameters)) {
        if (!(request instanceof IR.LocalRunner.LocalEnvironmentRequest)) {
          throw new CantRunException("Cannot run application with UrlClassPath on the remote target.");
        }
        classpath.add(new IR.FixedValue<>(PathUtil.getJarPathForClass(UrlClassLoader.class)));
        classpath.add(new IR.FixedValue<>(PathUtil.getJarPathForClass(StringUtilRt.class)));
        classpath.add(new IR.FixedValue<>(PathUtil.getJarPathForClass(THashMap.class)));
        //explicitly enumerate jdk classes as UrlClassLoader doesn't delegate to parent classloader when loading resources
        //which leads to exceptions when coverage instrumentation tries to instrument loader class and its dependencies
        Sdk jdk = javaParameters.getJdk();
        if (jdk != null) {
          for (VirtualFile file : jdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
            String path = PathUtil.getLocalPath(file);
            if (StringUtil.isNotEmpty(path)) {
              classpath.add(new IR.FixedValue<>(path));
            }
          }
        }
      }
      commandLine.addParameter("-classpath");
      String pathSeparator = String.valueOf(request.getRemotePlatform().getPlatform().pathSeparator);
      commandLine.addParameter(new IR.CompositeValue<>(classpath, values -> StringUtil.join(values, pathSeparator)));

      commandLine.addParameter(commandLineWrapper.getName());
      commandLine.addParameter(request.createUpload(classpathFile.getAbsolutePath()));
      //todo[remoteServers]: support deleting files on termination
      //OSProcessHandler.deleteFileOnTermination(commandLine, classpathFile);

      if (vmParamsFile != null) {
        commandLine.addParameter("@vm_params");
        commandLine.addParameter(request.createUpload(vmParamsFile.getAbsolutePath()));
        map.put(vmParamsFile.getAbsolutePath(), FileUtil.loadFile(vmParamsFile));
        //todo[remoteServers]: support deleting files on termination
        //OSProcessHandler.deleteFileOnTermination(commandLine, vmParamsFile);
      }

      if (appParamsFile != null) {
        commandLine.addParameter("@app_params");
        commandLine.addParameter(request.createUpload(appParamsFile.getAbsolutePath()));
        map.put(appParamsFile.getAbsolutePath(), FileUtil.loadFile(appParamsFile));
        //todo[remoteServers]: support deleting files on termination
        //OSProcessHandler.deleteFileOnTermination(commandLine, appParamsFile);
      }
    }
    catch (IOException e) {
      throwUnableToCreateTempFile(e);
    }
  }

  private static void setClasspathJarParams(@NotNull IR.NewCommandLine commandLine,
                                            @NotNull IR.RemoteEnvironmentRequest request,
                                            @Nullable JavaLanguageRuntimeConfiguration runtimeConfiguration,
                                            @NotNull SimpleJavaParameters javaParameters,
                                            @NotNull ParametersList vmParameters,
                                            @NotNull Class<?> commandLineWrapper,
                                            boolean dynamicVMOptions,
                                            boolean dynamicParameters) throws CantRunException {
    try {
      Manifest manifest = new Manifest();
      manifest.getMainAttributes().putValue("Created-By", ApplicationNamesInfo.getInstance().getFullProductName());

      String manifestText = "";
      if (dynamicVMOptions) {
        List<String> properties = new ArrayList<>();
        for (String param : vmParameters.getList()) {
          if (isUserDefinedProperty(param)) {
            properties.add(param);
          }
          else {
            appendVmParameter(commandLine, request, param);
          }
        }
        manifest.getMainAttributes().putValue("VM-Options", ParametersListUtil.join(properties));
        manifestText += "VM-Options: " + ParametersListUtil.join(properties) + "\n";
      }
      else {
        appendVmParameters(commandLine, request, vmParameters);
      }

      appendEncoding(javaParameters, commandLine, vmParameters);

      if (dynamicParameters) {
        manifest.getMainAttributes()
          .putValue("Program-Parameters", ParametersListUtil.join(javaParameters.getProgramParametersList().getList()));
        manifestText += "Program-Parameters: " + ParametersListUtil.join(javaParameters.getProgramParametersList().getList()) + "\n";
      }

      File classpathJarFile = FileUtil.createTempFile(CommandLineWrapperUtil.CLASSPATH_JAR_FILE_NAME_PREFIX + Math.abs(new Random().nextInt()), ".jar", true);
      Collection<IR.RemoteValue<String>> classPathParameters = getClassPathValues(request, runtimeConfiguration, javaParameters);
      Promises.collectResults(ContainerUtil.map(classPathParameters, IR.RemoteValue::promise)).onSuccess(__ -> {
        try {
          boolean notEscape = vmParameters.hasParameter(PROPERTY_DO_NOT_ESCAPE_CLASSPATH_URL);
          StringBuilder classPath = new StringBuilder();
          for (IR.RemoteValue<String> parameter : classPathParameters) {
            if (classPath.length() > 0) classPath.append(' ');
            File file = new File(parameter.getRemoteValue());
            String url = (notEscape ? file.toURL() : file.toURI().toURL()).toString();
            classPath.append(!StringUtil.endsWithChar(url, '/') && new File(parameter.getLocalValue()).isDirectory() ? url + "/" : url);
          }
          CommandLineWrapperUtil.fillClasspathJarFile(manifest, classPath.toString(), classpathJarFile);
        }
        catch (IOException e) {
          //todo[remoteServers]: interrupt preparing environment
        }
      });

      String jarFilePath = classpathJarFile.getAbsolutePath();
      commandLine.addParameter("-classpath");
      if (dynamicVMOptions || dynamicParameters) {
        char pathSeparator = request.getRemotePlatform().getPlatform().pathSeparator;
        commandLine.addParameter(request.createUpload(PathUtil.getJarPathForClass(commandLineWrapper) + pathSeparator + jarFilePath));
        commandLine.addParameter(request.createUpload(commandLineWrapper.getName()));
      }
      commandLine.addParameter(request.createUpload(jarFilePath));

      //todo[remoteServers]: support COMMAND_LINE_CONTENT
      //commandLine.putUserData(COMMAND_LINE_CONTENT, ContainerUtil.stringMap(jarFilePath, manifestText + "Class-Path: " + path.getPathsString()));

      //todo[remoteServers]: support deleting files on termination
      //OSProcessHandler.deleteFileOnTermination(commandLine, classpathJarFile);
    }
    catch (IOException e) {
      throwUnableToCreateTempFile(e);
    }
  }

  @SuppressWarnings("SpellCheckingInspection")
  private static boolean isUserDefinedProperty(String param) {
    return param.startsWith("-D") && !(param.startsWith("-Dsun.") || param.startsWith("-Djava."));
  }

  private static void throwUnableToCreateTempFile(IOException cause) throws CantRunException {
    throw new CantRunException("Failed to create a temporary file in " + FileUtilRt.getTempDirectory(), cause);
  }

  private static void appendParamsEncodingClasspath(@NotNull IR.NewCommandLine commandLine,
                                                    @NotNull IR.RemoteEnvironmentRequest request,
                                                    @Nullable JavaLanguageRuntimeConfiguration runtimeConfiguration,
                                                    @NotNull SimpleJavaParameters javaParameters,
                                                    @NotNull ParametersList vmParameters) {
    appendVmParameters(commandLine, request, vmParameters);
    appendEncoding(javaParameters, commandLine, vmParameters);
    PathsList classPath = javaParameters.getClassPath();
    if (!classPath.isEmpty() && !explicitClassPath(vmParameters)) {
      commandLine.addParameter("-classpath");
      List<IR.RemoteValue<String>> pathValues = getClassPathValues(request, runtimeConfiguration, javaParameters);
      String pathSeparator = String.valueOf(request.getRemotePlatform().getPlatform().pathSeparator);
      commandLine.addParameter(new IR.CompositeValue<>(pathValues, values -> StringUtil.join(values, pathSeparator)));
    }

    PathsList modulePath = javaParameters.getModulePath();
    if (!modulePath.isEmpty() && !explicitModulePath(vmParameters)) {
      commandLine.addParameter("-p");
      commandLine.addParameter(modulePath.getPathsString());
    }
  }

  private static void appendVmParameters(@NotNull IR.NewCommandLine commandLine,
                                         @NotNull IR.RemoteEnvironmentRequest request,
                                         @NotNull ParametersList vmParameters) {
    for (String vmParameter : vmParameters.getList()) {
      appendVmParameter(commandLine, request, vmParameter);
    }
  }

  private static void appendVmParameter(@NotNull IR.NewCommandLine commandLine,
                                        @NotNull IR.RemoteEnvironmentRequest request,
                                        @NotNull String vmParameter) {
    if (SystemProperties.getBooleanProperty("remote.servers.ignore.vm.parameter", false)) {
      commandLine.addParameter(vmParameter);
      return;
    }

    if (vmParameter.startsWith("-agentpath:")) {
      appendVmAgentParameter(commandLine, request, vmParameter, "-agentpath:");
    }
    else if (vmParameter.startsWith("-javaagent:")) {
      appendVmAgentParameter(commandLine, request, vmParameter, "-javaagent:");
    }
    else {
      commandLine.addParameter(vmParameter);
    }
  }

  private static void appendVmAgentParameter(@NotNull IR.NewCommandLine commandLine,
                                             @NotNull IR.RemoteEnvironmentRequest request,
                                             @NotNull String vmParameter, 
                                             @NotNull String prefix) {
    String value = StringUtil.trimStart(vmParameter, prefix);
    int equalsSign = value.indexOf('=');
    String path = equalsSign > -1 ? value.substring(0, equalsSign) : value;
    if (!path.endsWith(".jar")) {
      // ignore non-cross-platform agents
      return;
    }
    String suffix = equalsSign > -1 ? value.substring(equalsSign) : "";
    commandLine.addParameter(new IR.MapValue<>(request.createUpload(path), v -> prefix + v + suffix));
  }

  @NotNull
  private static List<IR.RemoteValue<String>> getClassPathValues(@NotNull IR.RemoteEnvironmentRequest request,
                                                                 @Nullable JavaLanguageRuntimeConfiguration runtimeConfiguration,
                                                                 @NotNull SimpleJavaParameters javaParameters) {
    String localJdkPath = ObjectUtils.doIfNotNull(javaParameters.getJdk(), jdk -> jdk.getHomePath());
    String remoteJdkPath = runtimeConfiguration != null ? runtimeConfiguration.getHomePath() : "";

    ArrayList<IR.RemoteValue<String>> result = new ArrayList<>();
    for (String path : javaParameters.getClassPath().getPathList()) {
      if (localJdkPath == null || !path.startsWith(localJdkPath)) {
        result.add(request.createUpload(path));
      }
      else {
        char separator = request.getRemotePlatform().getPlatform().fileSeparator;
        result.add(new IR.FixedValue<>(remoteJdkPath + separator + StringUtil.trimStart(path, localJdkPath)));
      }
    }
    return result;
  }

  private static void appendEncoding(@NotNull SimpleJavaParameters javaParameters,
                                     @NotNull IR.NewCommandLine commandLine,
                                     @NotNull ParametersList parametersList) {
    // for correct handling of process's input and output, values of file.encoding and charset of CommandLine object should be in sync
    String encoding = parametersList.getPropertyValue("file.encoding");
    if (encoding == null) {
      Charset charset = javaParameters.getCharset();
      if (charset == null) charset = EncodingManager.getInstance().getDefaultCharset();
      commandLine.addParameter("-Dfile.encoding=" + charset.name());
      commandLine.setCharset(charset);
    }
    else {
      try {
        commandLine.setCharset(Charset.forName(encoding));
      }
      catch (UnsupportedCharsetException | IllegalCharsetNameException ignore) {
      }
    }
  }

  private static List<IR.RemoteValue<String>> getMainClassParams(SimpleJavaParameters javaParameters,
                                                                 @NotNull IR.RemoteEnvironmentRequest request) throws CantRunException {
    String mainClass = javaParameters.getMainClass();
    String moduleName = javaParameters.getModuleName();
    String jarPath = javaParameters.getJarPath();
    if (mainClass != null && moduleName != null) {
      return Arrays.asList(new IR.FixedValue<>("-m"), new IR.FixedValue<>(moduleName + '/' + mainClass));
    }
    else if (mainClass != null) {
      return Collections.singletonList(new IR.FixedValue<>(mainClass));
    }
    else if (jarPath != null) {
      return Arrays.asList(new IR.FixedValue<>("-jar"), request.createUpload(jarPath));
    }
    else {
      throw new CantRunException(ExecutionBundle.message("main.class.is.not.specified.error.message"));
    }
  }

  private static @Nullable Class<?> getCommandLineWrapperClass() {
    try {
      return Class.forName(WRAPPER_CLASS);
    }
    catch (ClassNotFoundException e) {
      return null;
    }
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
  /** @deprecated use {@link SimpleJavaParameters#toCommandLine()} */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
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
  //</editor-fold>
}
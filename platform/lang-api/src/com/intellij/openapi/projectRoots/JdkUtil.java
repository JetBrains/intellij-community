// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.execution.CantRunException;
import com.intellij.execution.CommandLineWrapperUtil;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetedCommandLineBuilder;
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentFactory;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.target.value.TargetValue;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public final class JdkUtil {
  public static final Key<Map<String, String>> COMMAND_LINE_CONTENT = Key.create("command.line.content");

  /**
   * The VM property is needed to workaround incorrect escaped URLs handling in WebSphere,
   * see <a href="https://youtrack.jetbrains.com/issue/IDEA-126859#comment=27-778948">IDEA-126859</a> for additional details
   */
  public static final String PROPERTY_DO_NOT_ESCAPE_CLASSPATH_URL = "idea.do.not.escape.classpath.url";

  private static final String WRAPPER_CLASS = "com.intellij.rt.execution.CommandLineWrapper";
  private static final String JAVAAGENT = "-javaagent";
  private static final Logger LOG = Logger.getInstance(JdkUtil.class);

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
    TargetedCommandLineBuilder commandLine = new TargetedCommandLineBuilder(request);
    JavaLanguageRuntimeConfiguration javaConfiguration = targetConfiguration != null
                                                         ? targetConfiguration.getRuntimes().findByType(JavaLanguageRuntimeConfiguration.class)
                                                         : null;
    if (request instanceof LocalTargetEnvironmentRequest || targetConfiguration == null) {
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
      Platform platform = request.getTargetPlatform().getPlatform();
      String java = platform == Platform.WINDOWS ? "java.exe" : "java";
      commandLine.setExePath(StringUtil.join(new String[]{javaConfiguration.getHomePath(), "bin", java}, String.valueOf(platform.fileSeparator)));
    }
    setupCommandLine(commandLine, request, javaParameters, javaConfiguration);
    return commandLine;
  }

  public static @NotNull GeneralCommandLine setupJVMCommandLine(@NotNull SimpleJavaParameters javaParameters) throws CantRunException {
    LocalTargetEnvironmentFactory environmentFactory = new LocalTargetEnvironmentFactory();
    TargetEnvironmentRequest request = environmentFactory.createRequest();
    return environmentFactory.prepareRemoteEnvironment(request, new EmptyProgressIndicator())
      .createGeneralCommandLine(setupJVMCommandLine(javaParameters, request, null).build());
  }

  private static void setupCommandLine(TargetedCommandLineBuilder commandLine,
                                       TargetEnvironmentRequest request,
                                       SimpleJavaParameters javaParameters,
                                       @Nullable JavaLanguageRuntimeConfiguration runtimeConfiguration) throws CantRunException {
    String workingDirectory = javaParameters.getWorkingDirectory();
    if (workingDirectory != null) {
      String remoteAppFolder = Optional.ofNullable(runtimeConfiguration)
        .map(JavaLanguageRuntimeConfiguration::getApplicationFolder)
        .map(StringUtil::nullize)
        .orElse(null);

      TargetEnvironmentRequest.Volume volume = request.createUploadRoot(remoteAppFolder, false);
      commandLine.setWorkingDirectory(volume.createUpload(workingDirectory));
    }
    javaParameters.getEnv().forEach((key, value) -> commandLine.addEnvironmentVariable(key, value));

    if (request instanceof LocalTargetEnvironmentRequest) {
      ParentEnvironmentType type = javaParameters.isPassParentEnvs() ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE;
      ((LocalTargetEnvironmentRequest)request).setParentEnvironmentType(type);
    }

    TargetEnvironmentRequest.Volume classPathVolume = request.createTempVolume();
    TargetEnvironmentRequest.Volume agentVolume = request.createTempVolume();

    ParametersList vmParameters = javaParameters.getVMParametersList();
    boolean dynamicClasspath = javaParameters.isDynamicClasspath();
    boolean dynamicVMOptions = dynamicClasspath && javaParameters.isDynamicVMOptions() && useDynamicVMOptions();
    boolean dynamicParameters = dynamicClasspath && javaParameters.isDynamicParameters() && useDynamicParameters();
    boolean dynamicMainClass = false;

    // copies agent .jar files to the beginning of the classpath to load agent classes faster
    if (isUrlClassloader(vmParameters)) {
      if (!(request instanceof LocalTargetEnvironmentRequest)) {
        throw new CantRunException("Cannot run application with UrlClassPath on the remote target.");
      }
      for (String parameter : vmParameters.getParameters()) {
        if (parameter.startsWith(JAVAAGENT)) {
          int agentArgsIdx = parameter.indexOf("=", JAVAAGENT.length());
          javaParameters.getClassPath().addFirst(parameter.substring(JAVAAGENT.length() + 1, agentArgsIdx > -1 ? agentArgsIdx : parameter.length()));
        }
      }
    }

    if (dynamicClasspath) {
      Charset cs = StandardCharsets.UTF_8;  // todo detect JNU charset from VM options?
      Class<?> commandLineWrapper;
      if (javaParameters.isArgFile()) {
        setArgFileParams(commandLine, classPathVolume, agentVolume, runtimeConfiguration, javaParameters, vmParameters, dynamicVMOptions,
                         dynamicParameters, cs);
        dynamicMainClass = dynamicParameters;
      }
      else if (!explicitClassPath(vmParameters) &&
               javaParameters.getJarPath() == null &&
               (commandLineWrapper = getCommandLineWrapperClass()) != null) {
        if (javaParameters.isUseClasspathJar()) {
          setClasspathJarParams(commandLine, request, classPathVolume, agentVolume,
                                runtimeConfiguration, javaParameters, vmParameters, commandLineWrapper,
                                dynamicVMOptions, dynamicParameters);
        }
        else if (javaParameters.isClasspathFile()) {
          setCommandLineWrapperParams(commandLine, request, classPathVolume, agentVolume,
                                      runtimeConfiguration, javaParameters,
                                      vmParameters, commandLineWrapper, dynamicVMOptions, dynamicParameters, cs);
        }
      }
      else {
        dynamicClasspath = dynamicParameters = false;
      }
    }

    if (!dynamicClasspath) {
      appendParamsEncodingClasspath(commandLine, request, classPathVolume, agentVolume,
                                    runtimeConfiguration, javaParameters, vmParameters);
    }

    if (!dynamicMainClass) {
      for (TargetValue<String> parameter : getMainClassParams(javaParameters, classPathVolume)) {
        commandLine.addParameter(parameter);
      }
    }

    if (!dynamicParameters) {
      for (String parameter : javaParameters.getProgramParametersList().getList()) {
        commandLine.addParameter(parameter);
      }
    }
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

  private static void setArgFileParams(TargetedCommandLineBuilder commandLine,
                                       TargetEnvironmentRequest.Volume classPathVolume,
                                       TargetEnvironmentRequest.Volume agentVolume,
                                       @Nullable JavaLanguageRuntimeConfiguration runtimeConfiguration,
                                       SimpleJavaParameters javaParameters,
                                       ParametersList vmParameters,
                                       boolean dynamicVMOptions,
                                       boolean dynamicParameters,
                                       Charset cs) throws CantRunException {
    try {
      Platform platform = classPathVolume.getRequest().getTargetPlatform().getPlatform();
      String pathSeparator = String.valueOf(platform.pathSeparator);
      Collection<Promise<String>> promises = new ArrayList<>();
      TargetValue<String> classPathParameter;
      PathsList classPath = javaParameters.getClassPath();
      if (!classPath.isEmpty() && !explicitClassPath(vmParameters)) {
        List<TargetValue<String>> pathValues = getClassPathValues(classPathVolume, runtimeConfiguration, javaParameters);
        classPathParameter = TargetValue.composite(pathValues, values -> StringUtil.join(values, pathSeparator));
        promises.add(classPathParameter.getTargetValue());
      }
      else {
        classPathParameter = null;
      }

      TargetValue<String> modulePathParameter;
      PathsList modulePath = javaParameters.getModulePath();
      if (!modulePath.isEmpty() && !explicitModulePath(vmParameters)) {
        List<TargetValue<String>> pathValues = getClassPathValues(classPathVolume, runtimeConfiguration, javaParameters);
        modulePathParameter = TargetValue.composite(pathValues, values -> StringUtil.join(values, pathSeparator));
        promises.add(modulePathParameter.getTargetValue());
      }
      else {
        modulePathParameter = null;
      }

      List<TargetValue<String>> mainClassParameters = dynamicParameters ? getMainClassParams(javaParameters, classPathVolume)
                                                                        : Collections.emptyList();

      promises.addAll(ContainerUtil.map(mainClassParameters, TargetValue::getTargetValue));

      File argFile = FileUtil.createTempFile("idea_arg_file" + new Random().nextInt(Integer.MAX_VALUE), null);
      commandLine.addFileToDeleteOnTermination(argFile);

      Promises.collectResults(promises).onSuccess(__ -> {
        List<String> fileArgs = new ArrayList<>();
        if (dynamicVMOptions) {
          fileArgs.addAll(vmParameters.getList());
        }
        else {
          appendVmParameters(commandLine, agentVolume, vmParameters);
        }
        try {
          if (classPathParameter != null) {
            fileArgs.add("-classpath");
            fileArgs.add(classPathParameter.getTargetValue().blockingGet(0));
          }
          if (modulePathParameter != null) {
            fileArgs.add("-p");
            fileArgs.add(modulePathParameter.getTargetValue().blockingGet(0));
          }

          for (TargetValue<String> mainClassParameter : mainClassParameters) {
            fileArgs.add(mainClassParameter.getTargetValue().blockingGet(0));
          }
          if (dynamicParameters) {
            fileArgs.addAll(javaParameters.getProgramParametersList().getList());
          }

          CommandLineWrapperUtil.writeArgumentsFile(argFile, fileArgs, platform.lineSeparator, cs);
        }
        catch (IOException e) {
          //todo[remoteServers]: interrupt preparing environment
        }
        catch (ExecutionException | TimeoutException e) {
          LOG.error("Couldn't resolve target value", e);
        }
      });

      HashMap<String, String> commandLineContent = new HashMap<>();
      commandLine.putUserData(COMMAND_LINE_CONTENT, commandLineContent);

      appendEncoding(javaParameters, commandLine, vmParameters);
      TargetValue<String> argFileParameter = classPathVolume.createUpload(argFile.getAbsolutePath());
      commandLine.addParameter(TargetValue.map(argFileParameter, s -> "@" + s));
      addCommandLineContentOnResolve(commandLineContent, argFile, argFileParameter);
    }
    catch (IOException e) {
      throwUnableToCreateTempFile(e);
    }
  }

  private static void setCommandLineWrapperParams(TargetedCommandLineBuilder commandLine,
                                                  TargetEnvironmentRequest request,
                                                  TargetEnvironmentRequest.Volume classPathVolume,
                                                  TargetEnvironmentRequest.Volume agentVolume,
                                                  @Nullable JavaLanguageRuntimeConfiguration runtimeConfiguration,
                                                  SimpleJavaParameters javaParameters,
                                                  ParametersList vmParameters,
                                                  Class<?> commandLineWrapper,
                                                  boolean dynamicVMOptions,
                                                  boolean dynamicParameters,
                                                  Charset cs) throws CantRunException {
    try {
      String lineSeparator = request.getTargetPlatform().getPlatform().lineSeparator;
      int pseudoUniquePrefix = new Random().nextInt(Integer.MAX_VALUE);
      File vmParamsFile = null;
      if (dynamicVMOptions) {
        List<String> toWrite = new ArrayList<>();
        for (String param : vmParameters.getList()) {
          if (isUserDefinedProperty(param)) {
            toWrite.add(param);
          }
          else {
            appendVmParameter(commandLine, agentVolume, param);
          }
        }
        if (!toWrite.isEmpty()) {
          vmParamsFile = FileUtil.createTempFile("idea_vm_params" + pseudoUniquePrefix, null);
          commandLine.addFileToDeleteOnTermination(vmParamsFile);
          CommandLineWrapperUtil.writeWrapperFile(vmParamsFile, toWrite, lineSeparator, cs);
        }
      }
      else {
        appendVmParameters(commandLine, agentVolume, vmParameters);
      }

      appendEncoding(javaParameters, commandLine, vmParameters);

      File appParamsFile = null;
      if (dynamicParameters) {
        appParamsFile = FileUtil.createTempFile("idea_app_params" + pseudoUniquePrefix, null);
        commandLine.addFileToDeleteOnTermination(appParamsFile);
        CommandLineWrapperUtil.writeWrapperFile(appParamsFile, javaParameters.getProgramParametersList().getList(), lineSeparator, cs);
      }

      File classpathFile = FileUtil.createTempFile("idea_classpath" + pseudoUniquePrefix, null);
      commandLine.addFileToDeleteOnTermination(classpathFile);

      Collection<TargetValue<String>> classPathParameters = getClassPathValues(classPathVolume, runtimeConfiguration, javaParameters);
      Promises.collectResults(ContainerUtil.map(classPathParameters, TargetValue::getTargetValue)).onSuccess(pathList -> {
        try {
          CommandLineWrapperUtil.writeWrapperFile(classpathFile, pathList, lineSeparator, cs);
        }
        catch (IOException e) {
          //todo[remoteServers]: interrupt preparing environment
        }
      });

      Set<TargetValue<String>> classpath = new LinkedHashSet<>();
      TargetEnvironmentRequest.Volume tempVolume = request.getDefaultVolume();

      classpath.add(tempVolume.createUpload(PathUtil.getJarPathForClass(commandLineWrapper)));
      if (isUrlClassloader(vmParameters)) {
        if (!(request instanceof LocalTargetEnvironmentRequest)) {
          throw new CantRunException("Cannot run application with UrlClassPath on the remote target.");
        }
        classpath.add(TargetValue.fixed(PathUtil.getJarPathForClass(UrlClassLoader.class)));
        classpath.add(TargetValue.fixed(PathUtil.getJarPathForClass(StringUtilRt.class)));
        classpath.add(TargetValue.fixed(PathUtil.getJarPathForClass(THashMap.class)));
        //explicitly enumerate jdk classes as UrlClassLoader doesn't delegate to parent classloader when loading resources
        //which leads to exceptions when coverage instrumentation tries to instrument loader class and its dependencies
        Sdk jdk = javaParameters.getJdk();
        if (jdk != null) {
          for (VirtualFile file : jdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
            String path = PathUtil.getLocalPath(file);
            if (StringUtil.isNotEmpty(path)) {
              classpath.add(TargetValue.fixed(path));
            }
          }
        }
      }
      commandLine.addParameter("-classpath");
      String pathSeparator = String.valueOf(request.getTargetPlatform().getPlatform().pathSeparator);
      commandLine.addParameter(TargetValue.composite(classpath, values -> StringUtil.join(values, pathSeparator)));

      commandLine.addParameter(commandLineWrapper.getName());

      Map<String, String> commandLineContent = new HashMap<>();
      commandLine.putUserData(COMMAND_LINE_CONTENT, commandLineContent);

      TargetValue<String> classPathParameter = tempVolume.createUpload(classpathFile.getAbsolutePath());
      commandLine.addParameter(classPathParameter);
      addCommandLineContentOnResolve(commandLineContent, classpathFile, classPathParameter);

      if (vmParamsFile != null) {
        commandLine.addParameter("@vm_params");
        TargetValue<String> vmParamsParameter = tempVolume.createUpload(vmParamsFile.getAbsolutePath());
        commandLine.addParameter(vmParamsParameter);
        addCommandLineContentOnResolve(commandLineContent, vmParamsFile, vmParamsParameter);
      }

      if (appParamsFile != null) {
        commandLine.addParameter("@app_params");
        TargetValue<String> appParamsParameter = tempVolume.createUpload(appParamsFile.getAbsolutePath());
        commandLine.addParameter(appParamsParameter);
        addCommandLineContentOnResolve(commandLineContent, appParamsFile, appParamsParameter);
      }
    }
    catch (IOException e) {
      throwUnableToCreateTempFile(e);
    }
  }

  private static void addCommandLineContentOnResolve(Map<String, String> commandLineContent, File localFile, TargetValue<String> value) {
    value.getTargetValue().onSuccess(resolved -> {
      try {
        commandLineContent.put(resolved, FileUtil.loadFile(localFile));
      }
      catch (IOException e) {
        LOG.error("Cannot add command line content for value " + resolved, e);
      }
    });
  }

  private static void setClasspathJarParams(TargetedCommandLineBuilder commandLine,
                                            TargetEnvironmentRequest request,
                                            TargetEnvironmentRequest.Volume classPathVolume,
                                            TargetEnvironmentRequest.Volume agentVolume,
                                            @Nullable JavaLanguageRuntimeConfiguration runtimeConfiguration,
                                            SimpleJavaParameters javaParameters,
                                            ParametersList vmParameters,
                                            Class<?> commandLineWrapper,
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
            appendVmParameter(commandLine, agentVolume, param);
          }
        }
        manifest.getMainAttributes().putValue("VM-Options", ParametersListUtil.join(properties));
        manifestText += "VM-Options: " + ParametersListUtil.join(properties) + "\n";
      }
      else {
        appendVmParameters(commandLine, agentVolume, vmParameters);
      }

      appendEncoding(javaParameters, commandLine, vmParameters);

      if (dynamicParameters) {
        manifest.getMainAttributes()
          .putValue("Program-Parameters", ParametersListUtil.join(javaParameters.getProgramParametersList().getList()));
        manifestText += "Program-Parameters: " + ParametersListUtil.join(javaParameters.getProgramParametersList().getList()) + "\n";
      }

      String jarFileContentPrefix = manifestText + "Class-Path: ";
      Map<String, String> commandLineContent = new HashMap<>();
      commandLine.putUserData(COMMAND_LINE_CONTENT, commandLineContent);

      File classpathJarFile =
        FileUtil.createTempFile(CommandLineWrapperUtil.CLASSPATH_JAR_FILE_NAME_PREFIX + Math.abs(new Random().nextInt()), ".jar", true);
      commandLine.addFileToDeleteOnTermination(classpathJarFile);

      String jarFilePath = classpathJarFile.getAbsolutePath();
      commandLine.addParameter("-classpath");
      if (dynamicVMOptions || dynamicParameters) {
        char pathSeparator = request.getTargetPlatform().getPlatform().pathSeparator;
        commandLine
          .addParameter(classPathVolume.createUpload(PathUtil.getJarPathForClass(commandLineWrapper) + pathSeparator + jarFilePath));
        commandLine.addParameter(classPathVolume.createUpload(commandLineWrapper.getName()));
      }
      TargetValue<String> jarFileValue = classPathVolume.createUpload(jarFilePath);
      commandLine.addParameter(jarFileValue);

      Collection<TargetValue<String>> classPathParameters = getClassPathValues(classPathVolume, runtimeConfiguration, javaParameters);
      Promises.collectResults(ContainerUtil.map(classPathParameters, TargetValue::getTargetValue)).onSuccess(targetClassPathParameters -> {
        try {
          boolean notEscape = vmParameters.hasParameter(PROPERTY_DO_NOT_ESCAPE_CLASSPATH_URL);
          StringBuilder classPath = new StringBuilder();
          for (TargetValue<String> parameter : classPathParameters) {
            if (classPath.length() > 0) classPath.append(' ');
            String localValue = parameter.getLocalValue().blockingGet(0);
            String targetValue = parameter.getTargetValue().blockingGet(0);
            if (targetValue == null || localValue == null) {
              throw new ExecutionException("Couldn't resolve target value", null);
            }
            File file = new File(targetValue);
            @SuppressWarnings("deprecation") String url = (notEscape ? file.toURL() : file.toURI().toURL()).toString();
            classPath.append(!StringUtil.endsWithChar(url, '/') && new File(localValue).isDirectory() ? url + "/" : url);
          }
          CommandLineWrapperUtil.fillClasspathJarFile(manifest, classPath.toString(), classpathJarFile);

          jarFileValue.getTargetValue().onSuccess(value -> {
            commandLineContent.put(value, jarFileContentPrefix + classPath.toString());
          });
        }
        catch (IOException | ExecutionException e) {
          //todo[remoteServers]: interrupt preparing environment
        }
        catch (TimeoutException e) {
          LOG.error("Couldn't resolve target value", e);
        }
      });

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

  private static void appendParamsEncodingClasspath(TargetedCommandLineBuilder commandLine,
                                                    TargetEnvironmentRequest request,
                                                    TargetEnvironmentRequest.Volume classPathVolume,
                                                    TargetEnvironmentRequest.Volume agentVolume,
                                                    @Nullable JavaLanguageRuntimeConfiguration runtimeConfiguration,
                                                    SimpleJavaParameters javaParameters,
                                                    ParametersList vmParameters) {
    appendVmParameters(commandLine, agentVolume, vmParameters);
    appendEncoding(javaParameters, commandLine, vmParameters);
    PathsList classPath = javaParameters.getClassPath();
    if (!classPath.isEmpty() && !explicitClassPath(vmParameters)) {
      commandLine.addParameter("-classpath");
      List<TargetValue<String>> pathValues = getClassPathValues(classPathVolume, runtimeConfiguration, javaParameters);
      String pathSeparator = String.valueOf(request.getTargetPlatform().getPlatform().pathSeparator);
      commandLine.addParameter(TargetValue.composite(pathValues, values -> StringUtil.join(values, pathSeparator)));
    }

    PathsList modulePath = javaParameters.getModulePath();
    if (!modulePath.isEmpty() && !explicitModulePath(vmParameters)) {
      commandLine.addParameter("-p");
      commandLine.addParameter(modulePath.getPathsString());
    }
  }

  private static void appendVmParameters(TargetedCommandLineBuilder commandLine,
                                         TargetEnvironmentRequest.Volume agentVolume,
                                         ParametersList vmParameters) {
    for (String vmParameter : vmParameters.getList()) {
      appendVmParameter(commandLine, agentVolume, vmParameter);
    }
  }

  private static void appendVmParameter(TargetedCommandLineBuilder commandLine,
                                        TargetEnvironmentRequest.Volume agentVolume,
                                        String vmParameter) {
    if (agentVolume.getRequest() instanceof LocalTargetEnvironmentRequest ||
        SystemProperties.getBooleanProperty("run.targets.ignore.vm.parameter", false)) {
      commandLine.addParameter(vmParameter);
      return;
    }

    if (vmParameter.startsWith("-agentpath:")) {
      appendVmAgentParameter(commandLine, agentVolume, vmParameter, "-agentpath:");
    }
    else if (vmParameter.startsWith("-javaagent:")) {
      appendVmAgentParameter(commandLine, agentVolume, vmParameter, "-javaagent:");
    }
    else {
      commandLine.addParameter(vmParameter);
    }
  }

  private static void appendVmAgentParameter(TargetedCommandLineBuilder commandLine,
                                             TargetEnvironmentRequest.Volume agentVolume,
                                             String vmParameter,
                                             String prefix) {
    String value = StringUtil.trimStart(vmParameter, prefix);
    int equalsSign = value.indexOf('=');
    String path = equalsSign > -1 ? value.substring(0, equalsSign) : value;
    if (!path.endsWith(".jar")) {
      // ignore non-cross-platform agents
      return;
    }
    String suffix = equalsSign > -1 ? value.substring(equalsSign) : "";
    commandLine.addParameter(TargetValue.map(agentVolume.createUpload(path), v -> prefix + v + suffix));
  }

  @NotNull
  private static List<TargetValue<String>> getClassPathValues(TargetEnvironmentRequest.Volume classPathVolume,
                                                              @Nullable JavaLanguageRuntimeConfiguration runtimeConfiguration,
                                                              SimpleJavaParameters javaParameters) {
    String localJdkPath = ObjectUtils.doIfNotNull(javaParameters.getJdk(), jdk -> jdk.getHomePath());
    String remoteJdkPath = runtimeConfiguration != null ? runtimeConfiguration.getHomePath() : null;

    ArrayList<TargetValue<String>> result = new ArrayList<>();
    for (String path : javaParameters.getClassPath().getPathList()) {
      if (localJdkPath == null || remoteJdkPath == null || !path.startsWith(localJdkPath)) {
        result.add(classPathVolume.createUpload(path));
      }
      else {
        char separator = classPathVolume.getRequest().getTargetPlatform().getPlatform().fileSeparator;
        result.add(
          TargetValue.fixed(FileUtil.toCanonicalPath(remoteJdkPath + separator + StringUtil.trimStart(path, localJdkPath), separator)));
      }
    }
    return result;
  }

  private static void appendEncoding(SimpleJavaParameters javaParameters, TargetedCommandLineBuilder commandLine, ParametersList parametersList) {
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

  private static List<TargetValue<String>> getMainClassParams(SimpleJavaParameters javaParameters,
                                                              TargetEnvironmentRequest.Volume volume) throws CantRunException {
    String mainClass = javaParameters.getMainClass();
    String moduleName = javaParameters.getModuleName();
    String jarPath = javaParameters.getJarPath();
    if (mainClass != null && moduleName != null) {
      return Arrays.asList(TargetValue.fixed("-m"), TargetValue.fixed(moduleName + '/' + mainClass));
    }
    else if (mainClass != null) {
      return Collections.singletonList(TargetValue.fixed(mainClass));
    }
    else if (jarPath != null) {
      return Arrays.asList(TargetValue.fixed("-jar"), volume.createUpload(jarPath));
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
    TargetedCommandLineBuilder targetedCommandLineBuilder = new TargetedCommandLineBuilder(request);
    setupCommandLine(targetedCommandLineBuilder, request, javaParameters, null);
    LocalTargetEnvironment environment = environmentFactory.prepareRemoteEnvironment(request, new EmptyProgressIndicator());
    GeneralCommandLine generalCommandLine = environment.createGeneralCommandLine(targetedCommandLineBuilder.build());
    commandLine.withParentEnvironmentType(javaParameters.isPassParentEnvs() ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE);
    commandLine.getParametersList().addAll(generalCommandLine.getParametersList().getList());
    commandLine.getEnvironment().putAll(generalCommandLine.getEnvironment());
  }
  //</editor-fold>
}
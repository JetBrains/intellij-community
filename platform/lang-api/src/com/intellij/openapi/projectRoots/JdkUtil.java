// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.execution.CantRunException;
import com.intellij.execution.CommandLineWrapperUtil;
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
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.lang.JavaVersion;
import com.intellij.util.lang.UrlClassLoader;
import gnu.trove.THashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
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

    JdkCommandLineSetup setup = new JdkCommandLineSetup(request, targetConfiguration);
    setup.setupJavaExePath(javaParameters);
    setup.setupCommandLine(javaParameters);
    return setup.getCommandLine();
  }

  public static @NotNull GeneralCommandLine setupJVMCommandLine(@NotNull SimpleJavaParameters javaParameters) throws CantRunException {
    LocalTargetEnvironmentFactory environmentFactory = new LocalTargetEnvironmentFactory();
    TargetEnvironmentRequest request = environmentFactory.createRequest();
    return environmentFactory.prepareRemoteEnvironment(request, new EmptyProgressIndicator())
      .createGeneralCommandLine(setupJVMCommandLine(javaParameters, request, null).build());
  }

  /*make private */
  static boolean isUrlClassloader(ParametersList vmParameters) {
    return UrlClassLoader.class.getName().equals(vmParameters.getPropertyValue("java.system.class.loader"));
  }

  /*make private */
  static boolean explicitClassPath(ParametersList vmParameters) {
    return vmParameters.hasParameter("-cp") || vmParameters.hasParameter("-classpath") || vmParameters.hasParameter("--class-path");
  }

  /*make private*/
  static boolean explicitModulePath(ParametersList vmParameters) {
    return vmParameters.hasParameter("-p") || vmParameters.hasParameter("--module-path");
  }

  /*make private*/
  static void setArgFileParams(JdkCommandLineSetup setup, TargetedCommandLineBuilder commandLine,
                               TargetEnvironmentRequest request,
                               TargetEnvironmentRequest.Volume classPathVolume,
                               TargetEnvironmentRequest.Volume agentVolume,
                               @Nullable JavaLanguageRuntimeConfiguration runtimeConfiguration,
                               SimpleJavaParameters javaParameters,
                               ParametersList vmParameters,
                               boolean dynamicVMOptions,
                               boolean dynamicParameters,
                               Charset cs) throws CantRunException {
    try {
      ArgFile argFile = new ArgFile(dynamicVMOptions, dynamicParameters, cs, setup.getPlatform());
      commandLine.addFileToDeleteOnTermination(argFile.getFile());

      PathsList classPath = javaParameters.getClassPath();
      if (!classPath.isEmpty() && !explicitClassPath(vmParameters)) {
        argFile.addPromisedParameter("-classpath", setup.composeClassPathValues(javaParameters, classPath));
      }

      PathsList modulePath = javaParameters.getModulePath();
      if (!modulePath.isEmpty() && !explicitModulePath(vmParameters)) {
        argFile.addPromisedParameter("-p", setup.composeClassPathValues(javaParameters, modulePath));
      }

      if (dynamicParameters) {
        for (TargetValue<String> nextMainClassParam : setup.getMainClassParams(javaParameters)) {
          argFile.addPromisedParameter(nextMainClassParam);
        }
      }

      if (!dynamicVMOptions) { // dynamic options will be handled later by ArgFile
        setup.appendVmParameters(vmParameters);
      }

      argFile.scheduleWriteFileWhenReady(javaParameters, vmParameters);

      HashMap<String, String> commandLineContent = new HashMap<>();
      commandLine.putUserData(COMMAND_LINE_CONTENT, commandLineContent);

      setup.appendEncoding(javaParameters, vmParameters);
      TargetValue<String> argFileParameter = classPathVolume.createUpload(argFile.getFile().getAbsolutePath());
      commandLine.addParameter(TargetValue.map(argFileParameter, s -> "@" + s));
      addCommandLineContentOnResolve(commandLineContent, argFile.getFile(), argFileParameter);
    }
    catch (IOException e) {
      throwUnableToCreateTempFile(e);
    }
  }

  static class ArgFile {
    private final File myFile;
    private final Charset myCharset;
    private final Platform myPlatform;
    private final boolean myDynamicVMOptions;
    private final boolean myDynamicParameters;

    private final Map<String, TargetValue<String>> myPromisedOptionValues = new LinkedHashMap<>();
    private final List<TargetValue<String>> myPromisedParameters = new LinkedList<>();
    private final List<Promise<String>> myAllPromises = new LinkedList<>();

    ArgFile(boolean dynamicVMOptions, boolean dynamicParameters, Charset charset, Platform platform) throws IOException {
      myDynamicVMOptions = dynamicVMOptions;
      myDynamicParameters = dynamicParameters;
      myCharset = charset;
      myPlatform = platform;

      myFile = FileUtil.createTempFile("idea_arg_file" + new Random().nextInt(Integer.MAX_VALUE), null);
    }

    public File getFile() {
      return myFile;
    }

    public void addPromisedParameter(@NonNls String optionName, TargetValue<String> promisedValue) {
      myPromisedOptionValues.put(optionName, promisedValue);
      registerPromise(promisedValue);
    }

    public void addPromisedParameter(TargetValue<String> promisedValue) {
      myPromisedParameters.add(promisedValue);
      registerPromise(promisedValue);
    }

    public void scheduleWriteFileWhenReady(SimpleJavaParameters javaParameters, ParametersList vmParameters) {
      Promises.collectResults(myAllPromises).onSuccess(__ -> {
        try {
          writeArgFileNow(javaParameters, vmParameters);
        }
        catch (IOException e) {
          //todo[remoteServers]: interrupt preparing environment
        }
        catch (ExecutionException | TimeoutException e) {
          LOG.error("Couldn't resolve target value", e);
        }
      });
    }

    private void writeArgFileNow(SimpleJavaParameters javaParameters, ParametersList vmParameters)
      throws IOException, ExecutionException, TimeoutException {

      List<String> fileArgs = new ArrayList<>();
      if (myDynamicVMOptions) {
        fileArgs.addAll(vmParameters.getList());
      }

      for (Map.Entry<String, TargetValue<String>> entry : myPromisedOptionValues.entrySet()) {
        String nextOption = entry.getKey();
        TargetValue<String> nextResolvedValue = entry.getValue();

        fileArgs.add(nextOption);
        fileArgs.add(nextResolvedValue.getTargetValue().blockingGet(0));
      }

      for (TargetValue<String> nextResolvedParameter : myPromisedParameters) {
        fileArgs.add(nextResolvedParameter.getTargetValue().blockingGet(0));
      }

      if (myDynamicParameters) {
        fileArgs.addAll(javaParameters.getProgramParametersList().getList());
      }

      CommandLineWrapperUtil.writeArgumentsFile(myFile, fileArgs, myPlatform.lineSeparator, myCharset);
    }

    private void registerPromise(TargetValue<String> value) {
      myAllPromises.add(value.getTargetValue());
    }
  }

  /*make private*/
  static void setCommandLineWrapperParams(JdkCommandLineSetup setup, TargetedCommandLineBuilder commandLine,
                                          TargetEnvironmentRequest request,
                                          TargetEnvironmentRequest.Volume classPathVolume,
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
            setup.appendVmParameter(param);
          }
        }
        if (!toWrite.isEmpty()) {
          vmParamsFile = FileUtil.createTempFile("idea_vm_params" + pseudoUniquePrefix, null);
          commandLine.addFileToDeleteOnTermination(vmParamsFile);
          CommandLineWrapperUtil.writeWrapperFile(vmParamsFile, toWrite, lineSeparator, cs);
        }
      }
      else {
        setup.appendVmParameters(vmParameters);
      }

      setup.appendEncoding(javaParameters, vmParameters);

      File appParamsFile = null;
      if (dynamicParameters) {
        appParamsFile = FileUtil.createTempFile("idea_app_params" + pseudoUniquePrefix, null);
        commandLine.addFileToDeleteOnTermination(appParamsFile);
        CommandLineWrapperUtil.writeWrapperFile(appParamsFile, javaParameters.getProgramParametersList().getList(), lineSeparator, cs);
      }

      File classpathFile = FileUtil.createTempFile("idea_classpath" + pseudoUniquePrefix, null);
      commandLine.addFileToDeleteOnTermination(classpathFile);

      Collection<TargetValue<String>> classPathParameters = setup.getClassPathValues(javaParameters, javaParameters.getClassPath());
      Promises.collectResults(ContainerUtil.map(classPathParameters, TargetValue::getTargetValue)).onSuccess(pathList -> {
        try {
          CommandLineWrapperUtil.writeWrapperFile(classpathFile, pathList, lineSeparator, cs);
        }
        catch (IOException e) {
          //todo[remoteServers]: interrupt preparing environment
        }
      });

      Set<TargetValue<String>> classpath = new LinkedHashSet<>();

      classpath.add(classPathVolume.createUpload(PathUtil.getJarPathForClass(commandLineWrapper)));
      if (isUrlClassloader(vmParameters)) {
        if (!(request instanceof LocalTargetEnvironmentRequest)) {
          throw new CantRunException("Cannot run application with UrlClassPath on the remote target.");
        }
        //todo[remoteServers]: [why are they fixed below?]
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
              classpath.add(TargetValue.fixed(path)); //todo[remoteServers]: why fixed??
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

      TargetValue<String> classPathParameter = classPathVolume.createUpload(classpathFile.getAbsolutePath());
      commandLine.addParameter(classPathParameter);
      addCommandLineContentOnResolve(commandLineContent, classpathFile, classPathParameter);

      if (vmParamsFile != null) {
        commandLine.addParameter("@vm_params");
        TargetValue<String> vmParamsParameter = classPathVolume.createUpload(vmParamsFile.getAbsolutePath());
        commandLine.addParameter(vmParamsParameter);
        addCommandLineContentOnResolve(commandLineContent, vmParamsFile, vmParamsParameter);
      }

      if (appParamsFile != null) {
        commandLine.addParameter("@app_params");
        TargetValue<String> appParamsParameter = classPathVolume.createUpload(appParamsFile.getAbsolutePath());
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

  /*make private*/
  static void setClasspathJarParams(JdkCommandLineSetup setup, TargetedCommandLineBuilder commandLine,
                                    TargetEnvironmentRequest request,
                                    TargetEnvironmentRequest.Volume classPathVolume,
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
            setup.appendVmParameter(param);
          }
        }
        manifest.getMainAttributes().putValue("VM-Options", ParametersListUtil.join(properties));
        manifestText += "VM-Options: " + ParametersListUtil.join(properties) + "\n";
      }
      else {
        setup.appendVmParameters(vmParameters);
      }

      setup.appendEncoding(javaParameters, vmParameters);

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

      Collection<TargetValue<String>> classPathParameters = setup.getClassPathValues(javaParameters, javaParameters.getClassPath());
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
            commandLineContent.put(value, jarFileContentPrefix + classPath);
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
    setup.appendModulePath(javaParameters, vmParameters);
  }

  @SuppressWarnings("SpellCheckingInspection")
  private static boolean isUserDefinedProperty(String param) {
    return param.startsWith("-D") && !(param.startsWith("-Dsun.") || param.startsWith("-Djava."));
  }

  private static void throwUnableToCreateTempFile(IOException cause) throws CantRunException {
    throw new CantRunException("Failed to create a temporary file in " + FileUtilRt.getTempDirectory(), cause);
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
    TargetedCommandLineBuilder targetedCommandLineBuilder = new TargetedCommandLineBuilder(request);
    JdkCommandLineSetup setup = new JdkCommandLineSetup(request, null);
    setup.setupCommandLine(javaParameters);

    LocalTargetEnvironment environment = environmentFactory.prepareRemoteEnvironment(request, new EmptyProgressIndicator());
    GeneralCommandLine generalCommandLine = environment.createGeneralCommandLine(targetedCommandLineBuilder.build());
    commandLine.withParentEnvironmentType(javaParameters.isPassParentEnvs() ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE);
    commandLine.getParametersList().addAll(generalCommandLine.getParametersList().getList());
    commandLine.getEnvironment().putAll(generalCommandLine.getEnvironment());
  }
  //</editor-fold>
}
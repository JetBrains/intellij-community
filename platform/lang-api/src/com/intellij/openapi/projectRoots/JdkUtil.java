// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots;

import com.intellij.execution.CantRunException;
import com.intellij.execution.CommandLineWrapperUtil;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.JarUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.lang.UrlClassLoader;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
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

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.JdkUtil");
  private static final String WRAPPER_CLASS = "com.intellij.rt.execution.CommandLineWrapper";

  private JdkUtil() { }

  /**
   * Returns the specified attribute of the JDK (examines rt.jar), or {@code null} if cannot determine the value.
   */
  @Nullable
  public static String getJdkMainAttribute(@NotNull Sdk jdk, @NotNull Attributes.Name attribute) {
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

  public static GeneralCommandLine setupJVMCommandLine(@NotNull SimpleJavaParameters javaParameters) throws CantRunException {
    Sdk jdk = javaParameters.getJdk();
    if (jdk == null) throw new CantRunException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
    SdkTypeId type = jdk.getSdkType();
    if (!(type instanceof JavaSdkType)) throw new CantRunException(ExecutionBundle.message("run.configuration.error.no.jdk.specified"));
    String exePath = ((JavaSdkType)type).getVMExecutablePath(jdk);
    if (exePath == null) throw new CantRunException(ExecutionBundle.message("run.configuration.cannot.find.vm.executable"));

    GeneralCommandLine commandLine = new GeneralCommandLine(exePath);
    setupCommandLine(commandLine, javaParameters);
    return commandLine;
  }

  private static void setupCommandLine(GeneralCommandLine commandLine, SimpleJavaParameters javaParameters) throws CantRunException {
    commandLine.withWorkDirectory(javaParameters.getWorkingDirectory());

    commandLine.withEnvironment(javaParameters.getEnv());
    commandLine.withParentEnvironmentType(javaParameters.isPassParentEnvs() ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE);

    ParametersList vmParameters = javaParameters.getVMParametersList();
    boolean dynamicClasspath = javaParameters.isDynamicClasspath();
    boolean dynamicVMOptions = dynamicClasspath && javaParameters.isDynamicVMOptions() && useDynamicVMOptions();
    boolean dynamicParameters = dynamicClasspath && javaParameters.isDynamicParameters() && useDynamicParameters();
    boolean dynamicMainClass = false;

    if (dynamicClasspath) {
      Class commandLineWrapper;
      if (javaParameters.isArgFile()) {
        setArgFileParams(commandLine, javaParameters, vmParameters, dynamicVMOptions, dynamicParameters);
        dynamicMainClass = dynamicParameters;
      }
      else if (!explicitClassPath(vmParameters) && javaParameters.getJarPath() == null && (commandLineWrapper = getCommandLineWrapperClass()) != null) {
        if (javaParameters.isUseClasspathJar()) {
          setClasspathJarParams(commandLine, javaParameters, vmParameters, commandLineWrapper, dynamicVMOptions, dynamicParameters);
        }
        else if (javaParameters.isClasspathFile()) {
          setCommandLineWrapperParams(commandLine, javaParameters, vmParameters, commandLineWrapper, dynamicVMOptions, dynamicParameters);
        }
      }
      else {
        dynamicClasspath = dynamicParameters = false;
      }
    }

    if (!dynamicClasspath) {
      appendParamsEncodingClasspath(javaParameters, commandLine, vmParameters);
    }

    if (!dynamicMainClass) {
      commandLine.addParameters(getMainClassParams(javaParameters));
    }

    if (!dynamicParameters) {
      commandLine.addParameters(javaParameters.getProgramParametersList().getList());
    }
  }

  private static boolean explicitClassPath(ParametersList vmParameters) {
    return vmParameters.hasParameter("-cp") || vmParameters.hasParameter("-classpath") || vmParameters.hasParameter("--class-path");
  }

  private static boolean explicitModulePath(ParametersList vmParameters) {
    return vmParameters.hasParameter("-p") || vmParameters.hasParameter("--module-path");
  }

  private static void setArgFileParams(GeneralCommandLine commandLine,
                                       SimpleJavaParameters javaParameters,
                                       ParametersList vmParameters,
                                       boolean dynamicVMOptions,
                                       boolean dynamicParameters) throws CantRunException {
    try {
      File argFile = FileUtil.createTempFile("idea_arg_file", null);

      try (PrintWriter writer = new PrintWriter(argFile)) {
        if (dynamicVMOptions) {
          for (String param : vmParameters.getList()) {
            writer.print(quoteArg(param));
            writer.print('\n');
          }
        }
        else {
          commandLine.addParameters(vmParameters.getList());
        }

        PathsList classPath = javaParameters.getClassPath();
        if (!classPath.isEmpty() && !explicitClassPath(vmParameters)) {
          writer.print("-classpath\n");
          writer.print(quoteArg(classPath.getPathsString()));
          writer.print('\n');
        }

        PathsList modulePath = javaParameters.getModulePath();
        if (!modulePath.isEmpty() && !explicitModulePath(vmParameters)) {
          writer.print("-p\n");
          writer.print(quoteArg(modulePath.getPathsString()));
          writer.print('\n');
        }

        if (dynamicParameters) {
          for (String parameter : getMainClassParams(javaParameters)) {
            writer.print(quoteArg(parameter));
            writer.print('\n');
          }
          for (String parameter : javaParameters.getProgramParametersList().getList()) {
            writer.print(quoteArg(parameter));
            writer.print('\n');
          }
        }
      }

      commandLine.putUserData(COMMAND_LINE_CONTENT, ContainerUtil.stringMap(argFile.getAbsolutePath(), FileUtil.loadFile(argFile)));

      appendEncoding(javaParameters, commandLine, vmParameters);

      commandLine.addParameter("@" + argFile.getAbsolutePath());

      OSProcessHandler.deleteFileOnTermination(commandLine, argFile);
    }
    catch (IOException e) {
      throwUnableToCreateTempFile(e);
    }
  }

  /* https://docs.oracle.com/javase/9/tools/java.htm, "java Command-Line Argument Files" */
  private static String quoteArg(String arg) {
    if (StringUtil.containsAnyChar(arg, " \"\n\r\t\f") || arg.endsWith("\\") || arg.startsWith("#")) {
      StringBuilder sb = new StringBuilder(arg.length() * 2);
      sb.append('"');

      for (int i = 0; i < arg.length(); i++) {
        char c = arg.charAt(i);
        switch (c) {
          case '\n': sb.append("\\n"); break;
          case '\r': sb.append("\\r"); break;
          case '\t': sb.append("\\t"); break;
          case '\f': sb.append("\\f"); break;
          case '\"': sb.append("\\\""); break;
          case '\\': sb.append("\\\\"); break;
          default:   sb.append(c);
        }
      }

      sb.append('"');
      return sb.toString();
    }

    return arg;
  }

  private static void setCommandLineWrapperParams(GeneralCommandLine commandLine,
                                                  SimpleJavaParameters javaParameters,
                                                  ParametersList vmParameters,
                                                  Class commandLineWrapper,
                                                  boolean dynamicVMOptions,
                                                  boolean dynamicParameters) throws CantRunException {
    try {
      File vmParamsFile = null;
      if (dynamicVMOptions) {
        vmParamsFile = FileUtil.createTempFile("idea_vm_params", null);
        try (PrintWriter writer = new PrintWriter(vmParamsFile)) {
          for (String param : vmParameters.getList()) {
            if (isUserDefinedProperty(param)) {
              writer.println(param);
            }
            else {
              commandLine.addParameter(param);
            }
          }
        }
      }
      else {
        commandLine.addParameters(vmParameters.getList());
      }

      appendEncoding(javaParameters, commandLine, vmParameters);

      File appParamsFile = null;
      if (dynamicParameters) {
        appParamsFile = FileUtil.createTempFile("idea_app_params", null);
        try (PrintWriter writer = new PrintWriter(appParamsFile)) {
          for (String parameter : javaParameters.getProgramParametersList().getList()) {
            writer.println(parameter);
          }
        }
      }

      File classpathFile = FileUtil.createTempFile("idea_classpath", null);
      PathsList classPath = javaParameters.getClassPath();
      try (PrintWriter writer = new PrintWriter(classpathFile)) {
        for (String path : classPath.getPathList()) {
          writer.println(path);
        }
      }

      Map<String, String> map = ContainerUtil.stringMap(classpathFile.getAbsolutePath(), classPath.getPathsString());
      commandLine.putUserData(COMMAND_LINE_CONTENT, map);

      Set<String> classpath = new LinkedHashSet<>();
      classpath.add(PathUtil.getJarPathForClass(commandLineWrapper));
      if (UrlClassLoader.class.getName().equals(vmParameters.getPropertyValue("java.system.class.loader"))) {
        classpath.add(PathUtil.getJarPathForClass(UrlClassLoader.class));
        classpath.add(PathUtil.getJarPathForClass(StringUtilRt.class));
        classpath.add(PathUtil.getJarPathForClass(THashMap.class));
      }
      commandLine.addParameter("-classpath");
      commandLine.addParameter(StringUtil.join(classpath, File.pathSeparator));

      commandLine.addParameter(commandLineWrapper.getName());
      commandLine.addParameter(classpathFile.getAbsolutePath());
      OSProcessHandler.deleteFileOnTermination(commandLine, classpathFile);

      if (vmParamsFile != null) {
        commandLine.addParameter("@vm_params");
        commandLine.addParameter(vmParamsFile.getAbsolutePath());
        map.put(vmParamsFile.getAbsolutePath(), FileUtil.loadFile(vmParamsFile));
        OSProcessHandler.deleteFileOnTermination(commandLine, vmParamsFile);
      }

      if (appParamsFile != null) {
        commandLine.addParameter("@app_params");
        commandLine.addParameter(appParamsFile.getAbsolutePath());
        map.put(appParamsFile.getAbsolutePath(), FileUtil.loadFile(appParamsFile));
        OSProcessHandler.deleteFileOnTermination(commandLine, appParamsFile);
      }
    }
    catch (IOException e) {
      throwUnableToCreateTempFile(e);
    }
  }

  private static void setClasspathJarParams(GeneralCommandLine commandLine,
                                            SimpleJavaParameters javaParameters,
                                            ParametersList vmParameters,
                                            Class commandLineWrapper,
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
            commandLine.addParameter(param);
          }
        }
        manifest.getMainAttributes().putValue("VM-Options", ParametersListUtil.join(properties));
        manifestText += "VM-Options: " + ParametersListUtil.join(properties) + "\n";
      }
      else {
        commandLine.addParameters(vmParameters.getList());
      }

      appendEncoding(javaParameters, commandLine, vmParameters);

      if (dynamicParameters) {
        manifest.getMainAttributes().putValue("Program-Parameters", ParametersListUtil.join(javaParameters.getProgramParametersList().getList()));
        manifestText += "Program-Parameters: " + ParametersListUtil.join(javaParameters.getProgramParametersList().getList()) + "\n";
      }

      boolean notEscape = vmParameters.hasParameter(PROPERTY_DO_NOT_ESCAPE_CLASSPATH_URL);
      PathsList path = javaParameters.getClassPath();
      File classpathJarFile = CommandLineWrapperUtil.createClasspathJarFile(manifest, path.getPathList(), notEscape);

      String jarFilePath = classpathJarFile.getAbsolutePath();
      commandLine.addParameter("-classpath");
      if (dynamicVMOptions || dynamicParameters) {
        commandLine.addParameter(PathUtil.getJarPathForClass(commandLineWrapper) + File.pathSeparator + jarFilePath);
        commandLine.addParameter(commandLineWrapper.getName());
      }
      commandLine.addParameter(jarFilePath);

      commandLine.putUserData(COMMAND_LINE_CONTENT, ContainerUtil.stringMap(jarFilePath, manifestText + "Class-Path: " + path.getPathsString()));

      OSProcessHandler.deleteFileOnTermination(commandLine, classpathJarFile);
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
    throw new CantRunException("Failed to a create temporary file in " + FileUtilRt.getTempDirectory(), cause);
  }

  private static void appendParamsEncodingClasspath(SimpleJavaParameters javaParameters,
                                                    GeneralCommandLine commandLine,
                                                    ParametersList vmParameters) {
    commandLine.addParameters(vmParameters.getList());

    appendEncoding(javaParameters, commandLine, vmParameters);

    PathsList classPath = javaParameters.getClassPath();
    if (!classPath.isEmpty() && !explicitClassPath(vmParameters)) {
      commandLine.addParameter("-classpath");
      commandLine.addParameter(classPath.getPathsString());
    }

    PathsList modulePath = javaParameters.getModulePath();
    if (!modulePath.isEmpty() && !explicitModulePath(vmParameters)) {
      commandLine.addParameter("-p");
      commandLine.addParameter(modulePath.getPathsString());
    }
  }

  private static void appendEncoding(SimpleJavaParameters javaParameters, GeneralCommandLine commandLine, ParametersList parametersList) {
    // Value of file.encoding and charset of GeneralCommandLine should be in sync in order process's input and output be correctly handled.
    String encoding = parametersList.getPropertyValue("file.encoding");
    if (encoding == null) {
      Charset charset = javaParameters.getCharset();
      if (charset == null) charset = EncodingManager.getInstance().getDefaultCharset();
      commandLine.addParameter("-Dfile.encoding=" + charset.name());
      commandLine.withCharset(charset);
    }
    else {
      try {
        Charset charset = Charset.forName(encoding);
        commandLine.withCharset(charset);
      }
      catch (UnsupportedCharsetException | IllegalCharsetNameException ignore) { }
    }
  }

  private static List<String> getMainClassParams(SimpleJavaParameters javaParameters) throws CantRunException {
    String mainClass = javaParameters.getMainClass();
    String moduleName = javaParameters.getModuleName();
    String jarPath = javaParameters.getJarPath();
    if (mainClass != null && moduleName != null) {
      return Arrays.asList("-m", moduleName + '/' + mainClass);
    }
    else if (mainClass != null) {
      return Collections.singletonList(mainClass);
    }
    else if (jarPath != null) {
      return Arrays.asList("-jar", jarPath);
    }
    else {
      throw new CantRunException(ExecutionBundle.message("main.class.is.not.specified.error.message"));
    }
  }

  @Nullable
  private static Class getCommandLineWrapperClass() {
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
  /** @deprecated to be removed in IDEA 2018 */
  @Nullable
  public static String getJarMainAttribute(@NotNull VirtualFile jarRoot, @NotNull Attributes.Name attribute) {
    VirtualFile manifestFile = jarRoot.findFileByRelativePath(JarFile.MANIFEST_NAME);
    if (manifestFile != null) {
      try (InputStream stream = manifestFile.getInputStream()) {
        return new Manifest(stream).getMainAttributes().getValue(attribute);
      }
      catch (IOException e) {
        LOG.debug(e);
      }
    }

    return null;
  }

  /** @deprecated use {@link SimpleJavaParameters#toCommandLine()} (to be removed in IDEA 2018) */
  public static GeneralCommandLine setupJVMCommandLine(final String exePath,
                                                       final SimpleJavaParameters javaParameters,
                                                       final boolean forceDynamicClasspath) {
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
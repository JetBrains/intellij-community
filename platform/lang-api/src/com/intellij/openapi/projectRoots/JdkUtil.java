/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.JarUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.lang.ClassPath;
import com.intellij.util.lang.UrlClassLoader;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * @author max
 */
public class JdkUtil {
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
        homePath + "/jre/lib/vm.jar",
        homePath + "/../Classes/classes.jar",
        homePath + "/lib/jrt-fs.jar",
        homePath + "/jrt-fs.jar");
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
    File binPath = new File(homePath, "bin");
    if (!binPath.exists()) return false;

    FileFilter fileFilter = f -> {
      if (f.isDirectory()) return false;
      String name = FileUtil.getNameWithoutExtension(f);
      return "javac".equals(name) || "javah".equals(name);
    };
    File[] children = binPath.listFiles(fileFilter);

    return children != null && children.length >= 2 &&
           checkForRuntime(homePath.getAbsolutePath());
  }

  public static boolean checkForJre(@NotNull String homePath) {
    return checkForJre(new File(FileUtil.toSystemDependentName(homePath)));
  }

  public static boolean checkForJre(@NotNull File homePath) {
    File binPath = new File(homePath, "bin");
    if (!binPath.exists()) return false;

    FileFilter fileFilter = f -> !f.isDirectory() && "java".equals(FileUtil.getNameWithoutExtension(f));
    File[] children = binPath.listFiles(fileFilter);

    return children != null && children.length >= 1 &&
           checkForRuntime(homePath.getAbsolutePath());
  }

  public static boolean checkForRuntime(@NotNull String homePath) {
    return new File(homePath, "jre/lib/rt.jar").exists() ||          // JDK
           new File(homePath, "lib/rt.jar").exists() ||              // JRE
           new File(homePath, "lib/modules").exists() ||             // Jigsaw JDK/JRE
           new File(homePath, "../Classes/classes.jar").exists() ||  // Apple JDK
           new File(homePath, "jre/lib/vm.jar").exists() ||          // IBM JDK
           new File(homePath, "classes").isDirectory();              // custom build
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
    final ParametersList vmParameters = javaParameters.getVMParametersList();
    commandLine.withEnvironment(javaParameters.getEnv());
    commandLine.withParentEnvironmentType(javaParameters.isPassParentEnvs() ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE);

    final Class commandLineWrapper;
    boolean dynamicParameters = false;
    if (javaParameters.isDynamicClasspath() &&
        !explicitClassPath(vmParameters) &&
        javaParameters.getModulePath().isEmpty() &&
        (commandLineWrapper = getCommandLineWrapperClass()) != null) {
      if (isClassPathJarEnabled(javaParameters, PathUtil.getJarPathForClass(ClassPath.class))) {
        dynamicParameters = setClasspathJarParams(javaParameters, commandLine, vmParameters, commandLineWrapper);
      }
      else {
        dynamicParameters = setCommandLineWrapperParams(javaParameters, commandLine, vmParameters, commandLineWrapper);
      }
    }
    else {
      appendParamsEncodingClasspath(javaParameters, commandLine, vmParameters);
    }

    final String mainClass = javaParameters.getMainClass();
    final String moduleName = javaParameters.getModuleName();
    final String jarPath = javaParameters.getJarPath();
    if (moduleName != null && mainClass != null) {
      commandLine.addParameter("-m");
      commandLine.addParameter(moduleName + '/' + mainClass);
    }
    else if (mainClass != null) {
      commandLine.addParameter(mainClass);
    }
    else if (jarPath != null) {
      commandLine.addParameter("-jar");
      commandLine.addParameter(jarPath);
    }
    else {
      throw new CantRunException(ExecutionBundle.message("main.class.is.not.specified.error.message"));
    }

    if (!dynamicParameters) {
      commandLine.addParameters(javaParameters.getProgramParametersList().getList());
    }

    commandLine.withWorkDirectory(javaParameters.getWorkingDirectory());
  }

  private static boolean explicitClassPath(ParametersList vmParameters) {
    return vmParameters.hasParameter("-cp") || vmParameters.hasParameter("-classpath") || vmParameters.hasParameter("--class-path");
  }

  private static boolean explicitModulePath(ParametersList vmParameters) {
    return vmParameters.hasParameter("-p") || vmParameters.hasParameter("--module-path");
  }

  private static boolean setCommandLineWrapperParams(SimpleJavaParameters javaParameters,
                                                     GeneralCommandLine commandLine,
                                                     ParametersList vmParametersList,
                                                     Class commandLineWrapper) throws CantRunException {
    boolean dynamicVMOptions = javaParameters.isDynamicVMOptions() && useDynamicVMOptions();
    boolean dynamicParameters = javaParameters.isDynamicParameters() && useDynamicParameters();

    File vmParamsFile = null;
    if (dynamicVMOptions) {
      try {
        vmParamsFile = FileUtil.createTempFile("idea_vm_params", null);
        try (PrintWriter writer = new PrintWriter(vmParamsFile)) {
          for (String param : vmParametersList.getList()) {
            if (isUserDefinedProperty(param)) {
              writer.println(param);
            }
            else {
              commandLine.addParameter(param);
            }
          }
        }
      }
      catch (IOException e) {
        throwUnableToCreateTempFile(e);
      }
    }
    else {
      commandLine.addParameters(vmParametersList.getList());
    }

    File appParamsFile = null;
    if (dynamicParameters) {
      try {
        appParamsFile = FileUtil.createTempFile("idea_app_params", null);
        try (PrintWriter writer = new PrintWriter(appParamsFile)) {
          for (String path : javaParameters.getProgramParametersList().getList()) {
            writer.println(path);
          }
        }
      }
      catch (IOException e) {
        throwUnableToCreateTempFile(e);
      }
    }

    File classpathFile = null;
    try {
      classpathFile = FileUtil.createTempFile("idea_classpath", null);
      try (PrintWriter writer = new PrintWriter(classpathFile)) {
        for (String path : javaParameters.getClassPath().getPathList()) {
          writer.println(path);
        }
      }

      String classpath = PathUtil.getJarPathForClass(commandLineWrapper);
      final String utilRtPath = PathUtil.getJarPathForClass(StringUtilRt.class);
      if (!classpath.equals(utilRtPath)) {
        classpath += File.pathSeparator + utilRtPath;
      }
      final Class<UrlClassLoader> ourUrlClassLoader = UrlClassLoader.class;
      if (ourUrlClassLoader.getName().equals(vmParametersList.getPropertyValue("java.system.class.loader"))) {
        classpath += File.pathSeparator + PathUtil.getJarPathForClass(ourUrlClassLoader);
        classpath += File.pathSeparator + PathUtil.getJarPathForClass(THashMap.class);
      }

      commandLine.addParameter("-classpath");
      commandLine.addParameter(classpath);
    }
    catch (IOException e) {
      throwUnableToCreateTempFile(e);
    }

    appendEncoding(javaParameters, commandLine, vmParametersList);
    commandLine.addParameter(commandLineWrapper.getName());
    commandLine.addParameter(classpathFile.getAbsolutePath());

    if (vmParamsFile != null) {
      commandLine.addParameter("@vm_params");
      commandLine.addParameter(vmParamsFile.getAbsolutePath());
    }

    if (appParamsFile != null) {
      commandLine.addParameter("@app_params");
      commandLine.addParameter(appParamsFile.getAbsolutePath());
    }

    Set<File> filesToDelete = getFilesToDeleteUserData(commandLine);
    ContainerUtil.addIfNotNull(filesToDelete, classpathFile);
    ContainerUtil.addIfNotNull(filesToDelete, vmParamsFile);
    ContainerUtil.addIfNotNull(filesToDelete, appParamsFile);

    return dynamicParameters;
  }

  private static boolean setClasspathJarParams(SimpleJavaParameters javaParameters,
                                               GeneralCommandLine commandLine,
                                               ParametersList vmParametersList,
                                               Class commandLineWrapper) throws CantRunException {
    boolean dynamicVMOptions = javaParameters.isDynamicVMOptions() && useDynamicVMOptions();
    boolean dynamicParameters = javaParameters.isDynamicParameters() && useDynamicParameters();

    try {
      final Manifest manifest = new Manifest();
      manifest.getMainAttributes().putValue("Created-By", ApplicationNamesInfo.getInstance().getFullProductName());

      if (dynamicVMOptions) {
        List<String> properties = new ArrayList<>();
        for (String param : vmParametersList.getList()) {
          if (isUserDefinedProperty(param)) {
            properties.add(param);
          }
          else {
            commandLine.addParameter(param);
          }
        }
        manifest.getMainAttributes().putValue("VM-Options", ParametersListUtil.join(properties));
      }
      else {
        commandLine.addParameters(vmParametersList.getList());
      }

      if (dynamicParameters) {
        manifest.getMainAttributes().putValue("Program-Parameters", ParametersListUtil.join(javaParameters.getProgramParametersList().getList()));
      }

      final boolean notEscape = vmParametersList.hasParameter(PROPERTY_DO_NOT_ESCAPE_CLASSPATH_URL);
      final List<String> classPathList = javaParameters.getClassPath().getPathList();

      final File classpathJarFile = CommandLineWrapperUtil.createClasspathJarFile(manifest, classPathList, notEscape);
      getFilesToDeleteUserData(commandLine).add(classpathJarFile);

      final String jarFile = classpathJarFile.getAbsolutePath();
      commandLine.addParameter("-classpath");
      if (dynamicVMOptions || dynamicParameters) {
        commandLine.addParameter(PathUtil.getJarPathForClass(commandLineWrapper) + File.pathSeparator + jarFile);
        appendEncoding(javaParameters, commandLine, vmParametersList);
        commandLine.addParameter(commandLineWrapper.getName());
        commandLine.addParameter(jarFile);
      }
      else {
        commandLine.addParameters(jarFile);
        appendEncoding(javaParameters, commandLine, vmParametersList);
      }
    }
    catch (IOException e) {
      throwUnableToCreateTempFile(e);
    }

    return dynamicParameters;
  }

  @SuppressWarnings("SpellCheckingInspection")
  private static boolean isUserDefinedProperty(String param) {
    return param.startsWith("-D") && !(param.startsWith("-Dsun.") || param.startsWith("-Djava."));
  }

  private static void throwUnableToCreateTempFile(IOException cause) throws CantRunException {
    throw new CantRunException("Failed to a create temporary file in " + FileUtilRt.getTempDirectory(), cause);
  }

  private static boolean isClassPathJarEnabled(SimpleJavaParameters javaParameters, String currentPath) {
    if (javaParameters.isUseClasspathJar() && useClasspathJar()) {
      try {
        final List<URL> urls = new ArrayList<>();
        for (String path : javaParameters.getClassPath().getPathList()) {
          if (!path.equals(currentPath)) {
            try {
              urls.add(new File(path).toURI().toURL());
            }
            catch (MalformedURLException ignore) {}
          }
        }
        final Class<?> aClass = Class.forName("com.intellij.util.lang.ClassPath", false, UrlClassLoader.build().urls(urls).get());
        try {
          aClass.getDeclaredMethod("initLoaders", URL.class, boolean.class, int.class);
        }
        catch (NoSuchMethodException e) {
          return false;
        }
      }
      catch (Throwable ignore) {}
      return true;
    }
    return false;
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

  private static Set<File> getFilesToDeleteUserData(GeneralCommandLine commandLine) {
    Set<File> filesToDelete = commandLine.getUserData(OSProcessHandler.DELETE_FILES_ON_TERMINATION);
    if (filesToDelete == null) {
      filesToDelete = new THashSet<>();
      commandLine.putUserData(OSProcessHandler.DELETE_FILES_ON_TERMINATION, filesToDelete);
    }
    return filesToDelete;
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
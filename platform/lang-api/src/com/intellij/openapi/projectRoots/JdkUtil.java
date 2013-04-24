/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.projectRoots;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.PathUtil;
import com.intellij.util.lang.UrlClassLoader;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class JdkUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.JdkUtil");
  private static final String WRAPPER_CLASS = "com.intellij.rt.execution.CommandLineWrapper";

  private JdkUtil() {
  }

  /**
   * @return the specified attribute of the JDK (examines rt.jar) or null if cannot determine the value
   */
  @Nullable
  public static String getJdkMainAttribute(@NotNull Sdk jdk, Attributes.Name attributeName) {
    final VirtualFile homeDirectory = jdk.getHomeDirectory();
    if (homeDirectory == null) {
      return null;
    }
    VirtualFile rtJar = homeDirectory.findFileByRelativePath("jre/lib/rt.jar");
    if (rtJar == null) {
      rtJar = homeDirectory.findFileByRelativePath("lib/rt.jar");
    }
    if (rtJar == null) {
      rtJar = homeDirectory.findFileByRelativePath("jre/lib/vm.jar"); // for IBM jdk
    }
    if (rtJar == null) {
      rtJar = homeDirectory.findFileByRelativePath("../Classes/classes.jar"); // for mac
    }
    if (rtJar == null) {
      String versionString = jdk.getVersionString();
      if (versionString != null) {
        final int start = versionString.indexOf("\"");
        final int end = versionString.lastIndexOf("\"");
        versionString = start >= 0 && (end > start)? versionString.substring(start + 1, end) : null;
      }
      return versionString;
    }
    VirtualFile rtJarFileContent = JarFileSystem.getInstance().findFileByPath(rtJar.getPath() + JarFileSystem.JAR_SEPARATOR);
    if (rtJarFileContent == null) {
      return null;
    }
    com.intellij.openapi.vfs.JarFile manifestJarFile;
    try {
      manifestJarFile = JarFileSystem.getInstance().getJarFile(rtJarFileContent);
    }
    catch (IOException e) {
      return null;
    }
    if (manifestJarFile == null) {
      return null;
    }
    try {
      com.intellij.openapi.vfs.JarFile.JarEntry entry = manifestJarFile.getEntry(JarFile.MANIFEST_NAME);
      if (entry == null) {
        return null;
      }
      InputStream is = manifestJarFile.getInputStream(entry);
      Manifest manifest = new Manifest(is);
      is.close();
      Attributes attributes = manifest.getMainAttributes();
      return attributes.getValue(attributeName);
    }
    catch (IOException e) {
      // nothing
    }
    return null;
  }

  public static boolean checkForJdk(final File homePath) {
    File binPath = new File(homePath.getAbsolutePath() + File.separator + "bin");
    if (!binPath.exists()) return false;

    FileFilter fileFilter = new FileFilter() {
      @Override
      @SuppressWarnings({"HardCodedStringLiteral"})
      public boolean accept(File f) {
        if (f.isDirectory()) return false;
        return Comparing.strEqual(FileUtil.getNameWithoutExtension(f), "javac") ||
               Comparing.strEqual(FileUtil.getNameWithoutExtension(f), "javah");
      }
    };
    File[] children = binPath.listFiles(fileFilter);

    return children != null && children.length >= 2 &&
           checkForRuntime(homePath.getAbsolutePath());
  }

  public static boolean checkForJre(String homePath) {
    homePath = new File(FileUtil.toSystemDependentName(homePath)).getAbsolutePath();
    File binPath = new File(homePath + File.separator + "bin");
    if (!binPath.exists()) return false;

    FileFilter fileFilter = new FileFilter() {
      @Override
      @SuppressWarnings({"HardCodedStringLiteral"})
      public boolean accept(File f) {
        return !f.isDirectory() && Comparing.strEqual(FileUtil.getNameWithoutExtension(f), "java");
      }
    };
    File[] children = binPath.listFiles(fileFilter);

    return children != null && children.length >= 1 &&
           checkForRuntime(homePath);
  }

  public static boolean checkForRuntime(final String homePath) {
    return new File(new File(new File(homePath, "jre"), "lib"), "rt.jar").exists() ||
           new File(new File(homePath, "lib"), "rt.jar").exists() ||
           new File(new File(new File(homePath, ".."), "Classes"), "classes.jar").exists() ||  // Apple JDK
           new File(new File(new File(homePath, "jre"), "lib"), "vm.jar").exists() ||  // IBM JDK
           new File(homePath, "classes").isDirectory();  // custom build
  }

  public static GeneralCommandLine setupJVMCommandLine(final String exePath,
                                                       final SimpleJavaParameters javaParameters,
                                                       final boolean forceDynamicClasspath) {
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(exePath);

    final ParametersList vmParametersList = javaParameters.getVMParametersList();
    commandLine.getEnvironment().putAll(javaParameters.getEnv());
    commandLine.setPassParentEnvironment(javaParameters.isPassParentEnvs());

    final Class commandLineWrapper;
    if ((commandLineWrapper = getCommandLineWrapperClass()) != null) {
      if (forceDynamicClasspath) {
        File classpathFile = null;
        File vmParamsFile = null;
        if (!vmParametersList.hasParameter("-classpath") && !vmParametersList.hasParameter("-cp")) {
          if (javaParameters.isDynamicVMOptions() && useDynamicVMOptions()) {
            try {
              vmParamsFile = FileUtil.createTempFile("vm_params", null);
              final PrintWriter writer = new PrintWriter(vmParamsFile);
              try {
                for (String param : vmParametersList.getList()) {
                  if (param.startsWith("-D")) {
                    writer.println(param);
                  }
                }
              }
              finally {
                writer.close();
              }
            }
            catch (IOException e) {
              LOG.error(e);
            }
            final List<String> list = vmParametersList.getList();
            for (String param : list) {
              if (!param.trim().startsWith("-D")) {
                commandLine.addParameter(param);
              }
            }
          }
          else {
            commandLine.addParameters(vmParametersList.getList());
          }
          try {
            classpathFile = FileUtil.createTempFile("classpath", null);
            final PrintWriter writer = new PrintWriter(classpathFile);
            try {
              for (String path : javaParameters.getClassPath().getPathList()) {
                writer.println(path);
              }
            }
            finally {
              writer.close();
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
            LOG.error(e);
          }
        }

        appendEncoding(javaParameters, commandLine, vmParametersList);
        if (classpathFile != null) {
          commandLine.addParameter(commandLineWrapper.getName());
          commandLine.addParameter(classpathFile.getAbsolutePath());
        }

        if (vmParamsFile != null) {
          commandLine.addParameter("@vm_params");
          commandLine.addParameter(vmParamsFile.getAbsolutePath());
        }
      }
      else {
        appendParamsEncodingClasspath(javaParameters, commandLine, vmParametersList);
      }
    }
    else {
      appendParamsEncodingClasspath(javaParameters, commandLine, vmParametersList);
    }

    final String mainClass = javaParameters.getMainClass();
    commandLine.addParameter(mainClass);
    commandLine.addParameters(javaParameters.getProgramParametersList().getList());

    commandLine.setWorkDirectory(javaParameters.getWorkingDirectory());

    return commandLine;
  }

  private static void appendParamsEncodingClasspath(SimpleJavaParameters javaParameters,
                                                    GeneralCommandLine commandLine,
                                                    ParametersList parametersList) {
    commandLine.addParameters(parametersList.getList());
    appendEncoding(javaParameters, commandLine, parametersList);
    if (!parametersList.hasParameter("-classpath") && !parametersList.hasParameter("-cp")){
      commandLine.addParameter("-classpath");
      commandLine.addParameter(javaParameters.getClassPath().getPathsString());
    }
  }

  private static void appendEncoding(SimpleJavaParameters javaParameters, GeneralCommandLine commandLine, ParametersList parametersList) {
    // Value of -Dfile.encoding and charset of GeneralCommandLine should be in sync in order process's input and output be correctly handled.
    String encoding = parametersList.getPropertyValue("file.encoding");
    if (encoding == null) {
      Charset charset = javaParameters.getCharset();
      if (charset == null) charset = EncodingManager.getInstance().getDefaultCharset();
      if (charset == null) charset = CharsetToolkit.getDefaultSystemCharset();
      commandLine.addParameter("-Dfile.encoding=" + charset.name());
      commandLine.setCharset(charset);
    }
    else {
      try {
        Charset charset = Charset.forName(encoding);
        commandLine.setCharset(charset);
      }
      catch (UnsupportedCharsetException ignore) {
      }
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
    final String hasDynamicProperty = System.getProperty("idea.dynamic.classpath", "false");
    return Boolean.valueOf(project != null
                           ? PropertiesComponent.getInstance(project).getOrInit("dynamic.classpath", hasDynamicProperty)
                           : hasDynamicProperty).booleanValue();
  }

  public static boolean useDynamicVMOptions() {
    return Boolean.valueOf(PropertiesComponent.getInstance().getOrInit("dynamic.vmoptions", "true")).booleanValue();
  }
}
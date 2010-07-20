/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.PathUtil;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class JdkUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.projectRoots.JdkUtil");
  private static final String WRAPPER_CLASS = "com.intellij.rt.execution.CommandLineWrapper";

  private JdkUtil() {
  }

  /**
   * @return the specified attribute of the JDK (examines rt.jar) or null if cannot determine the value
   */
  @Nullable
  public static String getJdkMainAttribute(Sdk jdk, Attributes.Name attributeName) {
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
      return null;
    }
    VirtualFile rtJarFileContent = JarFileSystem.getInstance().findFileByPath(rtJar.getPath() + JarFileSystem.JAR_SEPARATOR);
    if (rtJarFileContent == null) {
      return null;
    }
    ZipFile manifestJarFile;
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
      ZipEntry entry = manifestJarFile.getEntry(JarFile.MANIFEST_NAME);
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


  public static boolean checkForJdk(File file) {
    file = new File(file.getAbsolutePath() + File.separator + "bin");
    if (!file.exists()) return false;
    FileFilter fileFilter = new FileFilter() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public boolean accept(File f) {
        if (f.isDirectory()) return false;
        return Comparing.strEqual(FileUtil.getNameWithoutExtension(f), "javac") ||
               Comparing.strEqual(FileUtil.getNameWithoutExtension(f), "javah");
      }
    };
    File[] children = file.listFiles(fileFilter);
    return children != null && children.length >= 2;
  }

  public static boolean checkForJre(String file) {
    File ioFile = new File(new File(file.replace('/', File.separatorChar)).getAbsolutePath() + File.separator + "bin");
    if (!ioFile.exists()) return false;
    FileFilter fileFilter = new FileFilter() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public boolean accept(File f) {
        return !f.isDirectory() && Comparing.strEqual(FileUtil.getNameWithoutExtension(f), "java");
      }
    };
    File[] children = ioFile.listFiles(fileFilter);
    return children != null && children.length >= 1;
  }

  public static GeneralCommandLine setupJVMCommandLine(final String exePath, final SimpleJavaParameters javaParameters,
                                                    final boolean forceDynamicClasspath) {
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(exePath);
    ParametersList parametersList = javaParameters.getVMParametersList();
    commandLine.addParameters(parametersList.getList());
    if (!parametersList.hasProperty("file.encoding")) {
      Charset charset = javaParameters.getCharset();
      if (charset == null) charset = EncodingManager.getInstance().getDefaultCharset();
      if (charset == null) charset = CharsetToolkit.getDefaultSystemCharset();
      commandLine.setCharsetAndAddJavaParameter(charset);
    }

    final Map<String, String> env = javaParameters.getEnv();
    if (env != null) {
      commandLine.setEnvParams(env);
      commandLine.setPassParentEnvs(javaParameters.isPassParentEnvs());
    }

    final Class commandLineWrapper;
    if (forceDynamicClasspath && (commandLineWrapper = getCommandLineWrapperClass()) != null) {
      File classpathFile = null;
      if(!parametersList.hasParameter("-classpath") && !parametersList.hasParameter("-cp")){
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
          final Class<UrlClassLoader> ourUrlClassLoader = UrlClassLoader.class;
          if (ourUrlClassLoader.getName().equals(parametersList.getPropertyValue("java.system.class.loader"))) {
            classpath += File.pathSeparator + PathUtil.getJarPathForClass(ourUrlClassLoader);
          }

          commandLine.addParameter("-classpath");
          commandLine.addParameter(classpath);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }

      if (classpathFile != null) {
        commandLine.addParameter(commandLineWrapper.getName());
        commandLine.addParameter(classpathFile.getAbsolutePath());
      }
    }
    else if(!parametersList.hasParameter("-classpath") && !parametersList.hasParameter("-cp")){
      commandLine.addParameter("-classpath");
      commandLine.addParameter(javaParameters.getClassPath().getPathsString());
    }

    final String mainClass = javaParameters.getMainClass();
    commandLine.addParameter(mainClass);
    commandLine.addParameters(javaParameters.getProgramParametersList().getList());
    commandLine.setWorkDirectory(javaParameters.getWorkingDirectory());


    return commandLine;
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
}
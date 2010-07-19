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
package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerIOUtil;
import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.ExternalCompiler;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.projectRoots.impl.MockJdkWrapper;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.rt.compiler.JavacRunner;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

public class JavacCompiler extends ExternalCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.javac.JavacCompiler");
  private final Project myProject;
  private final List<File> myTempFiles = new ArrayList<File>();
  @NonNls private static final String JAVAC_MAIN_CLASS_OLD = "sun.tools.javac.Main";
  @NonNls public static final String JAVAC_MAIN_CLASS = "com.sun.tools.javac.Main";
  private boolean myAnnotationProcessorMode = false;

  public JavacCompiler(Project project) {
    myProject = project;
  }

  public boolean isAnnotationProcessorMode() {
    return myAnnotationProcessorMode;
  }

  /**
   * @param annotationProcessorMode
   * @return previous value
   */
  public boolean setAnnotationProcessorMode(boolean annotationProcessorMode) {
    final boolean oldValue = myAnnotationProcessorMode;
    myAnnotationProcessorMode = annotationProcessorMode;
    return oldValue;
  }

  public boolean checkCompiler(final CompileScope scope) {
    final Module[] modules = scope.getAffectedModules();
    final Set<Sdk> checkedJdks = new HashSet<Sdk>();
    for (final Module module : modules) {
      final Sdk jdk  = ModuleRootManager.getInstance(module).getSdk();
      if (jdk == null) {
        continue;
      }
      if (checkedJdks.contains(jdk)) {
        continue;
      }
      final VirtualFile homeDirectory = jdk.getHomeDirectory();
      if (homeDirectory == null) {
        Messages.showMessageDialog(myProject, CompilerBundle.jdkHomeNotFoundMessage(jdk),
                                   CompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
        return false;
      }
      final SdkType sdkType = jdk.getSdkType();

      if (sdkType instanceof JavaSdkType){
        final String vmExecutablePath = ((JavaSdkType)sdkType).getVMExecutablePath(jdk);
        if (vmExecutablePath == null) {
          Messages.showMessageDialog(myProject,
                                     CompilerBundle.message("javac.error.vm.executable.missing", jdk.getName()),
                                     CompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
          return false;
        }
        final String toolsJarPath = ((JavaSdkType)sdkType).getToolsPath(jdk);
        if (toolsJarPath == null) {
          Messages.showMessageDialog(myProject,
                                     CompilerBundle.message("javac.error.tools.jar.missing", jdk.getName()), CompilerBundle.message("compiler.javac.name"),
                                     Messages.getErrorIcon());
          return false;
        }
        final String versionString = jdk.getVersionString();
        if (versionString == null) {
          Messages.showMessageDialog(myProject, CompilerBundle.message("javac.error.unknown.jdk.version", jdk.getName()),
                                     CompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
          return false;
        }

        if (CompilerUtil.isOfVersion(versionString, "1.0")) {
          Messages.showMessageDialog(myProject, CompilerBundle.message("javac.error.1_0_compilation.not.supported"), CompilerBundle.message("compiler.javac.name"), Messages.getErrorIcon());
          return false;
        }
      }
      checkedJdks.add(jdk);
    }

    return true;
  }

  @NotNull
  @NonNls
  public String getId() { // used for externalization
    return "Javac";
  }

  @NotNull
  public String getPresentableName() {
    return CompilerBundle.message("compiler.javac.name");
  }

  @NotNull
  public Configurable createConfigurable() {
    return new JavacConfigurable(JavacSettings.getInstance(myProject));
  }

  public OutputParser createErrorParser(@NotNull final String outputDir, Process process) {
    return new JavacOutputParser(myProject);
  }

  public OutputParser createOutputParser(@NotNull final String outputDir) {
    return null;
  }

  private static class MyException extends RuntimeException {
    private MyException(Throwable cause) {
      super(cause);
    }
  }

  @NotNull
  public String[] createStartupCommand(final ModuleChunk chunk, final CompileContext context, final String outputPath)
    throws IOException, IllegalArgumentException {

    try {
      return ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
        public String[] compute() {
          try {
            final List<String> commandLine = new ArrayList<String>();
            createStartupCommand(chunk, commandLine, outputPath, JavacSettings.getInstance(myProject));
            return ArrayUtil.toStringArray(commandLine);
          }
          catch (IOException e) {
            throw new MyException(e);
          }
        }
      });
    }
    catch (MyException e) {
      Throwable cause = e.getCause();
      if (cause instanceof IOException) {
        throw (IOException)cause;
      }
      throw e;
    }
  }

  private void createStartupCommand(final ModuleChunk chunk, @NonNls final List<String> commandLine, final String outputPath,
                                    JavacSettings javacSettings) throws IOException {
    final Sdk jdk = getJdkForStartupCommand(chunk);
    final String versionString = jdk.getVersionString();
    if (versionString == null || "".equals(versionString) || !(jdk.getSdkType() instanceof JavaSdkType)) {
      throw new IllegalArgumentException(CompilerBundle.message("javac.error.unknown.jdk.version", jdk.getName()));
    }
    final boolean isVersion1_0 = CompilerUtil.isOfVersion(versionString, "1.0");
    final boolean isVersion1_1 = CompilerUtil.isOfVersion(versionString, "1.1");
    final boolean isVersion1_2 = CompilerUtil.isOfVersion(versionString, "1.2");
    final boolean isVersion1_3 = CompilerUtil.isOfVersion(versionString, "1.3");
    final boolean isVersion1_4 = CompilerUtil.isOfVersion(versionString, "1.4");
    final boolean isVersion1_5 = CompilerUtil.isOfVersion(versionString, "1.5") || CompilerUtil.isOfVersion(versionString, "5.0");
    final boolean isVersion1_5_or_higher = isVersion1_5 || !(isVersion1_0 || isVersion1_1 || isVersion1_2 || isVersion1_3 || isVersion1_4);
    final int versionIndex = isVersion1_0? 0 : isVersion1_1? 1 : isVersion1_2? 2 : isVersion1_3? 3 : isVersion1_4? 4 : isVersion1_5? 5 : 6;

    JavaSdkType sdkType = (JavaSdkType)jdk.getSdkType();

    final String toolsJarPath = sdkType.getToolsPath(jdk);
    if (toolsJarPath == null) {
      throw new IllegalArgumentException(CompilerBundle.message("javac.error.tools.jar.missing", jdk.getName()));
    }

    final String vmExePath = sdkType.getVMExecutablePath(jdk);

    commandLine.add(vmExePath);

    if (isVersion1_1 || isVersion1_0) {
      commandLine.add("-mx" + javacSettings.MAXIMUM_HEAP_SIZE + "m");
    }
    else {
      commandLine.add("-Xmx" + javacSettings.MAXIMUM_HEAP_SIZE + "m");
    }

    final List<String> additionalOptions =
      addAdditionalSettings(commandLine, javacSettings, myAnnotationProcessorMode, versionIndex, myProject);

    CompilerUtil.addLocaleOptions(commandLine, false);

    commandLine.add("-classpath");

    if (isVersion1_0) {
      commandLine.add(sdkType.getToolsPath(jdk)); //  do not use JavacRunner for jdk 1.0
    }
    else {
      commandLine.add(sdkType.getToolsPath(jdk) + File.pathSeparator + JavaSdkUtil.getIdeaRtJarPath());
      commandLine.add(JavacRunner.class.getName());
      commandLine.add("\"" + versionString + "\"");
    }

    if (isVersion1_2 || isVersion1_1 || isVersion1_0) {
      commandLine.add(JAVAC_MAIN_CLASS_OLD);
    }
    else {
      commandLine.add(JAVAC_MAIN_CLASS);
    }

    addCommandLineOptions(chunk, commandLine, outputPath, jdk, isVersion1_0, isVersion1_1, myTempFiles, true, true, myAnnotationProcessorMode);

    commandLine.addAll(additionalOptions);

    final List<VirtualFile> files = chunk.getFilesToCompile();

    if (isVersion1_0) {
      for (VirtualFile file : files) {
        String path = file.getPath();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Adding path for compilation " + path);
        }
        commandLine.add(CompilerUtil.quotePath(path));
      }
    }
    else {
      File sourcesFile = FileUtil.createTempFile("javac", ".tmp");
      sourcesFile.deleteOnExit();
      myTempFiles.add(sourcesFile);
      final PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(sourcesFile)));
      try {
        for (final VirtualFile file : files) {
          // Important: should use "/" slashes!
          // but not for JDK 1.5 - see SCR 36673
          final String path = isVersion1_5_or_higher ? file.getPath().replace('/', File.separatorChar) : file.getPath();
          if (LOG.isDebugEnabled()) {
            LOG.debug("Adding path for compilation " + path);
          }
          writer.println(isVersion1_1 ? path : CompilerUtil.quotePath(path));
        }
      }
      finally {
        writer.close();
      }
      commandLine.add("@" + sourcesFile.getAbsolutePath());
    }
  }

  public static List<String> addAdditionalSettings(List<String> commandLine, JavacSettings javacSettings, boolean isAnnotationProcessing,
                                                   int versionIndex, Project project) {
    final List<String> additionalOptions = new ArrayList<String>();
    StringTokenizer tokenizer = new StringTokenizer(javacSettings.getOptionsString(project), " ");
    if (versionIndex < 6) {
      isAnnotationProcessing = false; // makes no sense for these versions
    }
    if (isAnnotationProcessing) {
      final CompilerConfiguration config = CompilerConfiguration.getInstance(project);
      additionalOptions.add("-Xprefer:source");
      additionalOptions.add("-implicit:none");
      additionalOptions.add("-proc:only");
      if (!config.isObtainProcessorsFromClasspath()) {
        final String processorPath = config.getProcessorPath();
        if (processorPath.length() > 0) {
          additionalOptions.add("-processorpath");
          additionalOptions.add(FileUtil.toSystemDependentName(processorPath));
        }
      }
      for (Map.Entry<String, String> entry : config.getAnnotationProcessorsMap().entrySet()) {
        additionalOptions.add("-processor");
        additionalOptions.add(entry.getKey());
        final String options = entry.getValue();
        if (options.length() > 0) {
          StringTokenizer optionsTokenizer = new StringTokenizer(options, " ", false);
          while (optionsTokenizer.hasMoreTokens()) {
            final String token = optionsTokenizer.nextToken();
            if (token.startsWith("-A")) {
              additionalOptions.add(token.substring("-A".length()));
            }
            else {
              additionalOptions.add("-A" + token);
            }
          }
        }
      }
    }
    else {
      if (versionIndex > 5) {
        // Unless explicitly specified by user, disable annotation processing by default for 'java compilation' mode
        // This is needed to suppress unwanted side-effects from auto-discovered processors from compilation classpath
        additionalOptions.add("-proc:none");
      }
    }

    while (tokenizer.hasMoreTokens()) {
      @NonNls String token = tokenizer.nextToken();
      if (versionIndex == 0) {
        if ("-deprecation".equals(token)) {
          continue; // not supported for this version
        }
      }
      if (versionIndex <= 4) {
        if ("-Xlint".equals(token)) {
          continue; // not supported in these versions
        }
      }
      if (token.startsWith("-proc:")) {
        continue;
      }
      if (isAnnotationProcessing) {
        if (token.startsWith("-implicit:")) {
          continue;
        }
      }
      if (token.startsWith("-J-")) {
        commandLine.add(token.substring("-J".length()));
      }
      else {
        additionalOptions.add(token);
      }
    }

    return additionalOptions;
  }

  public static void addCommandLineOptions(ModuleChunk chunk, @NonNls List<String> commandLine, String outputPath, Sdk jdk,
                                           boolean version1_0,
                                           boolean version1_1,
                                           List<File> tempFiles, boolean addSourcePath, boolean useTempFile,
                                           boolean isAnnotationProcessingMode) throws IOException {

    LanguageLevel languageLevel = chunk.getLanguageLevel();
    CompilerUtil.addSourceCommandLineSwitch(jdk, languageLevel, commandLine);

    commandLine.add("-verbose");

    final String cp = chunk.getCompilationClasspath();
    final String bootCp = chunk.getCompilationBootClasspath();

    final String classPath;
    if (version1_0 || version1_1) {
      classPath = bootCp + File.pathSeparator + cp;
    }
    else {
      classPath = cp;
      commandLine.add("-bootclasspath");
      addClassPathValue(jdk, false, commandLine, bootCp, "javac_bootcp", tempFiles, useTempFile);
    }

    commandLine.add("-classpath");
    addClassPathValue(jdk, version1_0, commandLine, classPath, "javac_cp", tempFiles, useTempFile);

    if (!version1_1 && !version1_0 && addSourcePath) {
      commandLine.add("-sourcepath");
      // this way we tell the compiler that the sourcepath is "empty". However, javac thinks that sourcepath is 'new File("")'
      // this may cause problems if we have java code in IDEA working directory
      commandLine.add("\"\"");
    }

    if (isAnnotationProcessingMode) {
      commandLine.add("-s");
      commandLine.add(outputPath.replace('/', File.separatorChar));
      final String moduleOutputPath = CompilerPaths.getModuleOutputPath(chunk.getModules()[0], false);
      if (moduleOutputPath != null) {
        commandLine.add("-d");
        commandLine.add(moduleOutputPath.replace('/', File.separatorChar));
      }
    }
    else {
      commandLine.add("-d");
      commandLine.add(outputPath.replace('/', File.separatorChar));
    }
  }

  private static void addClassPathValue(final Sdk jdk, final boolean isVersion1_0, final List<String> commandLine, final String cpString, @NonNls final String tempFileName,
                                        List<File> tempFiles,
                                        boolean useTempFile) throws IOException {
    if (!useTempFile) {
      commandLine.add(cpString);
      return;
    }
    // must include output path to classpath, otherwise javac will compile all dependent files no matter were they compiled before or not
    if (isVersion1_0) {
      commandLine.add(((JavaSdkType)jdk.getSdkType()).getToolsPath(jdk) + File.pathSeparator + cpString);
    }
    else {
      File cpFile = FileUtil.createTempFile(tempFileName, ".tmp");
      cpFile.deleteOnExit();
      tempFiles.add(cpFile);
      final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(cpFile)));
      try {
        CompilerIOUtil.writeString(cpString, out);
      }
      finally {
        out.close();
      }
      commandLine.add("@" + cpFile.getAbsolutePath());
    }
  }

  private Sdk getJdkForStartupCommand(final ModuleChunk chunk) {
    final Sdk jdk = chunk.getJdk();
    if (ApplicationManager.getApplication().isUnitTestMode() && JavacSettings.getInstance(myProject).isTestsUseExternalCompiler()) {
      final String jdkHomePath = CompilerConfigurationImpl.getTestsExternalCompilerHome();
      if (jdkHomePath == null) {
        throw new IllegalArgumentException("[TEST-MODE] Cannot determine home directory for JDK to use javac from");
      }
      // when running under Mock JDK use VM executable from the JDK on which the tests run
      return new MockJdkWrapper(jdkHomePath, jdk);
    }
    return jdk;
  }

  public void compileFinished() {
    FileUtil.asyncDelete(myTempFiles);
    myTempFiles.clear();
  }
}

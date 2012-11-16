/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.ExternalCompiler;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.compiler.EclipseCompilerOptions;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
                                        
public class EclipseCompiler extends ExternalCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompiler");

  private final Project myProject;
  private final List<File> myTempFiles = new ArrayList<File>();
  private static final String COMPILER_CLASS_NAME = "org.eclipse.jdt.core.compiler.batch.BatchCompiler";
  @NonNls private static final String PATH_TO_COMPILER_JAR = findJarPah();

  private static String findJarPah() {
    try {
      final Class<?> aClass = Class.forName(COMPILER_CLASS_NAME);
      final String path = PathManager.getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
      if (path != null) {
        return path;
      }
    }
    catch (ClassNotFoundException ignored) {
    }

    File dir = new File(PathManager.getLibPath());
    File[] jars = dir.listFiles(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return name.startsWith("ecj-") && name.endsWith(".jar");
      }
    });
    return jars.length == 0 ? dir + "/ecj-*.jar" : jars[0].getPath();
  }

  public EclipseCompiler(Project project) {
    myProject = project;
  }

  public static boolean isInitialized() {
    File file = new File(PATH_TO_COMPILER_JAR);
    return file.exists();
  }

  public boolean checkCompiler(final CompileScope scope) {
    if (!isInitialized()) {
      Messages.showMessageDialog(
        myProject,
        CompilerBundle.message("eclipse.compiler.error.jar.not.found", PATH_TO_COMPILER_JAR),
        CompilerBundle.message("compiler.eclipse.name"),
        Messages.getErrorIcon()
      );
      return false;
    }
    return true;
  }

  @NonNls
  public static String getCompilerClass() {
    return "org.eclipse.jdt.internal.compiler.batch.Main";
  }

  @NotNull
  public String getId() { // used for externalization
    return JavaCompilers.ECLIPSE_ID;
  }

  @NotNull
  public String getPresentableName() {
    return CompilerBundle.message("compiler.eclipse.name");
  }

  @NotNull
  public Configurable createConfigurable() {
    return new EclipseCompilerConfigurable(EclipseCompilerConfiguration.getOptions(myProject, EclipseCompilerConfiguration.class));
  }

  public OutputParser createErrorParser(@NotNull final String outputDir, Process process) {
    return new EclipseCompilerErrorParser();
  }

  @Nullable
  public OutputParser createOutputParser(@NotNull final String outputDir) {
    return new EclipseCompilerOutputParser(outputDir);
  }

  @NotNull
  public String[] createStartupCommand(final ModuleChunk chunk, final CompileContext context, final String outputPath)
    throws IOException {

    final ArrayList<String> commandLine = new ArrayList<String>();
    final IOException[] ex = {null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          createStartupCommand(chunk, commandLine, outputPath, true);
        }
        catch (IOException e) {
          ex[0] = e;
        }
      }
    });
    if (ex[0] != null) {
      throw ex[0];
    }
    return ArrayUtil.toStringArray(commandLine);
  }

  private void createStartupCommand(final ModuleChunk chunk,
                                    @NonNls final ArrayList<String> commandLine,
                                    final String outputPath,
                                    final boolean useTempFile) throws IOException {
    final EclipseCompilerOptions options = EclipseCompilerConfiguration.getOptions(myProject, EclipseCompilerConfiguration.class);

    final Sdk projectJdk = JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    final String vmExePath = ((JavaSdkType)projectJdk.getSdkType()).getVMExecutablePath(projectJdk);
    commandLine.add(vmExePath);
    commandLine.add("-Xmx" + options.MAXIMUM_HEAP_SIZE + "m");

    CompilerUtil.addLocaleOptions(commandLine, false);

    commandLine.add("-classpath");
    commandLine.add(PATH_TO_COMPILER_JAR);
    commandLine.add(getCompilerClass());

    addCommandLineOptions(commandLine, chunk, outputPath, options, useTempFile, true);
  }

  public void addCommandLineOptions(@NotNull @NonNls final List<String> commandLine,
                                    @NotNull final ModuleChunk chunk,
                                    @NotNull final String outputPath,
                                    @NotNull final EclipseCompilerOptions options,
                                    final boolean useTempFile,
                                    boolean quoteBootClasspath) throws IOException {
    final Sdk jdk = chunk.getJdk();
    CompilerUtil.addSourceCommandLineSwitch(jdk, chunk.getLanguageLevel(), commandLine);
    CompilerUtil.addTargetCommandLineSwitch(chunk, commandLine);

    final String bootCp = chunk.getCompilationBootClasspath();

    final String classPath = chunk.getCompilationClasspath();

    if (!StringUtil.isEmpty(bootCp)) {
      commandLine.add("-bootclasspath");
      // important: need to quote boot classpath if path to jdk contain spaces
      commandLine.add(quoteBootClasspath ? CompilerUtil.quotePath(bootCp) : bootCp);
    }

    if (!StringUtil.isEmpty(classPath)) {
      commandLine.add("-classpath");
      commandLine.add(classPath);
    }

    commandLine.add("-d");
    commandLine.add(outputPath.replace('/', File.separatorChar));

    commandLine.add("-verbose");
    StringTokenizer tokenizer = new StringTokenizer(new EclipseSettingsBuilder(options).getOptionsString(chunk), " ");
    while (tokenizer.hasMoreTokens()) {
      commandLine.add(tokenizer.nextToken());
    }

    final List<VirtualFile> files = chunk.getFilesToCompile();

    if (useTempFile) {
      File sourcesFile = FileUtil.createTempFile("javac", ".tmp");
      sourcesFile.deleteOnExit();
      myTempFiles.add(sourcesFile);
      final PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(sourcesFile)));
      try {
        for (final VirtualFile file : files) {
          // Important: should use "/" slashes!
          // but not for JDK 1.5 - see SCR 36673
          final String path = file.getPath().replace('/', File.separatorChar);
          if (LOG.isDebugEnabled()) {
            LOG.debug("Adding path for compilation " + path);
          }
          writer.println(CompilerUtil.quotePath(path));
        }
      }
      finally {
        writer.close();
      }
      commandLine.add("@" + sourcesFile.getAbsolutePath());
    }
    else {
      for (VirtualFile file : files) {
        commandLine.add(file.getPath());
      }
    }
  }

  public void compileFinished() {
    FileUtil.asyncDelete(myTempFiles);
    myTempFiles.clear();
  }
}

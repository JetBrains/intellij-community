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
package com.intellij.compiler.impl.javaCompiler.jikes;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.ExternalCompiler;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.compiler.options.JavaCompilersTab;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

public class JikesCompiler extends ExternalCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.JikesHandler");
  private final Project myProject;
  private File myTempFile;

  public JikesCompiler(Project project) {
    myProject = project;
  }

  public boolean checkCompiler(final CompileScope scope) {
    String compilerPath = getCompilerPath();
    if (StringUtil.isEmpty(compilerPath)) {
      Messages.showMessageDialog(
        myProject,
        CompilerBundle.message("jikes.error.path.to.compiler.unspecified"), CompilerBundle.message("compiler.jikes.name"),
        Messages.getErrorIcon()
      );
      openConfigurationDialog();
      compilerPath = getCompilerPath(); // update path
      if (StringUtil.isEmpty(compilerPath)) {
        return false;
      }
    }

    File file = new File(compilerPath);
    if (!file.exists()) {
      Messages.showMessageDialog(
        myProject,
        CompilerBundle.message("jikes.error.path.to.compiler.missing", compilerPath),
        CompilerBundle.message("compiler.jikes.name"),
        Messages.getErrorIcon()
      );
      openConfigurationDialog();
      compilerPath = getCompilerPath(); // update path
      if (StringUtil.isEmpty(compilerPath)) {
        return false;
      }
      if (!new File(compilerPath).exists()) {
        return false;
      }
    }

    return true;
  }

  private void openConfigurationDialog() {
    final CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    final Collection<BackendCompiler> compilers = configuration.getRegisteredJavaCompilers();
    final BackendCompiler defaultCompiler = configuration.getDefaultCompiler();
    final JavaCompilersTab compilersTab = new JavaCompilersTab(myProject, compilers, defaultCompiler);

    ShowSettingsUtil.getInstance().editConfigurable(myProject, compilersTab);
  }

  private String getCompilerPath() {
    return JikesConfiguration.getSettings(myProject).JIKES_PATH.replace('/', File.separatorChar);
  }

  @NotNull
  @NonNls
  public String getId() // used for externalization
  {
    return "Jikes";
  }

  @NotNull
  public String getPresentableName() {
    return CompilerBundle.message("compiler.jikes.name");
  }

  @NotNull
  public Configurable createConfigurable() {
    return new JikesConfigurable(JikesConfiguration.getSettings(myProject));
  }

  public OutputParser createErrorParser(@NotNull final String outputDir, Process process) {
    return new JikesOutputParser(myProject);
  }

  @Nullable
  public OutputParser createOutputParser(@NotNull final String outputDir) {
    return null;
  }

  @NotNull
  public String[] createStartupCommand(final ModuleChunk chunk, final CompileContext context, final String outputPath)
    throws IOException {

    final ArrayList<String> commandLine = new ArrayList<String>();
    final IOException[] ex = {null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          _createStartupCommand(chunk, commandLine, outputPath);
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

  private void _createStartupCommand(final ModuleChunk chunk, final ArrayList<String> commandLine, @NotNull final String outputPath) throws IOException {

    myTempFile = File.createTempFile("jikes", ".tmp");
    myTempFile.deleteOnExit();

    final List<VirtualFile> files = chunk.getFilesToCompile();
    PrintWriter writer = new PrintWriter(new FileWriter(myTempFile));
    try {
      for (VirtualFile file : files) {
        writer.println(file.getPath());
      }
    }
    finally {
      writer.close();
    }

    String compilerPath = getCompilerPath();
    LOG.assertTrue(compilerPath != null, "No path to compiler configured");

    commandLine.add(compilerPath);

    //noinspection HardCodedStringLiteral
    commandLine.add("-verbose");
    //noinspection HardCodedStringLiteral
    commandLine.add("-classpath");

    // must include output path to classpath, otherwise javac will compile all dependent files no matter were they compiled before or not
    commandLine.add(chunk.getCompilationBootClasspath() + File.pathSeparator + chunk.getCompilationClasspath());

    setupSourceVersion(chunk, commandLine);
    //noinspection HardCodedStringLiteral
    commandLine.add("-sourcepath");
    String sourcePath = chunk.getSourcePath();
    if (sourcePath.length() > 0) {
      commandLine.add(sourcePath);
    }
    else {
      commandLine.add("\"\"");
    }

    //noinspection HardCodedStringLiteral
    commandLine.add("-d");
    commandLine.add(outputPath.replace('/', File.separatorChar));

    JikesSettings jikesSettings = JikesConfiguration.getSettings(myProject);
    StringTokenizer tokenizer = new StringTokenizer(jikesSettings.getOptionsString(), " ");
    while (tokenizer.hasMoreTokens()) {
      commandLine.add(tokenizer.nextToken());
    }
    commandLine.add("@" + myTempFile.getAbsolutePath());
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void setupSourceVersion(final ModuleChunk chunk, final ArrayList<String> commandLine) {
    final Sdk jdk = chunk.getJdk();
    final String versionString = jdk.getVersionString();

    final LanguageLevel applicableLanguageLevel = CompilerUtil.getApplicableLanguageLevel(versionString, chunk.getLanguageLevel());
    if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_5)) {
      commandLine.add("-source");
      commandLine.add("1.4"); // -source 1.5 not supported yet by jikes, so use the highest possible version
    }
    else if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_4)) {
      commandLine.add("-source");
      commandLine.add("1.4");
    }
  }


  public void compileFinished() {
    if (myTempFile != null) {
      FileUtil.delete(myTempFile);
      myTempFile = null;
    }
  }

}


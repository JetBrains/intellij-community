package com.intellij.compiler.impl.javaCompiler.jikes;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.ExternalCompiler;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.compiler.options.CompilerConfigurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class JikesCompiler extends ExternalCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.JikesHandler");
  private Project myProject;
  private File myTempFile;

  public JikesCompiler(Project project) {
    myProject = project;
  }

  public boolean checkCompiler(final CompileScope scope) {
    String compilerPath = getCompilerPath();
    if (compilerPath == null) {
      Messages.showMessageDialog(
        myProject,
        CompilerBundle.message("jikes.error.path.to.compiler.unspecified"), CompilerBundle.message("compiler.jikes.name"),
        Messages.getErrorIcon()
      );
      openConfigurationDialog();
      compilerPath = getCompilerPath(); // update path
      if (compilerPath == null) {
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
      if (compilerPath == null) {
        return false;
      }
      if (!new File(compilerPath).exists()) {
        return false;
      }
    }

    return true;
  }

  private void openConfigurationDialog() {
    ShowSettingsUtil.getInstance().editConfigurable(myProject, CompilerConfigurable.getInstance(myProject));
  }

  private String getCompilerPath() {
    return JikesSettings.getInstance(myProject).JIKES_PATH.replace('/', File.separatorChar);
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
    return new JikesConfigurable(JikesSettings.getInstance(myProject));
  }

  public OutputParser createErrorParser(final String outputDir) {
    return new JikesOutputParser(myProject);
  }

  @Nullable
  public OutputParser createOutputParser(final String outputDir) {
    return null;
  }

  @NotNull
  public String[] createStartupCommand(final ModuleChunk chunk, final CompileContext context, final String outputPath)
    throws IOException {

    final ArrayList<String> commandLine = new ArrayList<String>();
    final IOException[] ex = new IOException[]{null};
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
    return commandLine.toArray(new String[commandLine.size()]);
  }

  private void _createStartupCommand(final ModuleChunk chunk, final ArrayList<String> commandLine, final String outputPath) throws IOException {

    myTempFile = File.createTempFile("jikes", ".tmp");
    myTempFile.deleteOnExit();

    final VirtualFile[] files = chunk.getFilesToCompile();
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
    LOG.assertTrue(outputPath != null);
    commandLine.add(outputPath.replace('/', File.separatorChar));

    JikesSettings jikesSettings = JikesSettings.getInstance(myProject);
    StringTokenizer tokenizer = new StringTokenizer(jikesSettings.getOptionsString(), " ");
    while (tokenizer.hasMoreTokens()) {
      commandLine.add(tokenizer.nextToken());
    }
    commandLine.add("@" + myTempFile.getAbsolutePath());
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void setupSourceVersion(final ModuleChunk chunk, final ArrayList<String> commandLine) {
    final ProjectJdk jdk = chunk.getJdk();
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
      myTempFile.delete();
      myTempFile = null;
    }
  }

}


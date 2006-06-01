package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.ExternalCompiler;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class EclipseCompiler extends ExternalCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompiler");

  private Project myProject;
  private final List<File> myTempFiles = new ArrayList<File>();
  @NonNls public static final String PATH_TO_COMPILER_JAR = PathManager.getLibPath() + "/org.eclipse.jdt.core.jar";

  public EclipseCompiler(Project project) {
    myProject = project;
  }

  public boolean isInitialized() {
    File file = new File(PATH_TO_COMPILER_JAR);
    return file.exists();
  }

  public boolean checkCompiler() {
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
  public String getId() // used for externalization
  {
    return "Eclipse";
  }

  @NotNull
  public String getPresentableName() {
    return CompilerBundle.message("compiler.eclipse.name");
  }

  @NotNull
  public Configurable createConfigurable() {
    return new EclipseCompilerConfigurable(EclipseCompilerSettings.getInstance(myProject));
  }

  public OutputParser createErrorParser(final String outputDir) {
    return new EclipseCompilerErrorParser();
  }

  @Nullable
  public OutputParser createOutputParser(final String outputDir) {
    return new EclipseCompilerOutputParser(outputDir);
  }

  @NotNull
  public String[] createStartupCommand(final ModuleChunk chunk, final CompileContext context, final String outputPath)
    throws IOException {

    final ArrayList<String> commandLine = new ArrayList<String>();
    final IOException[] ex = new IOException[]{null};
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
    return commandLine.toArray(new String[commandLine.size()]);
  }

  private void createStartupCommand(final ModuleChunk chunk,
                                    @NonNls final ArrayList<String> commandLine,
                                    final String outputPath,
                                    final boolean useTempFile) throws IOException {
    EclipseCompilerSettings compilerSettings = EclipseCompilerSettings.getInstance(myProject);

    final String vmExePath = ProjectJdkTable.getInstance().getInternalJdk().getVMExecutablePath();
    commandLine.add(vmExePath);
    commandLine.add("-Xmx" + compilerSettings.MAXIMUM_HEAP_SIZE + "m");

    CompilerUtil.addLocaleOptions(commandLine, false);

    commandLine.add("-classpath");
    commandLine.add(PATH_TO_COMPILER_JAR);
    commandLine.add(getCompilerClass());

    addCommandLineOptions(commandLine, chunk, outputPath, compilerSettings, useTempFile, true);
  }

  public void addCommandLineOptions(@NonNls final ArrayList<String> commandLine,
                                    final ModuleChunk chunk,
                                    final String outputPath,
                                    final EclipseCompilerSettings compilerSettings,
                                    final boolean useTempFile,
                                    boolean quoteBootClasspath) throws IOException {
    final ProjectJdk jdk = chunk.getJdk();
    CompilerUtil.addSourceCommandLineSwitch(jdk, chunk.getLanguageLevel(), commandLine);

    final String bootCp = chunk.getCompilationBootClasspath();

    final String classPath = chunk.getCompilationClasspath();

    commandLine.add("-bootclasspath");
    // important: need to quote boot classpath if path to jdk contain spaces
    commandLine.add(quoteBootClasspath ? CompilerUtil.quotePath(bootCp) : bootCp);

    commandLine.add("-classpath");
    commandLine.add(classPath);

    commandLine.add("-d");
    commandLine.add(outputPath.replace('/', File.separatorChar));

    commandLine.add("-verbose");
    StringTokenizer tokenizer = new StringTokenizer(compilerSettings.getOptionsString(), " ");
    while (tokenizer.hasMoreTokens()) {
      commandLine.add(tokenizer.nextToken());
    }

    final VirtualFile[] files = chunk.getFilesToCompile();

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
    for (final File myTempFile : myTempFiles) {
      FileUtil.delete(myTempFile);
    }
    myTempFiles.clear();
  }

}

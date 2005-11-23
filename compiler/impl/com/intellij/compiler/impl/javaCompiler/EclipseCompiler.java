package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.EclipseCompilerOutputParser;
import com.intellij.compiler.JavacSettings;
import com.intellij.compiler.OutputParser;
import com.intellij.compiler.EclipseCompilerErrorParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.options.CompilerConfigurable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

class EclipseCompiler extends ExternalCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.EclipseCompiler");

  private Project myProject;
  private final List<File> myTempFiles = new ArrayList<File>();

  public EclipseCompiler(Project project) {
    myProject = project;
  }

  public boolean checkCompiler() {
    return true;
  }

  private void openConfigurationDialog() {
    ShowSettingsUtil.getInstance().editConfigurable(myProject, CompilerConfigurable.getInstance(myProject));
  }

  @NonNls private static String getCompilerClass() {
    return "org.eclipse.jdt.internal.compiler.batch.Main";
  }

  public OutputParser createErrorParser(final String outputDir) {
    return new EclipseCompilerErrorParser(myProject);
  }

  @Nullable
  public OutputParser createOutputParser(final String outputDir) {
    return new EclipseCompilerOutputParser(myProject, outputDir);
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

  private void _createStartupCommand(final ModuleChunk chunk, @NonNls final ArrayList<String> commandLine, final String outputPath) throws IOException {
    final ProjectJdk jdk = chunk.getJdk();
    final String versionString = jdk.getVersionString();
    if (versionString == null || "".equals(versionString)) {
      throw new IllegalArgumentException(CompilerBundle.message("javac.error.unknown.jdk.version", jdk.getName()));
    }
    final boolean isVersion1_0 = isOfVersion(versionString, "1.0");
    final boolean isVersion1_1 = isOfVersion(versionString, "1.1");
    final boolean isVersion1_2 = isOfVersion(versionString, "1.2");
    final boolean isVersion1_3 = isOfVersion(versionString, "1.3");
    final boolean isVersion1_4 = isOfVersion(versionString, "1.4");
    final boolean isVersion1_5 = isOfVersion(versionString, "1.5") || isOfVersion(versionString, "5.0");

    final JavacSettings javacSettings = JavacSettings.getInstance(myProject);

    final String vmExePath = jdk.getVMExecutablePath();

    commandLine.add(vmExePath);
    if (isVersion1_1 || isVersion1_0) {
      commandLine.add("-mx" + javacSettings.MAXIMUM_HEAP_SIZE + "m");
    }
    else {
      commandLine.add("-Xmx" + javacSettings.MAXIMUM_HEAP_SIZE + "m");
    }

    CompilerUtil.addLocaleOptions(commandLine, false);

    commandLine.add("-classpath");
    commandLine.add("C:\\java\\eclipse\\plugins\\org.eclipse.jdt.core_3.1.0.jar");
    commandLine.add(getCompilerClass());

    final LanguageLevel applicableLanguageLevel = getApplicableLanguageLevel(versionString);
    if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_5)) {
      commandLine.add("-source");
      commandLine.add("1.5");
    }
    else if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_4)) {
      commandLine.add("-source");
      commandLine.add("1.4");
    }
    else if (applicableLanguageLevel.equals(LanguageLevel.JDK_1_3)) {
      if (isVersion1_4 || isVersion1_5) {
        commandLine.add("-source");
        commandLine.add("1.3");
      }
    }

    final String cp = chunk.getCompilationClasspath();
    final String bootCp = chunk.getCompilationBootClasspath();

    final String classPath;
    if (isVersion1_0 || isVersion1_1) {
      classPath = bootCp + File.pathSeparator + cp;
    }
    else {
      classPath = cp;

      commandLine.add("-bootclasspath");
      // important: need to quote boot classpath if path to jdk contain spaces
      commandLine.add(CompilerUtil.quotePath(bootCp));
    }

    commandLine.add("-classpath");
    commandLine.add(classPath);

    commandLine.add("-d");
    commandLine.add(outputPath.replace('/', File.separatorChar));

    commandLine.add("-verbose");
    commandLine.add("-nowarn");

    final VirtualFile[] files = chunk.getFilesToCompile();

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
      //noinspection HardCodedStringLiteral
      File sourcesFile = FileUtil.createTempFile("javac", ".tmp");
      sourcesFile.deleteOnExit();
      myTempFiles.add(sourcesFile);
      final PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(sourcesFile)));
      try {
        for (final VirtualFile file : files) {
          // Important: should use "/" slashes!
          // but not for JDK 1.5 - see SCR 36673
          final String path = isVersion1_5 ? file.getPath().replace('/', File.separatorChar) : file.getPath();
          if (LOG.isDebugEnabled()) {
            LOG.debug("Adding path for compilation " + path);
          }
          writer.println(isVersion1_1 ? path : CompilerUtil.quotePath(path));
        }
      }
      finally {
        writer.close();
      }
      commandLine.add("@" + sourcesFile.getAbsolutePath()+"");
    }

    //commandLine.add("1>&2");

  }

  private LanguageLevel getApplicableLanguageLevel(String versionString) {
    LanguageLevel languageLevel = ProjectRootManagerEx.getInstanceEx(myProject).getLanguageLevel();

    if (LanguageLevel.JDK_1_5.equals(languageLevel)) {
      if (!(EclipseCompiler.isOfVersion(versionString, "1.5") || EclipseCompiler.isOfVersion(versionString, "5.0"))) {
        languageLevel = LanguageLevel.JDK_1_4;
      }
    }

    if (LanguageLevel.JDK_1_4.equals(languageLevel)) {
      if (!EclipseCompiler.isOfVersion(versionString, "1.4") && !EclipseCompiler.isOfVersion(versionString, "1.5") && !EclipseCompiler.isOfVersion(versionString, "5.0")) {
        languageLevel = LanguageLevel.JDK_1_3;
      }
    }

    return languageLevel;
  }

  public void processTerminated() {
    if (myTempFiles.size() > 0) {
      for (final File myTempFile : myTempFiles) {
        FileUtil.delete(myTempFile);
      }
      myTempFiles.clear();
    }
  }

  private static boolean isOfVersion(String versionString, String checkedVersion) {
    return versionString.contains(checkedVersion);
  }
}

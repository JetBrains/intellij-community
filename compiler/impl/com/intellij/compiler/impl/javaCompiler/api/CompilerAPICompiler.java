package com.intellij.compiler.impl.javaCompiler.api;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.DependencyProcessor;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.compiler.impl.javaCompiler.javac.JavacCompiler;
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfigurable;
import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;


public class CompilerAPICompiler implements BackendCompiler {
  private final Project myProject;
  private int myExitCode;
  private static final Set<FileType> COMPILABLE_TYPES = Collections.<FileType>singleton(StdFileTypes.JAVA);
  private final CompAPIDriver myCompAPIDriver = new CompAPIDriver();

  public CompilerAPICompiler(Project project) {
    myProject = project;
  }

  public DependencyProcessor getDependencyProcessor() {
    return null;
  }

  public boolean checkCompiler(final CompileScope scope) {
    final Module[] modules = scope.getAffectedModules();
    final Set<Sdk> checkedJdks = new HashSet<Sdk>();
    for (final Module module : modules) {
      final Sdk jdk  = ModuleRootManager.getInstance(module).getSdk();
      if (jdk == null) {
        continue;
      }
      checkedJdks.add(jdk);
    }
    Sdk projectJdk = ProjectRootManager.getInstance(myProject).getProjectJdk();
    if (projectJdk != null) checkedJdks.add(projectJdk);

    for (Sdk sdk : checkedJdks) {
      if (!CompilerUtil.isOfVersion(sdk.getVersionString(), "1.6")) {
        Messages.showErrorDialog(myProject, "Compiler API supports JDK of version 6 only: "+sdk.getVersionString(), "Incompatible JDK");
        return false;
      }
    }
    return true;
  }

  @NotNull
  @NonNls
  // used for externalization
  public String getId() {
    return "compAPI";
  }

  @NotNull
  public String getPresentableName() {
    return "Javac in-process (Java6 only)";
  }

  @NotNull
  public Configurable createConfigurable() {
    return new JavacConfigurable(JavacSettings.getInstance(myProject));
  }

  @NotNull
  public Set<FileType> getCompilableFileTypes() {
    return COMPILABLE_TYPES;
  }

  @Nullable
  public OutputParser createErrorParser(@NotNull final String outputDir) {
    return new OutputParser() {
      public boolean processMessageLine(Callback callback) {
        return myCompAPIDriver.processAll(callback);
      }
    };
  }

  @Nullable
  public OutputParser createOutputParser(@NotNull final String outputDir) {
    return null;
  }

  public void compileFinished() {
    myCompAPIDriver.finish();
  }

  @NotNull
  public Process launchProcess(@NotNull final ModuleChunk chunk, @NotNull final String outputDir, @NotNull final CompileContext compileContext) throws IOException {
    final IOException[] ex = {null};
    @NonNls final List<String> commandLine = ApplicationManager.getApplication().runReadAction(new Computable<List<String>>() {
      public List<String> compute() {
        try {
          List<String> commandLine = new ArrayList<String>();
          JavacSettings javacSettings = JavacSettings.getInstance(myProject);
          final List<String> additionalOptions =
            JavacCompiler.addAdditionalSettings(commandLine, javacSettings, false, false, false, false, false);

          JavacCompiler.addCommandLineOptions(chunk, commandLine, outputDir, chunk.getJdk(), false,false, null, false, false);
          commandLine.addAll(additionalOptions);
          return commandLine;
        }
        catch (IOException e) {
          ex[0] = e;
        }
        return null;
      }
    });
    if (ex[0] != null) {
      throw ex[0];
    }

    return new Process() {
      public OutputStream getOutputStream() {
        throw new UnsupportedOperationException();
      }

      public InputStream getInputStream() {
        return null;
      }

      public InputStream getErrorStream() {
        return null;
      }

      public void destroy() {
      }

      public int waitFor() {
        try {
          //commandLine.remove("-verbose");
          compile(commandLine, chunk, outputDir);
          myExitCode = 0;
          return myExitCode;
        }
        catch (Exception e) {
          compileContext.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
          myExitCode = -1;
          return -1;
        }
      }

      public int exitValue() {
        return myExitCode;
      }
    };
  }

  private void compile(List<String> commandLine, ModuleChunk chunk, String outputDir) {
    VirtualFile[] filesToCompile = chunk.getFilesToCompile();
    List<File> paths = new ArrayList<File>(filesToCompile.length);
    for (VirtualFile file : filesToCompile) {
      paths.add(new File(file.getPresentableUrl()));
    }
    myCompAPIDriver.compile(commandLine, paths, outputDir);
  }
}
package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;


public class EclipseEmbeddedCompiler implements BackendCompiler {
  private Project myProject;
  private final EclipseCompiler myEclipseExternalCompiler;
  private int myExitCode;
  private IEclipseCompilerDriver myEclipseCompilerDriver;

  public EclipseEmbeddedCompiler(Project project) {
    myProject = project;
    myEclipseExternalCompiler = new EclipseCompiler(project);
    createCompileDriver();
  }

  public boolean checkCompiler() {
    return myEclipseCompilerDriver != null && myEclipseExternalCompiler.checkCompiler();
  }

  @NotNull
  @NonNls
  public String getId() // used for externalization
  {
    return "EclipseEmbedded";
  }

  @NotNull
  public String getPresentableName() {
    return CompilerBundle.message("compiler.eclipse.embedded.name");
  }

  @NotNull
  public Configurable createConfigurable() {
    return new EclipseCompilerConfigurable(EclipseEmbeddedCompilerSettings.getInstance(myProject));
  }

  @Nullable
  public OutputParser createErrorParser(final String outputDir) {
    return new OutputParser() {
      public boolean processMessageLine(Callback callback) {
        return myEclipseCompilerDriver.processMessageLine(callback, outputDir, myProject);
      }
    };
  }

  @Nullable
  public OutputParser createOutputParser(final String outputDir) {
    return null;
  }

  public void compileFinished() {
  }


  @NotNull
  public Process launchProcess(final ModuleChunk chunk, final String outputDir, final CompileContext compileContext) throws IOException {
    @NonNls final ArrayList<String> commandLine = new ArrayList<String>();
    final IOException[] ex = new IOException[]{null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          myEclipseExternalCompiler.addCommandLineOptions(commandLine, chunk, outputDir, EclipseEmbeddedCompilerSettings.getInstance(myProject), false, false);
        }
        catch (IOException e) {
          ex[0] = e;
        }
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
          commandLine.remove("-verbose");
          String[] finalCmds = commandLine.toArray(new String[commandLine.size()]);
          myEclipseCompilerDriver.parseCommandLineAndCompile(finalCmds,compileContext);
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

  private void createCompileDriver() {
    myEclipseCompilerDriver = new EclipseCompilerDriver();
  }
}

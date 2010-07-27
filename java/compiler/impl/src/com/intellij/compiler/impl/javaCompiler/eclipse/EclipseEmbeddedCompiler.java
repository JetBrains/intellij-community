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
package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;


public class EclipseEmbeddedCompiler implements BackendCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.eclipse.EclipseEmbeddedCompiler");

  private final Project myProject;
  private final EclipseCompiler myEclipseExternalCompiler;
  private int myExitCode;
  private final EclipseCompilerDriver myEclipseCompilerDriver;
  private static final Set<FileType> COMPILABLE_TYPES = Collections.<FileType>singleton(StdFileTypes.JAVA);

  public EclipseEmbeddedCompiler(Project project) {
    myProject = project;
    myEclipseExternalCompiler = new EclipseCompiler(project);
    myEclipseCompilerDriver = new EclipseCompilerDriver();
  }

  public boolean checkCompiler(final CompileScope scope) {
    return myEclipseCompilerDriver != null && myEclipseExternalCompiler.checkCompiler(scope);
  }

  @NotNull
  @NonNls
  public String getId() {
    // used for externalization
    return "EclipseEmbedded";
  }

  @NotNull
  public String getPresentableName() {
    return CompilerBundle.message("compiler.eclipse.embedded.name");
  }

  @NotNull
  public Configurable createConfigurable() {
    return new EclipseCompilerConfigurable(EclipseEmbeddedCompilerConfiguration.getSettings(myProject, EclipseEmbeddedCompilerConfiguration.class));
  }

  @NotNull
  public Set<FileType> getCompilableFileTypes() {
    return COMPILABLE_TYPES;
  }

  @Nullable
  public OutputParser createErrorParser(@NotNull final String outputDir, Process process) {
    return new OutputParser() {
      public boolean processMessageLine(Callback callback) {
        return myEclipseCompilerDriver.processMessageLine(callback, outputDir, myProject);
      }
    };
  }

  @Nullable
  public OutputParser createOutputParser(@NotNull final String outputDir) {
    return null;
  }

  public void compileFinished() {
  }


  @NotNull
  public Process launchProcess(@NotNull final ModuleChunk chunk, @NotNull final String outputDir, @NotNull final CompileContext compileContext) throws IOException {
    @NonNls final ArrayList<String> commandLine = new ArrayList<String>();
    final IOException[] ex = {null};
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          JavacSettings settings = EclipseEmbeddedCompilerConfiguration.getSettings(myProject, EclipseEmbeddedCompilerConfiguration.class);
          myEclipseExternalCompiler.addCommandLineOptions(commandLine, chunk, outputDir, settings, false, false);
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
          String[] finalCmds = ArrayUtil.toStringArray(commandLine);
          myEclipseCompilerDriver.parseCommandLineAndCompile(finalCmds,compileContext);
          myExitCode = 0;
          return myExitCode;
        }
        catch (Exception e) {
          compileContext.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
          LOG.info(e);
          myExitCode = -1;
          return -1;
        }
      }

      public int exitValue() {
        return myExitCode;
      }
    };
  }
}

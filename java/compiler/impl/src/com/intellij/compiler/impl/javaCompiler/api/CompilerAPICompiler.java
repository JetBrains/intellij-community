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
package com.intellij.compiler.impl.javaCompiler.api;

import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.ModuleChunk;
import com.intellij.compiler.impl.javaCompiler.javac.JavacCompiler;
import com.intellij.compiler.impl.javaCompiler.javac.JavacConfigurable;
import com.intellij.compiler.impl.javaCompiler.javac.JavacSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;


public class CompilerAPICompiler implements BackendCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.api.CompilerAPICompiler");
  private final Project myProject;
  private static final Set<FileType> COMPILABLE_TYPES = Collections.<FileType>singleton(StdFileTypes.JAVA);

  public CompilerAPICompiler(Project project) {
    myProject = project;
  }

  public boolean checkCompiler(final CompileScope scope) {
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
    return "Javac in-process (Java6+ only)";
  }

  @NotNull
  public Configurable createConfigurable() {
    return new JavacConfigurable(CompilerAPIConfiguration.getSettings(myProject, CompilerAPIConfiguration.class));
  }

  @NotNull
  public Set<FileType> getCompilableFileTypes() {
    return COMPILABLE_TYPES;
  }

  @Nullable
  public OutputParser createErrorParser(@NotNull final String outputDir, final Process process) {
    return new OutputParser() {
      public boolean processMessageLine(Callback callback) {
        ((MyProcess)process).myCompAPIDriver.processAll(callback);
        return false;
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
    final IOException[] ex = {null};
    @NonNls final List<String> commandLine = ApplicationManager.getApplication().runReadAction(new Computable<List<String>>() {
      public List<String> compute() {
        try {
          List<String> commandLine = new ArrayList<String>();
          JavacSettings javacSettings = CompilerAPIConfiguration.getSettings(myProject, CompilerAPIConfiguration.class);
          final List<String> additionalOptions =
            JavacCompiler.addAdditionalSettings(commandLine, javacSettings, false, 6, myProject);

          JavacCompiler.addCommandLineOptions(chunk, commandLine, outputDir, chunk.getJdk(), false,false, null, false, false, false);
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
    return new MyProcess(commandLine, chunk, outputDir, compileContext);
  }

  private static void compile(List<String> commandLine, ModuleChunk chunk, String outputDir, CompAPIDriver myCompAPIDriver) {
    List<VirtualFile> filesToCompile = chunk.getFilesToCompile();
    List<File> paths = new ArrayList<File>(filesToCompile.size());
    for (VirtualFile file : filesToCompile) {
      paths.add(new File(file.getPresentableUrl()));
    }
    myCompAPIDriver.compile(commandLine, paths, outputDir);
  }

  private static class MyProcess extends Process {
    private final List<String> myCommandLine;
    private final ModuleChunk myChunk;
    private final String myOutputDir;
    private final CompileContext myCompileContext;
    private final CompAPIDriver myCompAPIDriver = new CompAPIDriver();

    private MyProcess(List<String> commandLine, ModuleChunk chunk, String outputDir, CompileContext compileContext) {
      myCommandLine = commandLine;
      myChunk = chunk;
      myOutputDir = outputDir;
      myCompileContext = compileContext;
    }

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
      myCompAPIDriver.finish();
    }

    private int myExitCode;
    public int waitFor() {
      try {
        myCommandLine.remove("-verbose");
        compile(myCommandLine, myChunk, myOutputDir, myCompAPIDriver);
        myExitCode = 0;
        return myExitCode;
      }
      catch (Exception e) {
        myCompileContext.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
        LOG.info(e);
        myExitCode = -1;
        return -1;
      }
    }

    public int exitValue() {
      return myExitCode;
    }

    @Override
    public String toString() {
      return myChunk.toString();
    }
  }
}

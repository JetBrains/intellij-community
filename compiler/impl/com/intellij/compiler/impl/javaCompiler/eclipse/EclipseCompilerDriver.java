/**
 * @author Alexey
 */
/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.internal.compiler.*;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.batch.FileSystem;
import org.eclipse.jdt.internal.compiler.batch.Main;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;    

public class EclipseCompilerDriver implements IEclipseCompilerDriver {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompilerDriver");

  private String[] sourceFilePaths;
  private Map compilerOptions;
  private final BlockingQueue<CompilationResult> myCompilationResults = new LinkedBlockingQueue<CompilationResult>();
  private FileSystem classPath;

  private void parseCommandLine(String[] args) throws InvalidInputException {
    StringWriter err = new StringWriter();
    Main driver = new Main(null, new PrintWriter(err), false);
    driver.configure(args);
    StringBuffer buffer = err.getBuffer();
    if (buffer.length() != 0) {
      throw new InvalidInputException(buffer.toString());
    }
    sourceFilePaths = driver.filenames;
    compilerOptions = driver.options;
    classPath = driver.getLibraryAccess();
  }

  private CompilationUnit[] getCompilationUnits() {
    int fileCount = sourceFilePaths.length;
    CompilationUnit[] units = new CompilationUnit[fileCount];
    final String defaultEncoding = null;

    for (int i = 0; i < fileCount; i++) {
      units[i] = new MyCompilationUnit(sourceFilePaths[i], defaultEncoding);
    }
    return units;
  }

  private ICompilerRequestor getBatchRequestor(final CompileContext compileContext) {
    return new ICompilerRequestor() {
      public void acceptResult(CompilationResult compilationResult) {
        ProgressIndicator progress = compileContext.getProgressIndicator();
        if (progress != null) {
          progress.checkCanceled();
        }
        myCompilationResults.offer(compilationResult);
      }
    };
  }

  private static final CompilationResult END_OF_STREAM = new CompilationResult(new char[0], 0, 0, 0);

  private INameEnvironment getEnvironment() {
    return classPath;
  }

  private static IProblemFactory getProblemFactory() {
    return new DefaultProblemFactory(Locale.getDefault());
  }

  private static IErrorHandlingPolicy getHandlingPolicy() {
    return new IErrorHandlingPolicy() {
      public boolean proceedOnErrors() {
        return false; // stop if there are some errors
      }

      public boolean stopOnFirstError() {
        return false;
      }
    };
  }

  private Map getCompilerOptions() {
    return compilerOptions;
  }


  private void compile(final CompileContext compileContext) {
    final INameEnvironment environment = getEnvironment();

    Compiler compiler =
      new Compiler(
        environment,
        getHandlingPolicy(),
        getCompilerOptions(),
        getBatchRequestor(compileContext),
        getProblemFactory()){
        protected void handleInternalException(Throwable internalException, CompilationUnitDeclaration unit, CompilationResult result) {
          if (internalException instanceof ProcessCanceledException) throw (ProcessCanceledException)internalException;
          super.handleInternalException(internalException, unit, result);
        }
      };
    compiler.parseThreshold = 2500;
    try {
      compiler.compile(getCompilationUnits());
    }
    catch (ProcessCanceledException e) {
      //compileContext.addMessage(CompilerMessageCategory.ERROR, "Canceled",null,-1,-1);
    }
    finally {
      myCompilationResults.offer(END_OF_STREAM);
      environment.cleanup();
    }
  }

  public boolean processMessageLine(final OutputParser.Callback callback, final String outputDir, Project project) {
    CompilationResult result;
    try {
      result = myCompilationResults.take();
    }
    catch (InterruptedException e) {
      LOG.error(e);
      return true;
    }
    if (result == END_OF_STREAM) {
      return false;
    }

    String file = String.valueOf(result.getFileName());
    callback.setProgressText(CompilerBundle.message("eclipse.compiler.parsing", file));
    callback.fileProcessed(file);

    ClassFile[] classFiles = result.getClassFiles();
    for (ClassFile classFile : classFiles) {
      String filePath = String.valueOf(classFile.fileName());
      String relativePath = FileUtil.toSystemDependentName(filePath + ".class");
      String path = FileUtil.toSystemDependentName(outputDir) + File.separatorChar + relativePath;
                                                    
      try {
        ClassFile.writeToDisk(
                true,
                outputDir,
                relativePath,
                classFile.getBytes());
      }
      catch (IOException e) {
        LOG.error(e);
      }

      callback.fileGenerated(path);
    }
    IProblem[] problems = result.getProblems();
    if (problems != null) {
      for (IProblem problem : problems) {
        CompilerMessageCategory category = problem.isError() ? CompilerMessageCategory.ERROR
                                           : problem.isWarning() ? CompilerMessageCategory.WARNING :
                                             CompilerMessageCategory.INFORMATION;
        String filePath = String.valueOf(problem.getOriginatingFileName());
        String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(filePath));
        int lineNumber = problem.getSourceLineNumber();
        int sourceStart = problem.getSourceStart();
        int column = getColumn(url, lineNumber, sourceStart, project);
        callback.message(category, problem.getMessage(), url, lineNumber, column);
      }
    }
    return true;
  }

  private static int getColumn(final String url, final int lineNumber, final int sourceStart, final Project project) {
    if (sourceStart == 0) return 0;
    return ApplicationManager.getApplication().runReadAction(new Computable<Integer>() {
      public Integer compute() {
        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
        if (file == null) return 0;
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document == null) return 0;
        int lineStartOffset = document.getLineStartOffset(lineNumber - 1);

        String lineSeparator = FileDocumentManager.getInstance().getLineSeparator(file, project);
        int offsetInVirtualFile = sourceStart - (lineNumber - 1) * (lineSeparator.length() - 1);
        return offsetInVirtualFile - lineStartOffset + 1;
      }
    }).intValue();
  }

  public void parseCommandLineAndCompile(final String[] finalCmds, final CompileContext compileContext) throws Exception {
    parseCommandLine(finalCmds);

    compile(compileContext);
  }

  private static class MyCompilationUnit extends CompilationUnit {
    private final String myDefaultEncoding;

    public MyCompilationUnit(final String sourceFilePath, final String defaultEncoding) {
      super(null, sourceFilePath, defaultEncoding);
      myDefaultEncoding = defaultEncoding;
    }

    public char[] getContents() {
      final String fileName = String.valueOf(getFileName());
      try {
        return FileUtil.loadFileText(new File(fileName), myDefaultEncoding);
      }
      catch (IOException e) {
        LOG.error(e);
      }
      return null;
    }
  }
}

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

/*
 * @author: Eugene Zhuravlev
 * Date: Jan 24, 2003
 * Time: 4:25:47 PM
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.CompilerException;
import com.intellij.compiler.OutputParser;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.TranslatingCompiler;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Chunk;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class AnnotationProcessorsRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.BackendCompilerWrapper");

  private final BackendCompiler myCompiler;
  private final Set<VirtualFile> mySuccesfullyCompiledJavaFiles; // VirtualFile

  private final CompileContextEx myCompileContext;
  private final List<VirtualFile> myFilesToCompile;
  private final TranslatingCompiler.OutputSink mySink;
  private final Chunk<Module> myChunk;
  private final Project myProject;
  private final Set<VirtualFile> myFilesToRecompile;
  private final ProjectFileIndex myProjectFileIndex;
  private long myCompilationDuration = 0L;


  public AnnotationProcessorsRunner(Chunk<Module> chunk, @NotNull final Project project,
                                @NotNull List<VirtualFile> filesToCompile,
                                @NotNull CompileContextEx compileContext,
                                @NotNull BackendCompiler compiler, TranslatingCompiler.OutputSink sink) {
    myChunk = chunk;
    myProject = project;
    myCompiler = compiler;
    myCompileContext = compileContext;
    myFilesToCompile = filesToCompile;
    myFilesToRecompile = new HashSet<VirtualFile>(filesToCompile);
    mySink = sink;
    myProjectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    mySuccesfullyCompiledJavaFiles = new HashSet<VirtualFile>(filesToCompile.size());
  }

  public void compile() throws CompilerException, CacheCorruptedException {
    try {
      if (!myFilesToCompile.isEmpty()) {
        final Map<Module, List<VirtualFile>> moduleToFilesMap = buildModuleToFilesMap(myFilesToCompile);
        myProcessedFilesCount = 0;
        try {
          // todo: need special ModuleChunk to be able to filter sources
          final ModuleChunk chunk = new ModuleChunk(myCompileContext, myChunk, moduleToFilesMap);
          // TODO: do we really need this for annot. processors?
          //runTransformingCompilers(chunk);

          setPresentableNameFor(chunk);

          // assuming output dir pointing to source-generated output dir
          final List<OutputDir> outs = getOutputDirsToCompileTo(chunk);

          for (final OutputDir outputDir : outs) {
            // todo: proper filtering
            chunk.setSourcesFilter(outputDir.getKind());
            doCompile(chunk, outputDir.getPath());
          }
        }
        catch (IOException e) {
          throw new CompilerException(e.getMessage(), e);
        }
      }
    }
    catch (SecurityException e) {
      throw new CompilerException(CompilerBundle.message("error.compiler.process.not.started", e.getMessage()), e);
    }
    catch (IllegalArgumentException e) {
      throw new CompilerException(e.getMessage(), e);
    }
    finally {
      CompilerUtil.logDuration(myCompiler.getId() + " running", myCompilationDuration);
    }

    // do not update caches if cancelled because there is a chance that they will be incomplete

    myFilesToRecompile.removeAll(mySuccesfullyCompiledJavaFiles);
    if (myFilesToRecompile.size() > 0) {
      mySink.add(null, Collections.<TranslatingCompiler.OutputItem>emptyList(), myFilesToRecompile.toArray(new VirtualFile[myFilesToRecompile.size()]));
    }
  }

  private Map<Module, List<VirtualFile>> buildModuleToFilesMap(final List<VirtualFile> filesToCompile) {
    if (myChunk.getNodes().size() == 1) {
      return Collections.singletonMap(myChunk.getNodes().iterator().next(), Collections.unmodifiableList(filesToCompile));
    }
    return CompilerUtil.buildModuleToFilesMap(myCompileContext, filesToCompile);
  }


  private void setPresentableNameFor(final ModuleChunk chunk) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final Module[] modules = chunk.getModules();
        StringBuilder moduleName = new StringBuilder(Math.min(128, modules.length * 8));
        for (int idx = 0; idx < modules.length; idx++) {
          final Module module = modules[idx];
          if (idx > 0) {
            moduleName.append(", ");
          }
          moduleName.append(module.getName());
          if (moduleName.length() > 128 && idx + 1 < modules.length /*name is already too long and seems to grow longer*/) {
            moduleName.append("...");
            break;
          }
        }
        myModuleName = moduleName.toString();
      }
    });
  }

  private List<OutputDir> getOutputDirsToCompileTo(ModuleChunk chunk) throws IOException {
    // todo
    return Collections.emptyList();
  }

  private final Object lock = new Object();

  private class SynchedCompilerParsing extends CompilerParsingThread {

    private SynchedCompilerParsing(Process process, final CompileContext context, OutputParser outputParser, boolean readErrorStream,
                                   boolean trimLines) {
      super(process, outputParser, readErrorStream, trimLines,context);
    }

    public void setProgressText(String text) {
      synchronized (lock) {
        super.setProgressText(text);
      }
    }

    public void message(CompilerMessageCategory category, String message, String url, int lineNum, int columnNum) {
      synchronized (lock) {
        super.message(category, message, url, lineNum, columnNum);
      }
    }

    public void fileProcessed(String path) {
      synchronized (lock) {
        sourceFileProcessed();
      }
    }
  }

  private void doCompile(@NotNull final ModuleChunk chunk, @NotNull String outputDir) throws IOException {
    myCompileContext.getProgressIndicator().checkCanceled();

    if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return chunk.getFilesToCompile().isEmpty() ? Boolean.TRUE : Boolean.FALSE;
      }
    }).booleanValue()) {
      return; // should not invoke javac with empty sources list
    }

    ModuleType moduleType = chunk.getModules()[0].getModuleType();
    if (!(chunk.getJdk().getSdkType() instanceof JavaSdkType) &&
        !(moduleType instanceof JavaModuleType || moduleType.createModuleBuilder() instanceof JavaModuleBuilder)) {
      // TODO
      // don't try to compile non-java type module
      return;
    }

    int exitValue = 0;
    try {
      Process process = myCompiler.launchProcess(chunk, outputDir, myCompileContext);
      final long compilationStart = System.currentTimeMillis();

      OutputParser errorParser = myCompiler.createErrorParser(outputDir, process);
      CompilerParsingThread errorParsingThread = errorParser == null
                                                 ? null
                                                 : new SynchedCompilerParsing(process, myCompileContext, errorParser, true, errorParser.isTrimLines());
      Future<?> errorParsingThreadFuture = null;
      if (errorParsingThread != null) {
        errorParsingThreadFuture = ApplicationManager.getApplication().executeOnPooledThread(errorParsingThread);
      }

      OutputParser outputParser = myCompiler.createOutputParser(outputDir);
      CompilerParsingThread outputParsingThread = outputParser == null
                                                  ? null
                                                  : new SynchedCompilerParsing(process, myCompileContext, outputParser, false, outputParser.isTrimLines());
      Future<?> outputParsingThreadFuture = null;
      if (outputParsingThread != null) {
        outputParsingThreadFuture = ApplicationManager.getApplication().executeOnPooledThread(outputParsingThread);
      }

      try {
        exitValue = process.waitFor();
      }
      catch (InterruptedException e) {
        process.destroy();
        exitValue = process.exitValue();
      }
      finally {
        myCompilationDuration += (System.currentTimeMillis() - compilationStart);
        if (errorParsingThread != null) {
          errorParsingThread.setProcessTerminated(true);
        }
        if (outputParsingThread != null) {
          outputParsingThread.setProcessTerminated(true);
        }
        joinThread(errorParsingThreadFuture);
        joinThread(outputParsingThreadFuture);

        registerParsingException(outputParsingThread);
        registerParsingException(errorParsingThread);
        assert outputParsingThread == null || !outputParsingThread.processing;
        assert errorParsingThread == null || !errorParsingThread.processing;
      }
    }
    finally {
      compileFinished(exitValue, chunk, outputDir);
      myModuleName = null;
    }
  }

  private static void joinThread(final Future<?> threadFuture) {
    if (threadFuture != null) {
      try {
        threadFuture.get();
      }
      catch (InterruptedException ignored) {
      }
      catch(ExecutionException ignored) {
      }
    }
  }

  private void registerParsingException(final CompilerParsingThread outputParsingThread) {
    Throwable error = outputParsingThread == null ? null : outputParsingThread.getError();
    if (error != null) {
      String message = error.getMessage();
      if (error instanceof CacheCorruptedException) {
        myCompileContext.requestRebuildNextTime(message);
      }
      else {
        myCompileContext.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
      }
    }
  }

  private void compileFinished(int exitValue, final ModuleChunk chunk, final String outputDir) {
    if (exitValue != 0 && !myCompileContext.getProgressIndicator().isCanceled() &&
        myCompileContext.getMessageCount(CompilerMessageCategory.ERROR) == 0) {
      myCompileContext.addMessage(CompilerMessageCategory.ERROR, CompilerBundle.message("error.compiler.internal.error", exitValue), null, -1, -1);
    }

    myCompiler.compileFinished();

    try {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          final String outputDirPath = outputDir.replace(File.separatorChar, '/');
          for (final Module module : chunk.getModules()) {
            for (final VirtualFile root : chunk.getSourceRoots(module)) {
              final String packagePrefix = myProjectFileIndex.getPackageNameByDirectory(root);
              if (LOG.isDebugEnabled()) {
                LOG.debug("Building output items for " + root.getPresentableUrl() + "; output dir = " + outputDirPath + "; packagePrefix = \"" + packagePrefix + "\"");
              }
            }
          }
        }
      });
    }
    finally {
      // todo: refresh dirs her
    }
  }

  private volatile int myProcessedFilesCount = 0;
  private volatile int myClassesCount = 0;
  private volatile String myModuleName = null;

  private void sourceFileProcessed() {
    myProcessedFilesCount++;
    updateStatistics();
  }

  private void updateStatistics() {
    final String msg;
    String moduleName = myModuleName;
    if (moduleName != null) {
      msg = CompilerBundle.message("statistics.files.classes.module", myProcessedFilesCount, myClassesCount, moduleName);
    }
    else {
      msg = CompilerBundle.message("statistics.files.classes", myProcessedFilesCount, myClassesCount);
    }
    myCompileContext.getProgressIndicator().setText2(msg);
  }

  /*
  private void runTransformingCompilers(final ModuleChunk chunk) {
    final JavaSourceTransformingCompiler[] transformers =
      CompilerManager.getInstance(myProject).getCompilers(JavaSourceTransformingCompiler.class);
    if (transformers.length == 0) {
      return;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Running transforming compilers...");
    }
    final Module[] modules = chunk.getModules();
    for (final JavaSourceTransformingCompiler transformer : transformers) {
      final Map<VirtualFile, VirtualFile> originalToCopyFileMap = new HashMap<VirtualFile, VirtualFile>();
      final Application application = ApplicationManager.getApplication();
      application.invokeAndWait(new Runnable() {
        public void run() {
          for (final Module module : modules) {
            List<VirtualFile> filesToCompile = chunk.getFilesToCompile(module);
            for (final VirtualFile file : filesToCompile) {
              if (transformer.isTransformable(file)) {
                application.runWriteAction(new Runnable() {
                  public void run() {
                    try {
                      VirtualFile fileCopy = createFileCopy(getTempDir(module), file);
                      originalToCopyFileMap.put(file, fileCopy);
                    }
                    catch (IOException e) {
                      // skip it
                    }
                  }
                });
              }
            }
          }
        }
      }, myCompileContext.getProgressIndicator().getModalityState());

      // do actual transform
      for (final Module module : modules) {
        final List<VirtualFile> filesToCompile = chunk.getFilesToCompile(module);
        for (int j = 0; j < filesToCompile.size(); j++) {
          final VirtualFile file = filesToCompile.get(j);
          VirtualFile fileCopy = originalToCopyFileMap.get(file);
          if (fileCopy != null) {
            final boolean ok = transformer.transform(myCompileContext, fileCopy, file);
            if (ok) {
              chunk.substituteWithTransformedVersion(module, j, fileCopy);
            }
          }
        }
      }
    }
  }

  private VirtualFile createFileCopy(VirtualFile tempDir, final VirtualFile file) throws IOException {
    final String fileName = file.getName();
    if (tempDir.findChild(fileName) != null) {
      int idx = 0;
      while (true) {
        //noinspection HardCodedStringLiteral
        final String dirName = "dir" + idx++;
        final VirtualFile dir = tempDir.findChild(dirName);
        if (dir == null) {
          tempDir = tempDir.createChildDirectory(this, dirName);
          break;
        }
        if (dir.findChild(fileName) == null) {
          tempDir = dir;
          break;
        }
      }
    }
    return VfsUtil.copyFile(this, file, tempDir);
  }
  */

}
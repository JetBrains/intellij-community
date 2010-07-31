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
 * Date: Jan 17, 2003
 * Time: 3:22:59 PM
 */
package com.intellij.compiler.impl.javaCompiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerException;
import com.intellij.compiler.impl.CompileContextExProxy;
import com.intellij.compiler.impl.javaCompiler.javac.JavacCompiler;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Chunk;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AnnotationProcessingCompiler implements TranslatingCompiler{
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.javaCompiler.JavaCompiler");
  private final Project myProject;
  private final CompilerConfiguration myConfig;

  public AnnotationProcessingCompiler(Project project) {
    myProject = project;
    myConfig = CompilerConfiguration.getInstance(project);
  }

  @NotNull
  public String getDescription() {
    return CompilerBundle.message("annotation.processing.compiler.description");
  }

  public boolean isCompilableFile(VirtualFile file, CompileContext context) {
    if (!myConfig.isAnnotationProcessorsEnabled()) {
      return false;
    } 
    return file.getFileType() == StdFileTypes.JAVA && !isExcludedFromAnnotationProcessing(file, context);
  }

  public void compile(final CompileContext context, final Chunk<Module> moduleChunk, final VirtualFile[] files, OutputSink sink) {
    if (!myConfig.isAnnotationProcessorsEnabled()) {
      return;
    }
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    final CompileContextEx _context = new CompileContextExProxy((CompileContextEx)context) {
      public VirtualFile getModuleOutputDirectory(Module module) {
        final String path = CompilerPaths.getAnnotationProcessorsGenerationPath(module);
        return path != null? lfs.findFileByPath(path) : null;
      }

      public VirtualFile getModuleOutputDirectoryForTests(Module module) {
        return getModuleOutputDirectory(module);
      }
    };
    final JavacCompiler javacCompiler = getBackEndCompiler();
    final boolean processorMode = javacCompiler.setAnnotationProcessorMode(true);
    final BackendCompilerWrapper wrapper = new BackendCompilerWrapper(moduleChunk, myProject, Arrays.asList(files), _context, javacCompiler, sink);
    try {
      wrapper.compile();
    }
    catch (CompilerException e) {
      _context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    }
    catch (CacheCorruptedException e) {
      LOG.info(e);
      _context.requestRebuildNextTime(e.getMessage());
    }
    finally {
      javacCompiler.setAnnotationProcessorMode(processorMode);
      final Set<VirtualFile> dirsToRefresh = new HashSet<VirtualFile>();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          for (Module module : moduleChunk.getNodes()) {
            final VirtualFile out = _context.getModuleOutputDirectory(module);
            if (out != null) {
              dirsToRefresh.add(out);
            }
          }
        }
      });
      for (VirtualFile root : dirsToRefresh) {
        root.refresh(false, true);
      }
    }
  }

  private boolean isExcludedFromAnnotationProcessing(VirtualFile file, CompileContext context) {
    final Module module = context.getModuleByFile(file);
    if (module != null) {
      if (!myConfig.isAnnotationProcessingEnabled(module)) {
        return true;
      }
      final String path = CompilerPaths.getAnnotationProcessorsGenerationPath(module);
      final VirtualFile generationDir = path != null? LocalFileSystem.getInstance().findFileByPath(path) : null;
      if (generationDir != null && VfsUtil.isAncestor(generationDir, file, false)) {
        return true;
      }
    }
    return myConfig.isExcludedFromCompilation(file);
  }

  public boolean validateConfiguration(CompileScope scope) {
    final JavacCompiler compiler = getBackEndCompiler();
    final boolean previousValue = compiler.setAnnotationProcessorMode(true);
    try {
      return compiler.checkCompiler(scope);
    }
    finally {
      compiler.setAnnotationProcessorMode(previousValue);
    }
  }

  private JavacCompiler getBackEndCompiler() {
    CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    return configuration.getJavacCompiler();
  }

}

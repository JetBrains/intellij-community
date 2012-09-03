/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * @author max
 */
public class ReformatAndOptimizeImportsProcessor extends AbstractLayoutCodeProcessor {
  public static final String COMMAND_NAME = CodeInsightBundle.message("progress.reformat.and.optimize.common.command.text");
  private static final String PROGRESS_TEXT = CodeInsightBundle.message("reformat.and.optimize.progress.common.text");

  private final OptimizeImportsProcessor myOptimizeImportsProcessor;
  private final ReformatCodeProcessor myReformatCodeProcessor;

  public ReformatAndOptimizeImportsProcessor(Project project, boolean processChangedTextOnly) {
    super(project, COMMAND_NAME, PROGRESS_TEXT, processChangedTextOnly);
    myOptimizeImportsProcessor = new OptimizeImportsProcessor(project);
    myReformatCodeProcessor = new ReformatCodeProcessor(project, processChangedTextOnly);
  }

  public ReformatAndOptimizeImportsProcessor(Project project, Module module, boolean processChangedTextOnly) {
    super(project, module, COMMAND_NAME, PROGRESS_TEXT, processChangedTextOnly);
    myOptimizeImportsProcessor = new OptimizeImportsProcessor(project, module);
    myReformatCodeProcessor = new ReformatCodeProcessor(project, module, processChangedTextOnly);
  }

  public ReformatAndOptimizeImportsProcessor(Project project, PsiFile[] files, boolean processChangedTextOnly) {
    super(project, files, PROGRESS_TEXT, COMMAND_NAME, null, processChangedTextOnly);
    myOptimizeImportsProcessor = new OptimizeImportsProcessor(project);
    myReformatCodeProcessor = new ReformatCodeProcessor(project, processChangedTextOnly);
  }

  public ReformatAndOptimizeImportsProcessor(Project project,
                                             PsiDirectory directory,
                                             boolean includeSubdirs,
                                             boolean processChangedTextOnly)
  {
    super(project, directory, includeSubdirs, PROGRESS_TEXT, COMMAND_NAME, processChangedTextOnly);
    myOptimizeImportsProcessor = new OptimizeImportsProcessor(project, directory, includeSubdirs);
    myReformatCodeProcessor = new ReformatCodeProcessor(project, directory, includeSubdirs, processChangedTextOnly);
  }

  public ReformatAndOptimizeImportsProcessor(Project project, PsiFile file, boolean processChangedTextOnly) {
    super(project, file, PROGRESS_TEXT, COMMAND_NAME, processChangedTextOnly);
    myOptimizeImportsProcessor = new OptimizeImportsProcessor(project, file);
    myReformatCodeProcessor = new ReformatCodeProcessor(project, file, null, processChangedTextOnly);
  }

  @Override
  @NotNull
  protected FutureTask<Boolean> preprocessFile(@NotNull PsiFile file, boolean processChangedTextOnly) throws IncorrectOperationException {
    final FutureTask<Boolean> reformatTask = myReformatCodeProcessor.preprocessFile(file, processChangedTextOnly);
    final FutureTask<Boolean> optimizeImportsTask = myOptimizeImportsProcessor.preprocessFile(file, false);
    return new FutureTask<Boolean>(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        reformatTask.run();
        if (!reformatTask.get() || reformatTask.isCancelled()) {
          return false;
        }
        
        CodeStyleManagerImpl.setSequentialProcessingAllowed(false);
        try {
          optimizeImportsTask.run();
          return optimizeImportsTask.get() && !optimizeImportsTask.isCancelled();
        }
        finally {
          CodeStyleManagerImpl.setSequentialProcessingAllowed(true);
        }
      }
    });
  }
}

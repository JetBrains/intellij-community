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
import com.intellij.lang.ImportOptimizer;
import com.intellij.lang.LanguageImportStatements;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.FutureTask;

public class OptimizeImportsProcessor extends AbstractLayoutCodeProcessor {
  private static final String PROGRESS_TEXT = CodeInsightBundle.message("progress.text.optimizing.imports");
  public static final String COMMAND_NAME = CodeInsightBundle.message("process.optimize.imports");

  public OptimizeImportsProcessor(Project project) {
    super(project, COMMAND_NAME, PROGRESS_TEXT, false);
  }

  public OptimizeImportsProcessor(Project project, Module module) {
    super(project, module, COMMAND_NAME, PROGRESS_TEXT, false);
  }

  public OptimizeImportsProcessor(Project project, PsiDirectory directory, boolean includeSubdirs) {
    super(project, directory, includeSubdirs, PROGRESS_TEXT, COMMAND_NAME, false);
  }

  public OptimizeImportsProcessor(Project project, PsiFile file) {
    super(project, file, PROGRESS_TEXT, COMMAND_NAME, false);
  }

  public OptimizeImportsProcessor(Project project, PsiFile[] files, Runnable postRunnable) {
    this(project, files, COMMAND_NAME, postRunnable);
  }

  public OptimizeImportsProcessor(Project project, PsiFile[] files, String commandName, Runnable postRunnable) {
    super(project, files, PROGRESS_TEXT, commandName, postRunnable, false);
  }

  public OptimizeImportsProcessor(AbstractLayoutCodeProcessor processor) {
    super(processor, COMMAND_NAME, PROGRESS_TEXT);
  }

  @Override
  @NotNull
  protected FutureTask<Boolean> prepareTask(@NotNull PsiFile file, boolean processChangedTextOnly) {
    final Set<ImportOptimizer> optimizers = LanguageImportStatements.INSTANCE.forFile(file);
    final List<Runnable> runnables = new ArrayList<Runnable>();
    List<PsiFile> files = file.getViewProvider().getAllFiles();
    for (ImportOptimizer optimizer : optimizers) {
      for (PsiFile psiFile : files) {
        if (optimizer.supports(psiFile)) {
          runnables.add(optimizer.processFile(psiFile));
        }
      }
    }
    Runnable runnable = runnables.isEmpty() ? EmptyRunnable.getInstance() : new Runnable() {
      @Override
      public void run() {
        CodeStyleManagerImpl.setSequentialProcessingAllowed(false);
        try {
          for (Runnable runnable : runnables)
            runnable.run();
        }
        finally {
          CodeStyleManagerImpl.setSequentialProcessingAllowed(true);
        }
      }
    };
    return new FutureTask<Boolean>(runnable, true);
  }
}

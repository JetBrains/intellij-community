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

package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.lang.ImportOptimizer;
import com.intellij.lang.LanguageImportStatements;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.FutureTask;

public class OptimizeImportsProcessor extends AbstractLayoutCodeProcessor {
  private static final String PROGRESS_TEXT = CodeInsightBundle.message("progress.text.optimizing.imports");
  private static final String COMMAND_NAME = CodeInsightBundle.message("process.optimize.imports");

  public OptimizeImportsProcessor(Project project) {
    super(project, COMMAND_NAME, PROGRESS_TEXT);
  }

  public OptimizeImportsProcessor(Project project, Module module) {
    super(project, module, COMMAND_NAME, PROGRESS_TEXT);
  }

  public OptimizeImportsProcessor(Project project, PsiDirectory directory, boolean includeSubdirs) {
    super(project, directory, includeSubdirs, PROGRESS_TEXT, COMMAND_NAME);
  }

  public OptimizeImportsProcessor(Project project, PsiFile file) {
    super(project, file, PROGRESS_TEXT, COMMAND_NAME);
  }

  public OptimizeImportsProcessor(Project project, PsiFile[] files, Runnable postRunnable) {
    this(project, files, COMMAND_NAME, postRunnable);
  }

  public OptimizeImportsProcessor(Project project, PsiFile[] files, String commandName, Runnable postRunnable) {
    super(project, files, PROGRESS_TEXT, commandName, postRunnable);
  }

  @NotNull
  protected FutureTask<Boolean> preprocessFile(final PsiFile file) throws IncorrectOperationException {
    final ImportOptimizer optimizer = LanguageImportStatements.INSTANCE.forFile(file);
    Runnable runnable = optimizer != null ? optimizer.processFile(file) : EmptyRunnable.getInstance();
    return new FutureTask<Boolean>(runnable, true);
  }
}

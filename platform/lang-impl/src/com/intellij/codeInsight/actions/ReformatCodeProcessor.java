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
import com.intellij.formatting.FormattingProgressIndicatorImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class ReformatCodeProcessor extends AbstractLayoutCodeProcessor {
  
  public static final String COMMAND_NAME = CodeInsightBundle.message("process.reformat.code");
  
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.actions.ReformatCodeProcessor");

  private final TextRange myRange;
  private static final String PROGRESS_TEXT = CodeInsightBundle.message("reformat.progress.common.text");

  public ReformatCodeProcessor(Project project) {
    super(project, COMMAND_NAME, PROGRESS_TEXT);
    myRange = null;
  }

  public ReformatCodeProcessor(Project project, Module module) {
    super(project, module, COMMAND_NAME, PROGRESS_TEXT);
    myRange = null;
  }

  public ReformatCodeProcessor(Project project, PsiDirectory directory, boolean includeSubdirs) {
    super(project, directory, includeSubdirs, PROGRESS_TEXT, COMMAND_NAME);
    myRange = null;
  }

  public ReformatCodeProcessor(Project project, PsiFile file, TextRange range) {
    super(project, file, PROGRESS_TEXT, COMMAND_NAME);
    myRange = range;
  }

  public ReformatCodeProcessor(Project project, PsiFile[] files, Runnable postRunnable) {
    this(project, files, COMMAND_NAME, postRunnable);
  }
  
  public ReformatCodeProcessor(Project project, PsiFile[] files, String commandName, Runnable postRunnable) {
    super(project, files, PROGRESS_TEXT, commandName, postRunnable);
    myRange = null;
  }

  @NotNull
  protected FutureTask<Boolean> preprocessFile(final PsiFile file) throws IncorrectOperationException {
    return new FutureTask<Boolean>(new Callable<Boolean>() {
      @Override
      public Boolean call() throws Exception {
        FormattingProgressIndicatorImpl.FORMATTING_CANCELLED_FLAG.set(false);
        try {
          TextRange range = myRange == null ? file.getTextRange() : myRange;
          CodeStyleManager.getInstance(myProject).reformatText(file, range.getStartOffset(), range.getEndOffset());
          return !FormattingProgressIndicatorImpl.FORMATTING_CANCELLED_FLAG.get();
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
          return false;
        }
      }
    });
  }
}

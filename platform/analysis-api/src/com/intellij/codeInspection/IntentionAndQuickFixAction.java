/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
 */
public abstract class IntentionAndQuickFixAction implements LocalQuickFix, IntentionAction{
  public static IntentionAndQuickFixAction[] EMPTY_ARRAY = new IntentionAndQuickFixAction[0];

  @Override
  @NotNull
  public abstract String getName();

  @Override
  @NotNull
  public abstract String getFamilyName();

  public abstract void applyFix(@NotNull Project project, final PsiFile file, @Nullable final Editor editor);

  @Override
  @NotNull
  public final String getText() {
    return getName();
  }

  @Override
  public final void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    applyFix(project, descriptor.getPsiElement().getContainingFile(), null);
  }

  @Override
  public final void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    applyFix(project, file, editor);
  }

  /**
   *  In general case will be called if invoked as IntentionAction.
   */
  @Override
  public boolean isAvailable(@NotNull final Project project, @Nullable final Editor editor, final PsiFile file) {
    return true;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}

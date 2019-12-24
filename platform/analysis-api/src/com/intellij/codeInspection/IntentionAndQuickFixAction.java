// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Gregory.Shrago
 * @see LocalQuickFixAndIntentionActionOnPsiElement
 */
public abstract class IntentionAndQuickFixAction implements LocalQuickFix, IntentionAction {

  @Override
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  public abstract String getName();

  @Override
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  public abstract String getFamilyName();

  public abstract void applyFix(@NotNull Project project, PsiFile file, @Nullable Editor editor);

  @Override
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  public String getText() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    applyFix(project, descriptor.getPsiElement().getContainingFile(), null);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    applyFix(project, file, editor);
  }

  /**
   * In general case will be called if invoked as IntentionAction.
   */
  @Override
  public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}

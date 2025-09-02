// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface implementing at once LocalQuickFix and IntentionAction.
 *
 * @author Gregory.Shrago
 * @see LocalQuickFixAndIntentionActionOnPsiElement
 * <p>
 * Other possible usage is in-depth customization of quick-fix's UI.
 * <p>
 * For example, user can implement {@link com.intellij.codeInsight.intention.CustomizableIntentionAction}
 * and this interface. During creation of popup quick-fix will not be wrapped into QuickFixWrapper and
 * UI will be customized as {@link com.intellij.codeInsight.intention.CustomizableIntentionAction} instructs to.
 */
public abstract class IntentionAndQuickFixAction implements LocalQuickFix, IntentionAction {

  @Override
  public abstract @IntentionName @NotNull String getName();

  @Override
  public abstract @IntentionFamilyName @NotNull String getFamilyName();

  public abstract void applyFix(@NotNull Project project, PsiFile psiFile, @Nullable Editor editor);

  @Override
  public @IntentionName @NotNull String getText() {
    return getName();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    applyFix(project, descriptor.getPsiElement().getContainingFile(), null);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    applyFix(project, psiFile, editor);
  }

  /**
   * In general case will be called if invoked as IntentionAction.
   */
  @Override
  public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, PsiFile psiFile) {
    return true;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}

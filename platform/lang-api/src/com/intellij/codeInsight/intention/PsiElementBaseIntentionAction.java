// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * To solve "caret after last symbol" problem consider using {@link BaseElementAtCaretIntentionAction}
 *
 * @author Anna Kozlova
 * @author Konstantin Bulenkov
 */
public abstract class PsiElementBaseIntentionAction extends BaseIntentionAction {
  @Override
  public final void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    if (editor == null || !checkFile(psiFile)) return;
    final PsiElement element = getElement(editor, psiFile);
    if (element != null) {
      invoke(project, editor, element);
    }
  }

  /**
   * Check whether this intention available in file.
   */
  public boolean checkFile(@Nullable PsiFile file) {
    if (file == null) return false;
    return canModify(file);
  }

  /**
   * Invokes intention action for the element under caret.
   *
   * @param project the project in which the file is opened.
   * @param editor  the editor for the file.
   * @param element the element under cursor.
   */
  public abstract void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException;

  @Override
  public final boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!checkFile(psiFile)) return false;
    final PsiElement element = editor == null ? null : getElement(editor, psiFile);
    return element != null && isAvailable(project, editor, element);
  }

  /**
   * Checks whether this intention is available at a caret offset in file.
   * If this method returns true, a light bulb for this intention is shown.
   *
   * @param project the project in which the availability is checked.
   * @param editor  the editor in which the intention will be invoked.
   * @param element the element under caret.
   * @return true if the intention is available, false otherwise.
   */
  public abstract boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element);

  /**
   * Retrieves the element this intention was invoked on.
   *
   * @param editor the editor in which the intention was invoked, can be preview editor.
   * @param file  the file in which the intention was invoked.
   * @return the element under caret.
   */
  protected static @Nullable PsiElement getElement(@NotNull Editor editor, @NotNull PsiFile file) {
    CaretModel caretModel = editor.getCaretModel();
    int position = caretModel.getOffset();
    return file.findElementAt(position);
  }
}
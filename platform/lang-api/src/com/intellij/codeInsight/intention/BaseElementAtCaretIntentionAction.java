// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseElementAtCaretIntentionAction extends BaseIntentionAction {
  private volatile boolean useElementToTheLeft;

  @Override
  public final boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (editor == null || psiFile == null || !checkFile(psiFile)) return false;

    useElementToTheLeft = false;
    int offset = editor.getCaretModel().getOffset();
    PsiElement elementToTheRight = psiFile.findElementAt(offset);
    if (elementToTheRight != null && isAvailable(project, editor, elementToTheRight)) {
      return true;
    }

    PsiElement elementToTheLeft = offset > 0 ? psiFile.findElementAt(offset - 1) : null;
    if (elementToTheLeft != null && isAvailable(project, editor, elementToTheLeft)) {
      useElementToTheLeft = true;
      return true;
    }

    return false;
  }

  protected boolean checkFile(@NotNull PsiFile file) {
    return canModify(file);
  }

  /**
   * Checks whether this intention is available at a caret offset in file.
   * If this method returns {@code true}, a light bulb for this intention is shown.
   *
   * @param project the project in which the availability is checked.
   * @param editor  the editor in which the intention will be invoked.
   * @param element the element under caret.
   * @return true if the intention is available, false otherwise.
   */
  public abstract boolean isAvailable(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element);

  @Override
  public final void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    if (editor == null || psiFile == null) return;

    PsiElement element = getElement(editor, psiFile);
    if (element == null) return;

    invoke(project, editor, element);
  }

  /**
   * Invokes intention action for the element under cursor.
   *
   * @param project the project in which the file is opened.
   * @param editor  the editor for the file.
   * @param element the element under cursor.
   * @throws IncorrectOperationException On errors.
   */
  public abstract void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiElement element) throws IncorrectOperationException;

  /**
   * Retrieves the element this intention was invoked on.
   *
   * @param editor the editor in which the intention was invoked, may be preview editor.
   * @param file  the file in which the intention was invoked.
   * @return the element under the caret.
   */
  protected @Nullable PsiElement getElement(@NotNull Editor editor, @NotNull PsiFile file) {
    int position = editor.getCaretModel().getOffset();
    return file.findElementAt(useElementToTheLeft ? position - 1 : position);
  }
}
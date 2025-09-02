// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class SuppressIntentionAction implements Iconable, IntentionAction {
  private @IntentionName String myText = "";
  public static final SuppressIntentionAction[] EMPTY_ARRAY = new SuppressIntentionAction[0];

  @Override
  public Icon getIcon(int flags) {
    return null;
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return myText;
  }

  protected void setText(@IntentionName @NotNull String text) {
    myText = text;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public String toString() {
    return getText();
  }

  @Override
  public final void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    PsiElement element = getElement(editor, psiFile);
    if (element != null) {
      invoke(project, editor, element);
    }
  }

  /**
   * Invokes intention action for the element under caret.
   *
   * @param project the project in which the file is opened.
   * @param editor  the editor for the file.
   * @param element the element under cursor.
   *
   */
  public abstract void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException;

  @Override
  public final boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (psiFile == null || editor == null) return false;
    PsiElement element = getElement(editor, psiFile);
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

  public boolean isSuppressAll() {
    return false;
  }

  private static @Nullable PsiElement getElement(@NotNull Editor editor, @NotNull PsiFile file) {
    CaretModel caretModel = editor.getCaretModel();
    int position = caretModel.getOffset();
    return file.findElementAt(position);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    // No preview is necessary for suppress action
    return IntentionPreviewInfo.EMPTY;
  }
}

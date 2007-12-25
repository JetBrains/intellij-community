/*
 * User: anna
 * Date: 24-Dec-2007
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class SuppressIntentionAction extends PsiElementBaseIntentionAction {
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final CaretModel caretModel = editor.getCaretModel();
    final int position = caretModel.getOffset();
    invoke(project, editor, file.findElementAt(position));
  }

  public abstract void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException;
}
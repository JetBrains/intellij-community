package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiThrowStatement;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 8, 2003
 * Time: 2:48:47 PM
 * To change this template use Options | File Templates.
 */
public class BreakingControlFlowEnterProcessor implements EnterProcessor {
  public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    if (psiElement instanceof PsiReturnStatement || psiElement instanceof PsiThrowStatement) {
      PsiElement parent = psiElement.getParent();
      if (!(parent instanceof PsiCodeBlock)) return false;
      editor.getCaretModel().moveToOffset(parent.getTextRange().getEndOffset());
    }

    return false;
  }
}

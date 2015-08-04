package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.util.IncorrectOperationException;

/**
 * @author peter
 */
public class MissingClassBodyFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiClass) || psiElement instanceof PsiTypeParameter) return;
    PsiClass psiClass = (PsiClass) psiElement;

    if (psiClass.getLBrace() == null) {
      int offset = psiClass.getTextRange().getEndOffset();
      editor.getDocument().insertString(offset, "{\n}");
      editor.getCaretModel().moveToOffset(offset);
    }
  }
}

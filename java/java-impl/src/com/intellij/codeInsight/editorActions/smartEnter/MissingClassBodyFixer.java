package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * @author peter
 */
public class MissingClassBodyFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiRecordComponent) {
      psiElement = ((PsiRecordComponent)psiElement).getContainingClass();
    }
    if (!(psiElement instanceof PsiClass) || psiElement instanceof PsiTypeParameter) return;
    PsiClass psiClass = (PsiClass) psiElement;

    if (psiClass.getLBrace() == null) {
      PsiElement lastChild = psiClass.getLastChild();
      int offset = psiClass.getTextRange().getEndOffset();
      if (lastChild instanceof PsiErrorElement) {
        PsiElement previous = lastChild.getPrevSibling();
        if (previous instanceof PsiWhiteSpace) {
          offset = previous.getTextRange().getStartOffset();
        } else {
          offset = lastChild.getTextRange().getStartOffset();
        }
      }
      if (psiClass.isRecord() && psiClass.getRecordHeader() == null) {
        editor.getDocument().insertString(offset, "() {}");
        editor.getCaretModel().moveToOffset(offset + 1);
        processor.setSkipEnter(true);
      } else {
        editor.getDocument().insertString(offset, "{\n}");
        editor.getCaretModel().moveToOffset(offset);
      }
    }
  }
}

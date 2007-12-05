package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.text.CharArrayUtil;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 8, 2003
 * Time: 2:35:36 PM
 * To change this template use Options | File Templates.
 */
public class AfterSemicolonEnterProcessor implements EnterProcessor {
  public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    if (psiElement instanceof PsiExpressionStatement ||
        psiElement instanceof PsiDeclarationStatement ||
        psiElement instanceof PsiDoWhileStatement ||
        psiElement instanceof PsiReturnStatement ||
        psiElement instanceof PsiThrowStatement ||
        psiElement instanceof PsiBreakStatement ||
        psiElement instanceof PsiContinueStatement ||
        psiElement instanceof PsiAssertStatement ||
        psiElement instanceof PsiField ||
        psiElement instanceof PsiMethod && (((PsiMethod) psiElement).getContainingClass().isInterface() ||
                                            ((PsiMethod) psiElement).hasModifierProperty(PsiModifier.ABSTRACT) ||
                                            ((PsiMethod) psiElement).hasModifierProperty(PsiModifier.NATIVE))) {
      int errorOffset = getErrorElementOffset(psiElement);
      int elementEndOffset = psiElement.getTextRange().getEndOffset();
      if (psiElement instanceof PsiEnumConstant) {
        final CharSequence text = editor.getDocument().getCharsSequence();
        final int commaOffset = CharArrayUtil.shiftForwardUntil(text, elementEndOffset, ",");
        if (commaOffset < text.length()) {
          elementEndOffset = commaOffset + 1;
        }
      }

      if (errorOffset >= 0 && errorOffset < elementEndOffset) {
        final CharSequence text = editor.getDocument().getCharsSequence();
        if (text.charAt(errorOffset) == ' ' && text.charAt(errorOffset + 1) == ';') {
          errorOffset++;
        }
      }

      editor.getCaretModel().moveToOffset(errorOffset >= 0 ? errorOffset : elementEndOffset);
      return isModified;
    }
    return false;
  }

  private static int getErrorElementOffset(PsiElement elt) {
    final int[] offset = new int[] { -1 };
    elt.accept(new PsiRecursiveElementVisitor() {
      @Override public void visitErrorElement(PsiErrorElement element) {
        if (offset[0] == -1) offset[0] = element.getTextRange().getStartOffset();
      }
    });
    return offset[0];
  }
}

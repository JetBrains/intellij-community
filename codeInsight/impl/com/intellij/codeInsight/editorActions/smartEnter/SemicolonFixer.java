package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 3:35:49 PM
 * To change this template use Options | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class SemicolonFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiReturnStatement) {
      PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
      if (method != null && PsiType.VOID == method.getReturnType()) {
        PsiReturnStatement stmt = (PsiReturnStatement)psiElement;
        if (stmt.getReturnValue() != null) {
          Document doc = editor.getDocument();
          doc.insertString(stmt.getTextRange().getStartOffset() + "return".length(), ";");
          return;
        }
      }
    }


    if (psiElement instanceof PsiExpressionStatement ||
        psiElement instanceof PsiDeclarationStatement ||
        psiElement instanceof PsiDoWhileStatement ||
        psiElement instanceof PsiReturnStatement ||
        psiElement instanceof PsiThrowStatement ||
        psiElement instanceof PsiBreakStatement ||
        psiElement instanceof PsiContinueStatement ||
        psiElement instanceof PsiAssertStatement ||
        psiElement instanceof PsiField && !(psiElement instanceof PsiEnumConstant) ||
        psiElement instanceof PsiMethod && (((PsiMethod) psiElement).getContainingClass().isInterface() ||
                                            ((PsiMethod) psiElement).hasModifierProperty(PsiModifier.ABSTRACT))) {
      String text = psiElement.getText();

      int insertionOffset = psiElement.getTextRange().getEndOffset();
      Document doc = editor.getDocument();
      if (psiElement instanceof PsiField && ((PsiField) psiElement).hasModifierProperty(PsiModifier.ABSTRACT)) {
        // absract rarely seem to be field. It is rather incomplete method.
        doc.insertString(insertionOffset, "()");
        insertionOffset += "()".length();
      }

      if (!StringUtil.endsWithChar(text, ';')) {
        final PsiElement parent = psiElement.getParent();
        if (parent instanceof PsiForStatement && ((PsiForStatement) parent).getUpdate() == psiElement) {
          return;
        }
        doc.insertString(insertionOffset, ";");
      }
    }
  }
}

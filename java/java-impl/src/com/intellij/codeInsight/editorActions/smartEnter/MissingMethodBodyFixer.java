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
 * Time: 7:24:03 PM
 * To change this template use Options | File Templates.
 */
public class MissingMethodBodyFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiMethod)) return;
    PsiMethod method = (PsiMethod) psiElement;
    if (method.getContainingClass().isInterface() || method.hasModifierProperty(PsiModifier.ABSTRACT)) return;
    final PsiCodeBlock body = method.getBody();
    final Document doc = editor.getDocument();
    if (body != null) {
      // See IDEADEV-1093. This is quite hacky heuristic but it seem to be best we can do.
      String bodyText = body.getText();
      if (bodyText.startsWith("{")) {
        final PsiStatement[] statements = body.getStatements();
        if (statements.length > 0) {
          if (statements[0] instanceof PsiDeclarationStatement) {
            if (PsiTreeUtil.getDeepestLast(statements[0]) instanceof PsiErrorElement) {
              if (method.getContainingClass().getRBrace() == null) {
                doc.insertString(body.getTextRange().getStartOffset() + 1, "\n}");
              }
            }
          }
        }
      }
      return;
    }
    int endOffset = method.getTextRange().getEndOffset();
    if (StringUtil.endsWithChar(method.getText(), ';')) {
      doc.deleteString(endOffset - 1, endOffset);
      endOffset--;
    }
    doc.insertString(endOffset, "{\n}");
  }
}

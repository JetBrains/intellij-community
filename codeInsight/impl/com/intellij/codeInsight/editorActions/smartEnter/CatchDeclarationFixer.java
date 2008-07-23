package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 5:32:01 PM
 * To change this template use Options | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class CatchDeclarationFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiCatchSection) {
      final Document doc = editor.getDocument();
      final PsiCatchSection catchSection = (PsiCatchSection) psiElement;

      final int catchStart = catchSection.getTextRange().getStartOffset();
      int stopOffset = doc.getLineEndOffset(doc.getLineNumber(catchStart));

      final PsiCodeBlock catchBlock = catchSection.getCatchBlock();
      if (catchBlock != null) {
        stopOffset = Math.min(stopOffset, catchBlock.getTextRange().getStartOffset());
      }
      stopOffset = Math.min(stopOffset, catchSection.getTextRange().getEndOffset());

      final PsiJavaToken lParenth = catchSection.getLParenth();
      if (lParenth == null) {
        doc.replaceString(catchStart, stopOffset, "catch ()");
        processor.registerUnresolvedError(catchStart + "catch (".length());
      } else {
        if (catchSection.getParameter() == null) {
          processor.registerUnresolvedError(lParenth.getTextRange().getEndOffset());
        }
        if (catchSection.getRParenth() == null) {
          doc.insertString(stopOffset, ")");
        }
      }
    }
  }
}
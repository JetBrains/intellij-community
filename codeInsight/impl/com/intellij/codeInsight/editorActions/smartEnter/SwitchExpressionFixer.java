/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jul 30, 2006
 * Time: 4:55:07 PM
 */
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
public class SwitchExpressionFixer implements Fixer {
  public void apply(Editor editor, SmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiSwitchStatement) {
      final Document doc = editor.getDocument();
      final PsiSwitchStatement switchStatement = (PsiSwitchStatement)psiElement;
      final PsiJavaToken rParenth = switchStatement.getRParenth();
      final PsiJavaToken lParenth = switchStatement.getLParenth();
      final PsiExpression condition = switchStatement.getExpression();

      if (condition == null) {
        if (lParenth == null || rParenth == null) {
          int stopOffset = doc.getLineEndOffset(doc.getLineNumber(switchStatement.getTextRange().getStartOffset()));
          final PsiCodeBlock block = switchStatement.getBody();
          if (block != null) {
            stopOffset = Math.min(stopOffset, block.getTextRange().getStartOffset());
          }
          doc.replaceString(switchStatement.getTextRange().getStartOffset(), stopOffset, "switch ()");
        } else {
          processor.registerUnresolvedError(lParenth.getTextRange().getEndOffset());
        }
      } else if (rParenth == null) {
        doc.insertString(condition.getTextRange().getEndOffset(), ")");
      }
    }
  }
}
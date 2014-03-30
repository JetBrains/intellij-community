/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspMethodCall;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 3:35:49 PM
 * To change this template use Options | File Templates.
 */
public class MethodCallFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    PsiExpressionList args = null;
    if (psiElement instanceof PsiMethodCallExpression && !(psiElement instanceof JspMethodCall)) {
      args = ((PsiMethodCallExpression) psiElement).getArgumentList();
    } else if (psiElement instanceof PsiNewExpression) {
      args = ((PsiNewExpression) psiElement).getArgumentList();
    }

    if (args == null) return;

    PsiElement parenth = args.getLastChild();

    if (parenth == null || !")".equals(parenth.getText())) {
      int endOffset = -1;
      PsiElement child = args.getFirstChild();
      while (child != null) {
        if (child instanceof PsiErrorElement) {
          final PsiErrorElement errorElement = (PsiErrorElement)child;
          if (errorElement.getErrorDescription().indexOf("')'") >= 0) {
            endOffset = errorElement.getTextRange().getStartOffset();
            break;
          }
        }
        child = child.getNextSibling();
      }

      if (endOffset == -1) {
        endOffset = args.getTextRange().getEndOffset();
      }

      final PsiExpression[] params = args.getExpressions();
      if (params.length > 0 && 
          startLine(editor, args) != startLine(editor, params[0]) && 
          editor.getCaretModel().getOffset() < params[0].getTextRange().getStartOffset()) {
        endOffset = args.getTextRange().getStartOffset() + 1;
      }

      endOffset = CharArrayUtil.shiftBackward(editor.getDocument().getCharsSequence(), endOffset - 1, " \t\n") + 1;
      editor.getDocument().insertString(endOffset, ")");
    }
  }

  private int startLine(Editor editor, PsiElement psiElement) {
    return editor.getDocument().getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}

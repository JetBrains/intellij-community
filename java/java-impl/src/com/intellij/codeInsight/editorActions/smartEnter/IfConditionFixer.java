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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author max
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class IfConditionFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiIfStatement) {
      final Document doc = editor.getDocument();
      final PsiIfStatement ifStatement = (PsiIfStatement) psiElement;
      final PsiJavaToken rParen = ifStatement.getRParenth();
      final PsiJavaToken lParen = ifStatement.getLParenth();
      final PsiExpression condition = ifStatement.getCondition();

      if (condition == null) {
        if (lParen == null || rParen == null) {
          int stopOffset = doc.getLineEndOffset(doc.getLineNumber(ifStatement.getTextRange().getStartOffset()));
          final PsiStatement then = ifStatement.getThenBranch();
          if (then != null) {
            stopOffset = Math.min(stopOffset, then.getTextRange().getStartOffset());
          }
          stopOffset = Math.min(stopOffset, ifStatement.getTextRange().getEndOffset());

          doc.replaceString(ifStatement.getTextRange().getStartOffset(), stopOffset, "if ()");
          
          processor.registerUnresolvedError(ifStatement.getTextRange().getStartOffset() + "if (".length());
        } else {
          processor.registerUnresolvedError(lParen.getTextRange().getEndOffset());
        }
      } else if (rParen == null) {
        doc.insertString(condition.getTextRange().getEndOffset(), ")");
      }
    }
    else if (psiElement instanceof PsiExpression && psiElement.getParent() instanceof PsiExpressionStatement) {
      PsiElement prevLeaf = PsiTreeUtil.prevVisibleLeaf(psiElement);
      if (prevLeaf != null && prevLeaf.textMatches(PsiKeyword.IF)) {
        Document doc = editor.getDocument();
        doc.insertString(psiElement.getTextRange().getEndOffset(), ")");
        doc.insertString(psiElement.getTextRange().getStartOffset(), "(");
      }
    }
  }
}

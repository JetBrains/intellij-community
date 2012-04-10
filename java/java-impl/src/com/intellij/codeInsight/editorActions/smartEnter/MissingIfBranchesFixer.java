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
import com.intellij.util.IncorrectOperationException;

/**
 * @author max
 */
public class MissingIfBranchesFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiIfStatement)) return;

    PsiIfStatement ifStatement = (PsiIfStatement) psiElement;
    final Document doc = editor.getDocument();
    final PsiStatement elseBranch = ifStatement.getElseBranch();
    final PsiKeyword elseElement = ifStatement.getElseElement();
    if (elseElement != null && (elseBranch == null || !(elseBranch instanceof PsiBlockStatement) &&
                                                      startLine(doc, elseBranch) > startLine(doc, elseElement))) {
      doc.insertString(elseElement.getTextRange().getEndOffset(), "{}");
    }

    PsiElement thenBranch = ifStatement.getThenBranch();
    if (thenBranch instanceof PsiBlockStatement) return;

    boolean transformingOneLiner = false;
    if (thenBranch != null && startLine(doc, thenBranch) == startLine(doc, ifStatement)) {
      if (ifStatement.getCondition() != null) {
        return;
      }
      transformingOneLiner = true;
    }

    final PsiJavaToken rParenth = ifStatement.getRParenth();
    assert rParenth != null;

    if (elseBranch == null && !transformingOneLiner || thenBranch == null) {
      doc.insertString(rParenth.getTextRange().getEndOffset(), "{}");
    }
    else {
      doc.insertString(rParenth.getTextRange().getEndOffset(), "{");
      doc.insertString(thenBranch.getTextRange().getEndOffset() + 1, "}");
    }
  }

  private static int startLine(Document doc, PsiElement psiElement) {
    return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}

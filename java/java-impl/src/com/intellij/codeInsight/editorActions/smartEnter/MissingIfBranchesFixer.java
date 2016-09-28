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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class MissingIfBranchesFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiIfStatement)) return;

    PsiIfStatement ifStatement = (PsiIfStatement) psiElement;
    final Document doc = editor.getDocument();
    final PsiKeyword elseElement = ifStatement.getElseElement();
    if (elseElement != null) {
      handleBranch(doc, ifStatement, elseElement, ifStatement.getElseBranch());
    }

    PsiJavaToken rParenth = ifStatement.getRParenth();
    assert rParenth != null;
    handleBranch(doc, ifStatement, rParenth, ifStatement.getThenBranch());
  }

  private static void handleBranch(@NotNull Document doc, @NotNull PsiIfStatement ifStatement, @NotNull PsiElement beforeBranch, @Nullable PsiStatement branch) {
    if (branch instanceof PsiBlockStatement) return;
    boolean transformingOneLiner = branch != null && (startLine(doc, beforeBranch) == startLine(doc, branch) ||
                                                      startCol(doc, ifStatement) < startCol(doc, branch));

    if (!transformingOneLiner) {
      doc.insertString(beforeBranch.getTextRange().getEndOffset(), "{}");
    }
    else {
      doc.insertString(beforeBranch.getTextRange().getEndOffset(), "{");
      doc.insertString(branch.getTextRange().getEndOffset() + 1, "}");
    }

  }

  private static int startLine(Document doc, @NotNull PsiElement psiElement) {
    return doc.getLineNumber(psiElement.getTextRange().getStartOffset());
  }

  private static int startCol(Document doc, @NotNull PsiElement psiElement) {
    int offset = psiElement.getTextRange().getStartOffset();
    return offset - doc.getLineStartOffset(doc.getLineNumber(offset));
  }
}

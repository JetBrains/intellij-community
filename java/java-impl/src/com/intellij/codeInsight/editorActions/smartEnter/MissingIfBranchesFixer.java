// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    if (branch instanceof PsiBlockStatement || beforeBranch.textMatches(PsiKeyword.ELSE) && branch instanceof PsiIfStatement) return;
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

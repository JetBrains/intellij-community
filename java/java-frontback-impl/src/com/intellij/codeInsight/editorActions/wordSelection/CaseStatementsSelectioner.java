// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.PsiSwitchStatement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class CaseStatementsSelectioner extends BasicSelectioner {

  @Override
  public boolean canSelect(@NotNull PsiElement e) {
    return
      !(PsiUtil.isJavaToken(e, JavaTokenType.RBRACE)) &&
      !(PsiUtil.isJavaToken(e, JavaTokenType.LBRACE)) &&
      e.getParent() instanceof PsiCodeBlock &&
      e.getParent().getParent() instanceof PsiSwitchStatement;
  }

  @Override
  public List<TextRange> select(@NotNull PsiElement statement,
                                @NotNull CharSequence editorText,
                                int cursorOffset,
                                @NotNull Editor editor) {
    List<TextRange> result = new ArrayList<>();
    PsiElement caseStart = statement;
    PsiElement caseEnd = statement;

    if (statement instanceof PsiSwitchStatement) return result;

    PsiElement labelStatement = statement instanceof PsiSwitchLabelStatement ? statement : null;
    PsiElement sibling;
    if (labelStatement == null) {
      sibling = statement.getPrevSibling();
      while (sibling != null && !(sibling instanceof PsiSwitchLabelStatement)) {
        if (!(sibling instanceof PsiWhiteSpace)) caseStart = sibling;
        sibling = sibling.getPrevSibling();
      }
      labelStatement = sibling;
    }
    if (labelStatement != null) {
      PsiElement nextLabel;
      while ((nextLabel = PsiTreeUtil.skipSiblingsBackward(labelStatement, PsiWhiteSpace.class)) instanceof PsiSwitchLabelStatement) {
        labelStatement = nextLabel;
      }
    }

    sibling = statement instanceof PsiWhiteSpace ? statement.getNextSibling() : statement;
    while (sibling instanceof PsiSwitchLabelStatement) sibling = PsiTreeUtil.skipSiblingsForward(sibling, PsiWhiteSpace.class);
    while(sibling != null && !(sibling instanceof PsiSwitchLabelStatement)) {
      if (!(sibling instanceof PsiWhiteSpace) &&
          !(sibling instanceof PsiJavaToken) // end of switch
      ) {
        caseEnd = sibling;
      }
      sibling = sibling.getNextSibling();
    }

    Document document = editor.getDocument();
    int endOffset = DocumentUtil.getLineEndOffset(caseEnd.getTextOffset() + caseEnd.getTextLength(), document) + 1;

    if (!(caseStart instanceof PsiSwitchLabelStatement)) {
      result.add(new TextRange(DocumentUtil.getLineStartOffset(caseStart.getTextOffset(), document), endOffset));
    }
    if (labelStatement != null) {
      result.add(new TextRange(DocumentUtil.getLineStartOffset(labelStatement.getTextOffset(), document), endOffset));
    }
    return result;
  }
}

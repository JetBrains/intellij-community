// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.codeInsight.CodeInsightFrontbackUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiSwitchLabelStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JavaCaseBlockMover extends LineMover {

  @Override
  public boolean checkAvailable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down) {
    if (!(file instanceof PsiJavaFile)) return false;
    if (!super.checkAvailable(editor, file, info, down)) return false;

    final Document document = editor.getDocument();
    int startOffset = document.getLineStartOffset(info.toMove.startLine);
    int endOffset = getLineStartSafeOffset(document, info.toMove.endLine);
    List<PsiSwitchLabelStatement> statements = new SmartList<>();
    PsiElement firstElement = null;
    for (PsiElement element : CodeInsightFrontbackUtil.findStatementsInRange(file, startOffset, endOffset)) {
      if (element instanceof PsiSwitchLabelStatement) {
        statements.add((PsiSwitchLabelStatement)element);
      }
      else if (statements.isEmpty()) {
        firstElement = element;
      }
    }
    if (statements.isEmpty()) return false;
    if (firstElement != null) return info.prohibitMove(); // nonsensical selection

    PsiSwitchLabelStatement firstToMove = getThisCaseBlockStart(statements.get(0));
    PsiSwitchLabelStatement lastStatement = statements.get(statements.size() - 1);
    PsiElement nextCaseBlockStart = getNextCaseBlockStart(lastStatement);
    PsiElement lastToMove = PsiTreeUtil.skipWhitespacesBackward(nextCaseBlockStart);
    assert lastToMove != null;

    LineRange range = createRange(document, firstToMove, lastToMove);
    if (range == null) return info.prohibitMove();
    info.toMove = range;

    PsiElement firstToMove2;
    PsiElement lastToMove2;
    if (down) {
      if (!(nextCaseBlockStart instanceof PsiSwitchLabelStatement) || nextCaseBlockStart == lastStatement) return info.prohibitMove();
      firstToMove2 = nextCaseBlockStart;
      nextCaseBlockStart = getNextCaseBlockStart((PsiSwitchLabelStatement)firstToMove2);
      lastToMove2 = PsiTreeUtil.skipWhitespacesBackward(nextCaseBlockStart);
      assert lastToMove2 != null;
    }
    else {
      lastToMove2 = PsiTreeUtil.skipWhitespacesBackward(firstToMove);
      if (lastToMove2 == null) return info.prohibitMove();
      firstToMove2 = PsiTreeUtil.getPrevSiblingOfType(lastToMove2, PsiSwitchLabelStatement.class);
      if (firstToMove2 == null) return info.prohibitMove();
      firstToMove2 = getThisCaseBlockStart((PsiSwitchLabelStatement)firstToMove2);
    }
    LineRange range2 = createRange(document, firstToMove2, lastToMove2);
    if (range2 == null) return info.prohibitMove();
    info.toMove2 = range2;
    return true;
  }

  // returns PsiSwitchLabelStatement starting this case block
  private static @NotNull PsiSwitchLabelStatement getThisCaseBlockStart(PsiSwitchLabelStatement element) {
    PsiElement tmp;
    while ((tmp = PsiTreeUtil.skipWhitespacesBackward(element)) instanceof PsiSwitchLabelStatement) {
      element = (PsiSwitchLabelStatement)tmp;
    }
    return element;
  }

  // returns PsiSwitchLabelStatement starting next case block, or switch block's closing brace, if there is no next case block
  private static @NotNull PsiElement getNextCaseBlockStart(PsiSwitchLabelStatement element) {
    PsiElement result = element;
    PsiElement tmp;
    while ((tmp = PsiTreeUtil.skipWhitespacesForward(result)) instanceof PsiSwitchLabelStatement) {
      result = tmp;
    }
    tmp = PsiTreeUtil.getNextSiblingOfType(result, PsiSwitchLabelStatement.class);
    return tmp == null ? result.getParent().getLastChild() : tmp;
  }

  private static @Nullable LineRange createRange(@NotNull Document document, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    CharSequence text = document.getImmutableCharSequence();
    int startOffset = startElement.getTextRange().getStartOffset();
    int startLine = document.getLineNumber(startOffset);
    if (!CharArrayUtil.isEmptyOrSpaces(text, document.getLineStartOffset(startLine), startOffset)) {
      return null;
    }
    int endOffset = endElement.getTextRange().getEndOffset();
    int endLine = document.getLineNumber(endOffset);
    if (!CharArrayUtil.isEmptyOrSpaces(text, endOffset, document.getLineEndOffset(endLine))) {
      return null;
    }
    return new LineRange(startLine, endLine + 1);
  }
}

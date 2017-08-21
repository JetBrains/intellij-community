/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CaseBlockMover extends StatementUpDownMover {
  @Override
  public boolean checkAvailable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down) {
    if (!(file instanceof PsiJavaFile)) return false;

    PsiElement startElement = firstNonWhiteElement(editor.getSelectionModel().getSelectionStart(), file, true);
    if (startElement == null) return false;
    PsiElement endElement = firstNonWhiteElement(editor.getSelectionModel().getSelectionEnd(), file, false);
    if (endElement == null) return false;

    PsiSwitchLabelStatement caseStatement = PsiTreeUtil.getParentOfType(PsiTreeUtil.findCommonParent(startElement, endElement),
                                                                        PsiSwitchLabelStatement.class, false);
    if (caseStatement == null) return false;

    PsiElement firstToMove = getThisCaseBlockStart(caseStatement);
    PsiElement nextCaseBlockStart = getNextCaseBlockStart(caseStatement);
    PsiElement lastToMove = PsiTreeUtil.skipWhitespacesBackward(nextCaseBlockStart);
    assert lastToMove != null;

    LineRange range = createRange(editor.getDocument(), firstToMove, lastToMove);
    if (range == null) return info.prohibitMove();
    info.toMove = range;

    PsiElement firstToMove2;
    PsiElement lastToMove2;
    if (down) {
      if (!(nextCaseBlockStart instanceof PsiSwitchLabelStatement) || nextCaseBlockStart == caseStatement) return info.prohibitMove();
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
    LineRange range2 = createRange(editor.getDocument(), firstToMove2, lastToMove2);
    if (range2 == null) return info.prohibitMove();
    info.toMove2 = range2;
    return true;
  }

  // returns PsiSwitchLabelStatement starting this case block
  @NotNull
  private static PsiSwitchLabelStatement getThisCaseBlockStart(PsiSwitchLabelStatement element) {
    PsiElement tmp;
    while ((tmp = PsiTreeUtil.skipWhitespacesBackward(element)) instanceof PsiSwitchLabelStatement) {
      element = (PsiSwitchLabelStatement)tmp;
    }
    return element;
  }

  // returns PsiSwitchLabelStatement starting next case block, or switch block's closing brace, if there is no next case block
  @NotNull
  private static PsiElement getNextCaseBlockStart(PsiSwitchLabelStatement element) {
    PsiElement result = element;
    PsiElement tmp;
    while ((tmp = PsiTreeUtil.skipWhitespacesForward(result)) instanceof PsiSwitchLabelStatement) {
      result = tmp;
    }
    tmp = PsiTreeUtil.getNextSiblingOfType(result, PsiSwitchLabelStatement.class);
    return tmp == null ? result.getParent().getLastChild() : tmp;
  }

  @Nullable
  private static LineRange createRange(@NotNull Document document, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
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

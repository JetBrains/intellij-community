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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public abstract class StatementUpDownMover {
  public static final ExtensionPointName<StatementUpDownMover> STATEMENT_UP_DOWN_MOVER_EP =
    ExtensionPointName.create("com.intellij.statementUpDownMover");

  public static class MoveInfo extends UserDataHolderBase {
    /**
     * Source line range
     */
    public LineRange toMove;

    /**
     * Target line range, or {@code null} if move not available
     *
     * @see #prohibitMove()
     */
    public LineRange toMove2;

    public RangeMarker range1;
    public RangeMarker range2;

    public boolean indentSource;
    public boolean indentTarget = true;

    /**
     * Use this method in {@link StatementUpDownMover#checkAvailable(Editor, PsiFile, StatementUpDownMover.MoveInfo, boolean)}.
     *
     * @return true to suppress further movers processing
     */
    public final boolean prohibitMove() {
      toMove2 = null;
      return true;
    }
  }

  public abstract boolean checkAvailable(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down);

  public void beforeMove(@NotNull Editor editor, @NotNull MoveInfo info, boolean down) { }

  public void afterMove(@NotNull Editor editor, @NotNull PsiFile file, @NotNull MoveInfo info, boolean down) { }

  public static int getLineStartSafeOffset(@NotNull Document document, int line) {
    return line == document.getLineCount() ? document.getTextLength() : document.getLineStartOffset(line);
  }

  @NotNull
  protected static LineRange getLineRangeFromSelection(@NotNull Editor editor) {
    int startLine;
    int endLine;
    SelectionModel selectionModel = editor.getSelectionModel();
    LineRange range;
    if (selectionModel.hasSelection()) {
      startLine = editor.offsetToLogicalPosition(selectionModel.getSelectionStart()).line;
      LogicalPosition endPos = editor.offsetToLogicalPosition(selectionModel.getSelectionEnd());
      endLine = endPos.column == 0 ? endPos.line : endPos.line+1;
      range = new LineRange(startLine, endLine);
    }
    else {
      startLine = editor.getCaretModel().getLogicalPosition().line;
      endLine = startLine+1;
      range = new LineRange(startLine, endLine);
    }
    return range;
  }

  @Nullable
  protected static Pair<PsiElement, PsiElement> getElementRange(@NotNull Editor editor, @NotNull PsiFile file, @NotNull LineRange range) {
    int startOffset = editor.logicalPositionToOffset(new LogicalPosition(range.startLine, 0));
    PsiElement startingElement = firstNonWhiteElement(startOffset, file, true);
    if (startingElement == null) return null;
    int endOffset = editor.logicalPositionToOffset(new LogicalPosition(range.endLine, 0)) -1;

    PsiElement endingElement = firstNonWhiteElement(endOffset, file, false);
    if (endingElement == null) return null;
    if (PsiTreeUtil.isAncestor(startingElement, endingElement, false) ||
        startingElement.getTextRange().getEndOffset() <= endingElement.getTextRange().getStartOffset()) {
      return Pair.create(startingElement, endingElement);
    }
    if (PsiTreeUtil.isAncestor(endingElement, startingElement, false)) {
      return Pair.create(startingElement, endingElement);
    }
    return null;
  }

  @Nullable
  protected static PsiElement firstNonWhiteElement(int offset, @NotNull PsiFile file, boolean lookRight) {
    ASTNode leafElement = file.getNode().findLeafElementAt(offset);
    return leafElement == null ? null : firstNonWhiteElement(leafElement.getPsi(), lookRight);
  }

  @Nullable
  protected static PsiElement firstNonWhiteElement(PsiElement element, boolean lookRight) {
    if (element instanceof PsiWhiteSpace) {
      element = lookRight ? element.getNextSibling() : element.getPrevSibling();
    }
    return element;
  }
}
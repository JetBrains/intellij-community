/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class MoverWrapper {
  protected final boolean myIsDown;
  private final StatementUpDownMover myMover;
  private final StatementUpDownMover.MoveInfo myInfo;

  protected MoverWrapper(@NotNull final StatementUpDownMover mover, @NotNull final StatementUpDownMover.MoveInfo info, final boolean isDown) {
    myMover = mover;
    myIsDown = isDown;

    myInfo = info;
  }

  public StatementUpDownMover.MoveInfo getInfo() {
    return myInfo;
  }

  public final void move(Editor editor, final PsiFile file) {
    if (myInfo.toMove2 == null) {
      return;
    }
    myMover.beforeMove(editor, myInfo, myIsDown);
    final Document document = editor.getDocument();
    final int start = StatementUpDownMover.getLineStartSafeOffset(document, myInfo.toMove.startLine);
    final int end = StatementUpDownMover.getLineStartSafeOffset(document, myInfo.toMove.endLine);
    myInfo.range1 = document.createRangeMarker(start, end);

    String textToInsert = document.getCharsSequence().subSequence(start, end).toString();
    if (!StringUtil.endsWithChar(textToInsert,'\n')) textToInsert += '\n';

    final int start2 = document.getLineStartOffset(myInfo.toMove2.startLine);
    final int end2 = StatementUpDownMover.getLineStartSafeOffset(document,myInfo.toMove2.endLine);
    String textToInsert2 = document.getCharsSequence().subSequence(start2, end2).toString();
    if (!StringUtil.endsWithChar(textToInsert2,'\n')) textToInsert2 += '\n';
    myInfo.range2 = document.createRangeMarker(start2, end2);
    if (myInfo.range1.getStartOffset() < myInfo.range2.getStartOffset()) {
      myInfo.range1.setGreedyToLeft(true);
      myInfo.range1.setGreedyToRight(false);
      myInfo.range2.setGreedyToLeft(true);
      myInfo.range2.setGreedyToRight(true);
    }
    else {
      myInfo.range1.setGreedyToLeft(true);
      myInfo.range1.setGreedyToRight(true);
      myInfo.range2.setGreedyToLeft(true);
      myInfo.range2.setGreedyToRight(false);
    }

    final CaretModel caretModel = editor.getCaretModel();
    final int caretRelativePos = caretModel.getOffset() - start;
    final SelectionModel selectionModel = editor.getSelectionModel();
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();
    final boolean hasSelection = selectionModel.hasSelection();

    // to prevent flicker
    caretModel.moveToOffset(0);

    // There is a possible case that the user performs, say, method move. It's also possible that one (or both) of moved methods
    // are folded. We want to preserve their states then. The problem is that folding processing is based on PSI element pointers
    // and the pointers behave as following during move up/down:
    //     method1() {}
    //     method2() {}
    // Pointer for the fold region from method1 points to 'method2()' now and vice versa (check range markers processing on
    // document change for further information). I.e. information about fold regions statuses holds the data swapped for
    // 'method1' and 'method2'. Hence, we want to apply correct 'collapsed' status.
    FoldRegion topRegion = null;
    FoldRegion bottomRegion = null;
    for (FoldRegion foldRegion : editor.getFoldingModel().getAllFoldRegions()) {
      if (!foldRegion.isValid() || (!contains(myInfo.range1, foldRegion) && !contains(myInfo.range2, foldRegion))) {
        continue;
      }
      if (contains(myInfo.range1, foldRegion) && !contains(topRegion, foldRegion)) {
        topRegion = foldRegion;
      }
      else if (contains(myInfo.range2, foldRegion) && !contains(bottomRegion, foldRegion)) {
        bottomRegion = foldRegion;
      }
    }

    document.insertString(myInfo.range1.getStartOffset(), textToInsert2);
    document.deleteString(myInfo.range1.getStartOffset()+textToInsert2.length(), myInfo.range1.getEndOffset());

    document.insertString(myInfo.range2.getStartOffset(), textToInsert);
    int s = myInfo.range2.getStartOffset() + textToInsert.length();
    int e = myInfo.range2.getEndOffset();
    if (e > s) {
      document.deleteString(s, e);
    }

    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    // Swap fold regions status if necessary.
    if (topRegion != null && bottomRegion != null) {
      final FoldRegion finalTopRegion = topRegion;
      final FoldRegion finalBottomRegion = bottomRegion;
      editor.getFoldingModel().runBatchFoldingOperation(new Runnable() {
        @Override
        public void run() {
          boolean topExpanded = finalTopRegion.isExpanded();
          finalTopRegion.setExpanded(finalBottomRegion.isExpanded());
          finalBottomRegion.setExpanded(topExpanded);
        }
      });
    }

    if (hasSelection) {
      restoreSelection(editor, selectionStart, selectionEnd, start, myInfo.range2.getStartOffset());
    }

    caretModel.moveToOffset(myInfo.range2.getStartOffset() + caretRelativePos);
    if (myInfo.indentTarget) {
      indentLinesIn(editor, file, document, project, myInfo.range2);
    }
    if (myInfo.indentSource) {
      indentLinesIn(editor, file, document, project, myInfo.range1);
    }

    myMover.afterMove(editor, file, myInfo, myIsDown);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  /**
   * Allows to check if text range defined by the given range marker completely contains text range of the given fold region.
   *
   * @param rangeMarker   range marker to check
   * @param foldRegion    fold region to check
   * @return              <code>true</code> if text range defined by the given range marker completely contains text range
   *                      of the given fold region; <code>false</code> otherwise
   */
  private static boolean contains(@NotNull RangeMarker rangeMarker, @NotNull FoldRegion foldRegion) {
    return rangeMarker.getStartOffset() <= foldRegion.getStartOffset() && rangeMarker.getEndOffset() >= foldRegion.getEndOffset();
  }

  /**
   * Allows to check if given <code>'region2'</code> is nested to <code>'region1'</code>
   *
   * @param region1   'outer' region candidate
   * @param region2   'inner' region candidate
   * @return          <code>true</code> if 'region2' is nested to 'region1'; <code>false</code> otherwise
   */
  private static boolean contains(@Nullable FoldRegion region1, @NotNull FoldRegion region2) {
    if (region1 == null) {
      return false;
    }
    return region1.getStartOffset() <= region2.getStartOffset() && region1.getEndOffset() >= region2.getEndOffset();
  }

  private static void indentLinesIn(final Editor editor, final PsiFile file, final Document document, final Project project, RangeMarker range) {
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    int line1 = editor.offsetToLogicalPosition(range.getStartOffset()).line;
    int line2 = editor.offsetToLogicalPosition(range.getEndOffset()).line;

    while (!lineContainsNonSpaces(document, line1) && line1 < line2) line1++;
    while (!lineContainsNonSpaces(document, line2) && line1 < line2) line2--;

    final FileViewProvider provider = file.getViewProvider();
    PsiFile rootToAdjustIndentIn = provider.getPsi(provider.getBaseLanguage());
    codeStyleManager.adjustLineIndent(rootToAdjustIndentIn, new TextRange(document.getLineStartOffset(line1), document.getLineStartOffset(line2)));
  }

  private static boolean lineContainsNonSpaces(final Document document, final int line) {
    if (line >= document.getLineCount()) {
      return false;
    }
    int lineStartOffset = document.getLineStartOffset(line);
    int lineEndOffset = document.getLineEndOffset(line);
    @NonNls String text = document.getCharsSequence().subSequence(lineStartOffset, lineEndOffset).toString();
    return text.trim().length() != 0;
  }

  private static void restoreSelection(final Editor editor, final int selectionStart, final int selectionEnd, final int moveOffset, int insOffset) {
    final int selectionRelativeOffset = selectionStart - moveOffset;
    int newSelectionStart = insOffset + selectionRelativeOffset;
    int newSelectionEnd = newSelectionStart + selectionEnd - selectionStart;
    editor.getSelectionModel().setSelection(newSelectionStart, newSelectionEnd);
  }
}

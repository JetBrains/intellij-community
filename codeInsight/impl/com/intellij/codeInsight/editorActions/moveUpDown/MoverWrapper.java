package com.intellij.codeInsight.editorActions.moveUpDown;

import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

class MoverWrapper {
  protected final boolean myIsDown;
  private StatementUpDownMover myMover;
  private StatementUpDownMover.MoveInfo myInfo;

  protected MoverWrapper(@NotNull final StatementUpDownMover mover, @NotNull final StatementUpDownMover.MoveInfo info, final boolean isDown) {
    myMover = mover;
    myIsDown = isDown;

    myInfo = info;
  }

  public StatementUpDownMover.MoveInfo getInfo() {
    return myInfo;
  }

  public final void move(Editor editor, final PsiFile file) {
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

    document.insertString(myInfo.range1.getStartOffset(), textToInsert2);
    document.deleteString(myInfo.range1.getStartOffset()+textToInsert2.length(), myInfo.range1.getEndOffset());

    document.insertString(myInfo.range2.getStartOffset(), textToInsert);
    document.deleteString(myInfo.range2.getStartOffset()+textToInsert.length(), myInfo.range2.getEndOffset());

    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (hasSelection) {
      restoreSelection(editor, selectionStart, selectionEnd, start, myInfo.range2.getStartOffset());
    }

    caretModel.moveToOffset(myInfo.range2.getStartOffset() + caretRelativePos);
    indentLinesIn(editor, file, document, project, myInfo.range2);
    if (myInfo.indentSource) {
      indentLinesIn(editor, file, document, project, myInfo.range1);
    }

    myMover.afterMove(editor, file, myInfo, myIsDown);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  private static void indentLinesIn(final Editor editor, final PsiFile file, final Document document, final Project project, RangeMarker range) {
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    int line1 = editor.offsetToLogicalPosition(range.getStartOffset()).line;
    int line2 = editor.offsetToLogicalPosition(range.getEndOffset()).line;

    if (PsiUtil.isInJspFile(file)) {
      // This version is slow because of each moved line cause commit
      // and right now we unable to fix JSP formatter quickly
      // TODO: remove this code
      for (int line = line1; line <= line2; line++) {
        if (lineContainsNonSpaces(document, line)) {
          int lineStart = document.getLineStartOffset(line);
          codeStyleManager.adjustLineIndent(document, lineStart);
        }
      }
    }
    else {
      while (!lineContainsNonSpaces(document, line1) && line1 <= line2) line1++;
      while (!lineContainsNonSpaces(document, line2) && line2 > line1) line2--;

      try {
        final FileViewProvider provider = file.getViewProvider();
        PsiFile rootToAdjustIndentIn = provider.getPsi(provider.getBaseLanguage());
        codeStyleManager.adjustLineIndent(rootToAdjustIndentIn, new TextRange(document.getLineStartOffset(line1), document.getLineStartOffset(line2)));
      }
      catch (IncorrectOperationException ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  private static boolean lineContainsNonSpaces(final Document document, final int line) {
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

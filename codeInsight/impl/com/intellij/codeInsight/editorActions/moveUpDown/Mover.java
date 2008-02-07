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

abstract class Mover {
  protected final boolean isDown;
  @NotNull protected LineRange toMove;
  protected LineRange toMove2; // can be null if the move is illegal
  protected RangeMarker range1;
  protected RangeMarker range2;
  protected boolean indentSource;

  protected Mover(final boolean isDown) {
    this.isDown = isDown;
  }

  /**
   * @return false if this mover is unable to find a place to move stuff at,
   * otherwise, initialize fields and returns true
   */
  protected abstract boolean checkAvailable(Editor editor, PsiFile file);

  protected void beforeMove(final Editor editor) {

  }
  protected void afterMove(final Editor editor, final PsiFile file) {

  }

  public final void move(Editor editor, final PsiFile file) {
    beforeMove(editor);
    final Document document = editor.getDocument();
    final int start = getLineStartSafeOffset(document, toMove.startLine);
    final int end = getLineStartSafeOffset(document, toMove.endLine);
    range1 = document.createRangeMarker(start, end);

    String textToInsert = document.getCharsSequence().subSequence(start, end).toString();
    if (!StringUtil.endsWithChar(textToInsert,'\n')) textToInsert += '\n';

    final int start2 = document.getLineStartOffset(toMove2.startLine);
    final int end2 = getLineStartSafeOffset(document,toMove2.endLine);
    String textToInsert2 = document.getCharsSequence().subSequence(start2, end2).toString();
    if (!StringUtil.endsWithChar(textToInsert2,'\n')) textToInsert2 += '\n';
    range2 = document.createRangeMarker(start2, end2);
    if (range1.getStartOffset() < range2.getStartOffset()) {
      range1.setGreedyToLeft(true);
      range1.setGreedyToRight(false);
      range2.setGreedyToLeft(true);
      range2.setGreedyToRight(true);
    }
    else {
      range1.setGreedyToLeft(true);
      range1.setGreedyToRight(true);
      range2.setGreedyToLeft(true);
      range2.setGreedyToRight(false);
    }

    final CaretModel caretModel = editor.getCaretModel();
    final int caretRelativePos = caretModel.getOffset() - start;
    final SelectionModel selectionModel = editor.getSelectionModel();
    final int selectionStart = selectionModel.getSelectionStart();
    final int selectionEnd = selectionModel.getSelectionEnd();
    final boolean hasSelection = selectionModel.hasSelection();

    // to prevent flicker
    caretModel.moveToOffset(0);

    document.insertString(range1.getStartOffset(), textToInsert2);
    document.deleteString(range1.getStartOffset()+textToInsert2.length(), range1.getEndOffset());

    document.insertString(range2.getStartOffset(), textToInsert);
    document.deleteString(range2.getStartOffset()+textToInsert.length(), range2.getEndOffset());

    final Project project = file.getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (hasSelection) {
      restoreSelection(editor, selectionStart, selectionEnd, start, range2.getStartOffset());
    }

    caretModel.moveToOffset(range2.getStartOffset() + caretRelativePos);
    indentLinesIn(editor, file, document, project, range2);
    if (indentSource) {
      indentLinesIn(editor, file, document, project, range1);
    }

    afterMove(editor, file);
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

  protected static int getLineStartSafeOffset(final Document document, int line) {
    if (line == document.getLineCount()) return document.getTextLength();
    return document.getLineStartOffset(line);
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

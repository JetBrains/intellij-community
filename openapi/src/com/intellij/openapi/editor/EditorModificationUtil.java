/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

public class EditorModificationUtil {
  private EditorModificationUtil() {}

  public static void deleteSelectedText(Editor editor) {
    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasBlockSelection()) deleteBlockSelection(editor);
    if(!selectionModel.hasSelection()) return;

    int selectionStart = selectionModel.getSelectionStart();
    int selectionEnd = selectionModel.getSelectionEnd();

    editor.getCaretModel().moveToOffset(selectionStart);
    selectionModel.removeSelection();
    editor.getDocument().deleteString(selectionStart, selectionEnd);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  public static void deleteBlockSelection(Editor editor) {
    SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasBlockSelection()) return;

    int[] starts = selectionModel.getBlockSelectionStarts();
    int[] ends = selectionModel.getBlockSelectionEnds();

    for (int i = starts.length - 1; i >= 0; i--) {
      editor.getDocument().deleteString(starts[i], ends[i]);
    }

    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  public static void insertStringAtCaret(Editor editor, String s) {
    insertStringAtCaret(editor, s, false, true);
  }

  public static int insertStringAtCaret(Editor editor, String s, boolean toProcessOverwriteMode, boolean toMoveCaret) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      editor.getCaretModel().moveToOffset(selectionModel.getSelectionStart());
    }

    int oldOffset = editor.getCaretModel().getOffset();

    int afterLineEnd = calcAfterLineEnd(editor);
    if (afterLineEnd > 0) {
      StringBuffer buf = new StringBuffer();
      for (int i = 0; i < afterLineEnd; i++) {
        buf.append(' ');
      }
      buf.append(s);
      s = buf.toString();
    }

    if (editor.isInsertMode() || !toProcessOverwriteMode) {
      if (selectionModel.hasSelection()) {
        editor.getDocument().replaceString(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), s);
      } else {
        editor.getDocument().insertString(oldOffset, s);
      }
    } else {
      deleteSelectedText(editor);
      Document document = editor.getDocument();
      int lineNumber = editor.getCaretModel().getLogicalPosition().line;
      if (lineNumber >= document.getLineCount()){
        return insertStringAtCaret(editor, s, false, toMoveCaret);
      }

      int endOffset = document.getLineEndOffset(lineNumber);
      document.replaceString(oldOffset, Math.min(endOffset, oldOffset + s.length()), s);
    }

    int offset = oldOffset + s.length();
    if (toMoveCaret){
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      selectionModel.removeSelection();
    }

    return offset;
  }

  public static TextRange pasteFromClipboard(Editor editor) {
    return pasteFromTransferrable(getClipboardContent(editor), editor);
  }

  public static TextRange pasteFromTransferrable(Transferable content, Editor editor) {
    if (content != null) {
      try {
        String s = (String) content.getTransferData(DataFlavor.stringFlavor);
        s = StringUtil.convertLineSeparators(s, "\n");
        int caretOffset = editor.getCaretModel().getOffset();
        insertStringAtCaret(editor, s, false, true);
        return new TextRange(caretOffset, caretOffset + s.length());
      } catch (Exception exception) {
        editor.getComponent().getToolkit().beep();
      }
    }

    return null;
  }

  private static Transferable getClipboardContent(Editor editor) {
    Transferable content;
    Project project = editor.getProject();

    if (project != null) {
      content  = CopyPasteManager.getInstance().getContents();
    } else {
      Clipboard clipboard = editor.getComponent().getToolkit().getSystemClipboard();
      content = clipboard.getContents(editor.getComponent());
    }
    return content;
  }

  public static void pasteFromClipboardAsBlock(Editor editor) {
    Transferable content = getClipboardContent(editor);

    if (content != null) {
      try {
        int selectedLinesCount = 0;
        final SelectionModel selectionModel = editor.getSelectionModel();
        if (selectionModel.hasBlockSelection()) {
          final LogicalPosition start = selectionModel.getBlockStart();
          final LogicalPosition end = selectionModel.getBlockEnd();
          LogicalPosition caret = new LogicalPosition(Math.min(start.line, end.line), Math.min(start.column, end.column));
          selectedLinesCount = Math.abs(end.line - start.line);

          deleteSelectedText(editor);
          editor.getCaretModel().moveToLogicalPosition(caret);
        }

        LogicalPosition caretToRestore = editor.getCaretModel().getLogicalPosition();
        String s = (String) content.getTransferData(DataFlavor.stringFlavor);
        s = StringUtil.convertLineSeparators(s, "\n");
        String[] lines = LineTokenizer.tokenize(s.toCharArray(), false);
        if (lines.length > 1 || selectedLinesCount <= 1) {
          for (int i = 0; i < lines.length; i++) {
            insertStringAtCaret(editor, lines[i], false, false);
            editor.getCaretModel().moveCaretRelatively(0, 1, false, false, true);
          }
        } else {
          for (int i = 0; i <= selectedLinesCount; i++) {
            insertStringAtCaret(editor, s, false, false);
            editor.getCaretModel().moveCaretRelatively(0, 1, false, false, true);
          }
        }
        editor.getCaretModel().moveToLogicalPosition(caretToRestore);
      } catch (Exception exception) {
        editor.getComponent().getToolkit().beep();
      }
    }
  }

  public static int calcAfterLineEnd(Editor editor) {
    Document document = editor.getDocument();
    int columnNumber = editor.getCaretModel().getLogicalPosition().column;
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    if (lineNumber >= document.getLineCount()) {
      return columnNumber;
    }
    int lineEndOffset = document.getLineEndOffset(lineNumber);
    int lineEndColumnNumber = editor.offsetToLogicalPosition(lineEndOffset).column;
    return columnNumber - lineEndColumnNumber;
  }
}

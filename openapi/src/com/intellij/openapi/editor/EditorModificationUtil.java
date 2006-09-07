/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.MockDocumentEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

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

    String filler = calcStringToFillVitualSpace(editor);
    if (filler.length() > 0) {
      s = filler + s;
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

  @Nullable
  public static TextRange pasteFromClipboard(Editor editor) {
    return pasteFromTransferrable(getClipboardContent(editor), editor);
  }

  @Nullable
  public static TextRange pasteFromTransferrable(Transferable content, Editor editor) {
    if (content != null) {
      try {
        String s = getStringContent(content);

        int caretOffset = editor.getCaretModel().getOffset();
        insertStringAtCaret(editor, s, false, true);
        return new TextRange(caretOffset, caretOffset + s.length());
      } catch (Exception exception) {
        editor.getComponent().getToolkit().beep();
      }
    }

    return null;
  }

  private static String getStringContent(final Transferable content) throws UnsupportedFlavorException, IOException {
    RawText raw = null;
    try {
      raw = (RawText)content.getTransferData(RawText.FLAVOR);
    }
    catch (UnsupportedFlavorException e) {
      // OK. raw will be null and we'll get plain string
    }
    catch (IOException e) {
      // OK. raw will be null and we'll get plain string
    }

    String s;
    if (raw != null) {
      s = raw.rawText;
    }
    else {
      s = (String)content.getTransferData(DataFlavor.stringFlavor);
    }

    s = StringUtil.convertLineSeparators(s, "\n");
    return s;
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
          assert start != null;
          assert end != null;
          LogicalPosition caret = new LogicalPosition(Math.min(start.line, end.line), Math.min(start.column, end.column));
          selectedLinesCount = Math.abs(end.line - start.line);

          deleteSelectedText(editor);
          editor.getCaretModel().moveToLogicalPosition(caret);
        }

        LogicalPosition caretToRestore = editor.getCaretModel().getLogicalPosition();
        String s = getStringContent(content);

        String[] lines = LineTokenizer.tokenize(s.toCharArray(), false);
        if (lines.length > 1 || selectedLinesCount <= 1) {
          for (String line : lines) {
            insertStringAtCaret(editor, line, false, false);
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

  public static String calcStringToFillVitualSpace(Editor editor) {
    int afterLineEnd = calcAfterLineEnd(editor);
    if (afterLineEnd > 0) {
      final Project project = editor.getProject();
      StringBuffer buf = new StringBuffer();
      final Document doc = editor.getDocument();
      final int caretOffset = editor.getCaretModel().getOffset();
      boolean atLineStart = caretOffset >= doc.getTextLength() || doc.getLineStartOffset(doc.getLineNumber(caretOffset)) == caretOffset;
      if (atLineStart && project != null) {
        String properIndent = CodeStyleManager.getInstance(project).getLineIndent(editor);
        if (properIndent != null) {
          int tabSize = editor.getSettings().getTabSize(project);
          for (int i = 0; i < properIndent.length(); i++) {
            if (properIndent.charAt(i) == ' ') {
              afterLineEnd--;
            }
            else if (properIndent.charAt(i) == '\t') {
              if (afterLineEnd < tabSize) {
                break;
              }
              afterLineEnd -= tabSize;
            }
            buf.append(properIndent.charAt(i));
            if (afterLineEnd == 0) break;
          }
        }
      }

      for (int i = 0; i < afterLineEnd; i++) {
        buf.append(' ');
      }

      return buf.toString();
    }

    return "";
  }

  public static void typeInStringAtCaretHonorBlockSelection(final Editor editor, final String str, final boolean toProcessOverwriteMode)
    throws ReadOnlyFragmentModificationException
  {
    Document doc = editor.getDocument();
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasBlockSelection()) {
      RangeMarker guard = selectionModel.getBlockSelectionGuard();
      if (guard != null) {
        DocumentEvent evt = new MockDocumentEvent(doc, editor.getCaretModel().getOffset());
        ReadOnlyFragmentModificationException e = new ReadOnlyFragmentModificationException(evt, guard);
        EditorActionManager.getInstance().getReadonlyFragmentModificationHandler().handle(e);
      }
      else {
        final LogicalPosition start = selectionModel.getBlockStart();
        final LogicalPosition end = selectionModel.getBlockEnd();
        assert start != null;
        assert end != null;

        int column = Math.min(start.column, end.column);
        int startLine = Math.min(start.line, end.line);
        int endLine = Math.max(start.line, end.line);
        deleteBlockSelection(editor);
        for (int i = startLine; i <= endLine; i++) {
          editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(i, column));
          insertStringAtCaret(editor, str, toProcessOverwriteMode, true);
        }
        selectionModel.setBlockSelection(new LogicalPosition(startLine, column + str.length()),
                                         new LogicalPosition(endLine, column + str.length()));
      }
    }
    else {
      insertStringAtCaret(editor, str, toProcessOverwriteMode, true);
    }
  }
}

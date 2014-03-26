/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.MockDocumentEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class EditorModificationUtil {
  private EditorModificationUtil() { }

  public static void deleteSelectedText(Editor editor) {
    deleteSelectedTextNoScrolling(editor);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  private static void deleteSelectedTextNoScrolling(Editor editor) {
    SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasBlockSelection()) deleteBlockSelection(editor);
    if(!selectionModel.hasSelection()) return;

    int selectionStart = selectionModel.getSelectionStart();
    int selectionEnd = selectionModel.getSelectionEnd();

    VisualPosition selectionStartPosition = selectionModel.getSelectionStartPosition();
    if (editor.isColumnMode() && editor.getCaretModel().supportsMultipleCarets() && selectionStartPosition != null) {
      editor.getCaretModel().moveToVisualPosition(selectionStartPosition);
    }
    else {
      editor.getCaretModel().moveToOffset(selectionStart);
    }
    selectionModel.removeSelection();
    editor.getDocument().deleteString(selectionStart, selectionEnd);
  }

  public static void deleteSelectedTextForAllCarets(@NotNull final Editor editor) {
    editor.getCaretModel().runForEachCaret(new CaretAction() {
      @Override
      public void perform(Caret caret) {
        deleteSelectedTextNoScrolling(editor);
      }
    });
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  public static void deleteBlockSelection(Editor editor) {
    SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasBlockSelection()) return;

    LogicalPosition blockStart = selectionModel.getBlockStart();
    LogicalPosition blockEnd = selectionModel.getBlockEnd();
    if (blockStart == null || blockEnd == null) {
      return;
    }

    int startLine = blockStart.line;
    int endLine = blockEnd.line;

    int[] starts = selectionModel.getBlockSelectionStarts();
    int[] ends = selectionModel.getBlockSelectionEnds();

    for (int i = starts.length - 1; i >= 0; i--) {
      editor.getDocument().deleteString(starts[i], ends[i]);
    }

    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);

    zeroWidthBlockSelectionAtCaretColumn(editor, startLine, endLine);
  }

  private static void zeroWidthBlockSelectionAtCaretColumn(final Editor editor, final int startLine, final int endLine) {
    int caretColumn = editor.getCaretModel().getLogicalPosition().column;
    editor.getSelectionModel().setBlockSelection(new LogicalPosition(startLine, caretColumn), new LogicalPosition(endLine, caretColumn));
  }

  public static void insertStringAtCaret(Editor editor, @NotNull String s) {
    insertStringAtCaret(editor, s, false, true);
  }

  public static int insertStringAtCaret(Editor editor, @NotNull String s, boolean toProcessOverwriteMode, boolean toMoveCaret) {
    return insertStringAtCaret(editor, s, toProcessOverwriteMode, toMoveCaret, s.length());
  }

  public static int insertStringAtCaret(Editor editor, @NotNull String s, boolean toProcessOverwriteMode, boolean toMoveCaret, int caretShift) {
    int result = insertStringAtCaretNoScrolling(editor, s, toProcessOverwriteMode, toMoveCaret, caretShift);
    if (toMoveCaret) {
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
    return result;
  }

  private static int insertStringAtCaretNoScrolling(Editor editor, @NotNull String s, boolean toProcessOverwriteMode, boolean toMoveCaret, int caretShift) {
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasSelection()) {
      VisualPosition startPosition = selectionModel.getSelectionStartPosition();
      if (editor.isColumnMode() && editor.getCaretModel().supportsMultipleCarets() && startPosition != null) {
        editor.getCaretModel().moveToVisualPosition(startPosition);
      }
      else {
        editor.getCaretModel().moveToOffset(selectionModel.getSelectionStart(), true);
      }
    }

    // There is a possible case that particular soft wraps become hard wraps if the caret is located at soft wrap-introduced virtual
    // space, hence, we need to give editor a chance to react accordingly.
    editor.getSoftWrapModel().beforeDocumentChangeAtCaret();
    int oldOffset = editor.getCaretModel().getOffset();

    String filler = calcStringToFillVirtualSpace(editor);
    if (filler.length() > 0) {
      s = filler + s;
    }

    Document document = editor.getDocument();
    if (editor.isInsertMode() || !toProcessOverwriteMode) {
      if (selectionModel.hasSelection()) {
        oldOffset = selectionModel.getSelectionStart();
        document.replaceString(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd(), s);
      } else {
        document.insertString(oldOffset, s);
      }
    } else {
      deleteSelectedText(editor);
      int lineNumber = editor.getCaretModel().getLogicalPosition().line;
      if (lineNumber >= document.getLineCount()){
        return insertStringAtCaretNoScrolling(editor, s, false, toMoveCaret, s.length());
      }

      int endOffset = document.getLineEndOffset(lineNumber);
      document.replaceString(oldOffset, Math.min(endOffset, oldOffset + s.length()), s);
    }

    int offset = oldOffset + filler.length() + caretShift;
    if (toMoveCaret){
      editor.getCaretModel().moveToOffset(offset, true);
      selectionModel.removeSelection();
    }
    else if (editor.getCaretModel().getOffset() != oldOffset) { // handling the case when caret model tracks document changes
      editor.getCaretModel().moveToOffset(oldOffset);
    }

    return offset;
  }

  @Nullable
  public static TextRange pasteTransferable(final Editor editor, @Nullable Producer<Transferable> producer) {
    String text = getStringContent(producer);
    if (text == null) return null;

    if (editor.getCaretModel().supportsMultipleCarets()) {
      int caretCount = editor.getCaretModel().getCaretCount();
      if (caretCount == 1 && editor.isColumnMode()) {
        int pastedLineCount = LineTokenizer.calcLineCount(text, true);
        deleteSelectedText(editor);
        Caret caret = editor.getCaretModel().getPrimaryCaret();
        for (int i = 0; i < pastedLineCount - 1; i++) {
          caret = caret.clone(false);
          if (caret == null) {
            break;
          }
        }
        caretCount = editor.getCaretModel().getCaretCount();
      }
      final Iterator<String> segments = new ClipboardTextPerCaretSplitter().split(text, caretCount).iterator();
      editor.getCaretModel().runForEachCaret(new CaretAction() {
        @Override
        public void perform(Caret caret) {
          insertStringAtCaret(editor, segments.next(), false, true);
        }
      });
      return null;
    }
    else {
      int caretOffset = editor.getCaretModel().getOffset();
      insertStringAtCaret(editor, text, false, true);
      return new TextRange(caretOffset, caretOffset + text.length());
    }
  }

  public static void pasteTransferableAsBlock(Editor editor, @Nullable Producer<Transferable> producer) {
    String text = getStringContent(producer);
    if (text == null) return;

    int caretLine = editor.getCaretModel().getLogicalPosition().line;
    int originalCaretLine = caretLine;
    int selectedLinesCount = 0;

    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasBlockSelection()) {
      final LogicalPosition start = selectionModel.getBlockStart();
      final LogicalPosition end = selectionModel.getBlockEnd();
      assert start != null;
      assert end != null;
      LogicalPosition caret = new LogicalPosition(Math.min(start.line, end.line), Math.min(start.column, end.column));
      selectedLinesCount = Math.abs(end.line - start.line);
      caretLine = caret.line;

      deleteSelectedText(editor);
      editor.getCaretModel().moveToLogicalPosition(caret);
    }

    LogicalPosition caretToRestore = editor.getCaretModel().getLogicalPosition();

    String[] lines = LineTokenizer.tokenize(text.toCharArray(), false);
    if (lines.length > 1 || selectedLinesCount == 0) {
      int longestLineLength = 0;
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        longestLineLength = Math.max(longestLineLength, line.length());
        editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(caretLine + i, caretToRestore.column));
        insertStringAtCaret(editor, line, false, true);
      }
      caretToRestore = new LogicalPosition(originalCaretLine, caretToRestore.column + longestLineLength);
    }
    else {
      for (int i = 0; i <= selectedLinesCount; i++) {
        editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(caretLine + i, caretToRestore.column));
        insertStringAtCaret(editor, text, false, true);
      }
      caretToRestore = new LogicalPosition(originalCaretLine, caretToRestore.column + text.length());
    }

    editor.getCaretModel().moveToLogicalPosition(caretToRestore);
    zeroWidthBlockSelectionAtCaretColumn(editor, caretLine, caretLine + selectedLinesCount);
  }

  @Nullable
  private static String getStringContent(@Nullable Producer<Transferable> producer) {
    Transferable content = null;
    if (producer != null) {
      content = producer.produce();
    }
    else {
      CopyPasteManager manager = CopyPasteManager.getInstance();
      if (manager.areDataFlavorsAvailable(DataFlavor.stringFlavor)) {
        content = manager.getContents();
      }
    }
    if (content == null) return null;

    RawText raw = RawText.fromTransferable(content);
    if (raw != null) return raw.rawText;

    try {
      return (String)content.getTransferData(DataFlavor.stringFlavor);
    }
    catch (UnsupportedFlavorException ignore) { }
    catch (IOException ignore) { }

    return null;
  }

  /**
   * Calculates difference in columns between current editor caret position and end of the logical line fragment displayed
   * on a current visual line.
   *
   * @param editor    target editor
   * @return          difference in columns between current editor caret position and end of the logical line fragment displayed
   *                  on a current visual line
   */
  public static int calcAfterLineEnd(Editor editor) {
    Document document = editor.getDocument();
    CaretModel caretModel = editor.getCaretModel();
    LogicalPosition logicalPosition = caretModel.getLogicalPosition();
    int lineNumber = logicalPosition.line;
    int columnNumber = logicalPosition.column;
    if (lineNumber >= document.getLineCount()) {
      return columnNumber;
    }

    int caretOffset = caretModel.getOffset();
    int anchorLineEndOffset = document.getLineEndOffset(lineNumber);
    List<? extends SoftWrap> softWraps = editor.getSoftWrapModel().getSoftWrapsForLine(logicalPosition.line);
    for (SoftWrap softWrap : softWraps) {
      if (!editor.getSoftWrapModel().isVisible(softWrap)) {
        continue;
      }

      int softWrapOffset = softWrap.getStart();
      if (softWrapOffset == caretOffset) {
        // There are two possible situations:
        //     *) caret is located on a visual line before soft wrap-introduced line feed;
        //     *) caret is located on a visual line after soft wrap-introduced line feed;
        VisualPosition position = editor.offsetToVisualPosition(caretOffset - 1);
        VisualPosition visualCaret = caretModel.getVisualPosition();
        if (position.line == visualCaret.line) {
          return visualCaret.column - position.column - 1;
        }
      }
      if (softWrapOffset > caretOffset) {
        anchorLineEndOffset = softWrapOffset;
        break;
      }

      // Same offset corresponds to all soft wrap-introduced symbols, however, current method should behave differently in
      // situations when the caret is located just before the soft wrap and at the next visual line.
      if (softWrapOffset == caretOffset) {
        boolean visuallyBeforeSoftWrap = caretModel.getVisualPosition().line < editor.offsetToVisualPosition(caretOffset).line;
        if (visuallyBeforeSoftWrap) {
          anchorLineEndOffset = softWrapOffset;
          break;
        }
      }
    }

    int lineEndColumnNumber = editor.offsetToLogicalPosition(anchorLineEndOffset).column;
    return columnNumber - lineEndColumnNumber;
  }

  public static String calcStringToFillVirtualSpace(Editor editor) {
    int afterLineEnd = calcAfterLineEnd(editor);
    if (afterLineEnd > 0) {
      return calcStringToFillVirtualSpace(editor, afterLineEnd);
    }

    return "";
  }

  public static String calcStringToFillVirtualSpace(Editor editor, int afterLineEnd) {
    final Project project = editor.getProject();
    StringBuilder buf = new StringBuilder();
    final Document doc = editor.getDocument();
    final int caretOffset = editor.getCaretModel().getOffset();
    boolean atLineStart = caretOffset >= doc.getTextLength() || doc.getLineStartOffset(doc.getLineNumber(caretOffset)) == caretOffset;
    if (atLineStart && project != null) {
      int offset = editor.getCaretModel().getOffset();
      PsiDocumentManager.getInstance(project).commitDocument(doc); // Sync document and PSI before formatting.
      String properIndent = offset >= doc.getTextLength() ? "" : CodeStyleFacade.getInstance(project).getLineIndent(doc, offset);
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
        EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(doc).handle(e);
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

  public static void typeInStringAtCaretHonorMultipleCarets(final Editor editor, @NotNull final String str) {
    typeInStringAtCaretHonorMultipleCarets(editor, str, true, str.length());
  }

  public static void typeInStringAtCaretHonorMultipleCarets(final Editor editor, @NotNull final String str, final int caretShift) {
    typeInStringAtCaretHonorMultipleCarets(editor, str, true, caretShift);
  }

  public static void typeInStringAtCaretHonorMultipleCarets(final Editor editor, @NotNull final String str, final boolean toProcessOverwriteMode) {
    typeInStringAtCaretHonorMultipleCarets(editor, str, toProcessOverwriteMode, str.length());
  }

  /**
   * Inserts given string at each caret's position. Effective caret shift will be equal to <code>caretShift</code> for each caret.
   */
  public static void typeInStringAtCaretHonorMultipleCarets(final Editor editor, @NotNull final String str, final boolean toProcessOverwriteMode, final int caretShift)
    throws ReadOnlyFragmentModificationException
  {
    Document doc = editor.getDocument();
    final SelectionModel selectionModel = editor.getSelectionModel();
    if (selectionModel.hasBlockSelection()) {
      RangeMarker guard = selectionModel.getBlockSelectionGuard();
      if (guard != null) {
        DocumentEvent evt = new MockDocumentEvent(doc, editor.getCaretModel().getOffset());
        ReadOnlyFragmentModificationException e = new ReadOnlyFragmentModificationException(evt, guard);
        EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(doc).handle(e);
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
          insertStringAtCaret(editor, str, toProcessOverwriteMode, true, caretShift);
        }
        selectionModel.setBlockSelection(new LogicalPosition(startLine, column + str.length()),
                                         new LogicalPosition(endLine, column + str.length()));
      }
    }
    else {
      editor.getCaretModel().runForEachCaret(new CaretAction() {
        @Override
        public void perform(Caret caret) {
          insertStringAtCaretNoScrolling(editor, str, toProcessOverwriteMode, true, caretShift);
        }
      });
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }

  public static void moveAllCaretsRelatively(@NotNull Editor editor, final int caretShift) {
    editor.getCaretModel().runForEachCaret(new CaretAction() {
      @Override
      public void perform(Caret caret) {
        caret.moveToOffset(caret.getOffset() + caretShift);
      }
    });
  }

  /** @deprecated use {@link #pasteTransferable(Editor, Producer)} (to remove in IDEA 14) */
  @SuppressWarnings("UnusedDeclaration")
  public static TextRange pasteFromClipboard(Editor editor) {
    return pasteTransferable(editor, null);
  }

  /** @deprecated use {@link #pasteTransferable(Editor, Producer)} (to remove in IDEA 14) */
  @SuppressWarnings("SpellCheckingInspection,UnusedDeclaration")
  public static TextRange pasteFromTransferrable(final Transferable content, Editor editor) {
    return pasteTransferable(editor, new Producer<Transferable>() {
      @Nullable
      @Override
      public Transferable produce() {
        return content;
      }
    });
  }

  @SuppressWarnings("UnusedDeclaration")
  /** @deprecated use {@link #pasteTransferableAsBlock(Editor, Producer)} (to remove in IDEA 14) */
  public static void pasteFromClipboardAsBlock(Editor editor) {
    pasteTransferableAsBlock(editor, (Producer<Transferable>)null);
  }

  @SuppressWarnings("UnusedDeclaration")
  /** @deprecated use {@link #pasteTransferableAsBlock(Editor, Producer)} (to remove in IDEA 14) */
  public static void pasteTransferableAsBlock(Editor editor, @Nullable final Transferable content) {
    pasteTransferableAsBlock(editor, new Producer<Transferable>() {
      @Nullable
      @Override
      public Transferable produce() {
        return content;
      }
    });
  }
}

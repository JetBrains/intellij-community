/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.editor;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.textarea.TextComponentEditor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.Producer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkListener;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

public class EditorModificationUtil {
  private static final Key<ReadOnlyHint> READ_ONLY_VIEW_HINT_KEY = Key.create("READ_ONLY_VIEW_HINT_KEY");

  /**
   * @deprecated Use {@link #setReadOnlyHint(Editor, String)}
   */
  @Deprecated
  @SuppressWarnings("DeprecatedIsStillUsed")
  @ApiStatus.ScheduledForRemoval(inVersion = "2019.3")
  public static final Key<String> READ_ONLY_VIEW_MESSAGE_KEY = Key.create("READ_ONLY_VIEW_MESSAGE_KEY");

  private EditorModificationUtil() { }

  public static void deleteSelectedText(Editor editor) {
    SelectionModel selectionModel = editor.getSelectionModel();
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
    scrollToCaret(editor);
  }

  public static void deleteSelectedTextForAllCarets(@NotNull final Editor editor) {
    editor.getCaretModel().runForEachCaret(__ -> deleteSelectedText(editor));
  }

  public static void zeroWidthBlockSelectionAtCaretColumn(final Editor editor, final int startLine, final int endLine) {
    int caretColumn = editor.getCaretModel().getLogicalPosition().column;
    editor.getSelectionModel().setBlockSelection(new LogicalPosition(startLine, caretColumn), new LogicalPosition(endLine, caretColumn));
  }

  public static void insertStringAtCaret(Editor editor, @NotNull String s) {
    insertStringAtCaret(editor, s, false, true);
  }

  public static int insertStringAtCaret(Editor editor, @NotNull String s, boolean toProcessOverwriteMode) {
    return insertStringAtCaret(editor, s, toProcessOverwriteMode, s.length());
  }

  public static int insertStringAtCaret(Editor editor, @NotNull String s, boolean toProcessOverwriteMode, boolean toMoveCaret) {
    return insertStringAtCaret(editor, s, toProcessOverwriteMode, toMoveCaret, s.length());
  }

  public static int insertStringAtCaret(Editor editor, @NotNull String s, boolean toProcessOverwriteMode, int caretShift) {
    return insertStringAtCaret(editor, s, toProcessOverwriteMode, true, caretShift);
  }

  public static int insertStringAtCaret(Editor editor, @NotNull String s, boolean toProcessOverwriteMode, boolean toMoveCaret, int caretShift) {
    int result = insertStringAtCaretNoScrolling(editor, s, toProcessOverwriteMode, toMoveCaret, caretShift);
    if (toMoveCaret) {
      scrollToCaret(editor);
    }
    return result;
  }

  private static int insertStringAtCaretNoScrolling(Editor editor, @NotNull String s, boolean toProcessOverwriteMode, boolean toMoveCaret, int caretShift) {
    // There is a possible case that particular soft wraps become hard wraps if the caret is located at soft wrap-introduced virtual
    // space, hence, we need to give editor a chance to react accordingly.
    editor.getSoftWrapModel().beforeDocumentChangeAtCaret();
    int oldOffset = editor.getSelectionModel().getSelectionStart();

    String filler = editor.getSelectionModel().hasSelection() ? "" : calcStringToFillVirtualSpace(editor);
    if (filler.length() > 0) {
      s = filler + s;
    }

    Document document = editor.getDocument();
    SelectionModel selectionModel = editor.getSelectionModel();
    if (editor.isInsertMode() || !toProcessOverwriteMode) {
      if (selectionModel.hasSelection()) {
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
      editor.getCaretModel().moveToVisualPosition(editor.offsetToVisualPosition(offset, false, true));
      selectionModel.removeSelection();
    }
    else if (editor.getCaretModel().getOffset() != oldOffset) { // handling the case when caret model tracks document changes
      editor.getCaretModel().moveToOffset(oldOffset);
    }

    return offset;
  }

  public static void pasteTransferableAsBlock(Editor editor, @Nullable Supplier<? extends Transferable> producer) {
    Transferable content = getTransferable(producer);
    if (content == null) return;
    String text = getStringContent(content);
    if (text == null) return;

    int caretLine = editor.getCaretModel().getLogicalPosition().line;

    LogicalPosition caretToRestore = editor.getCaretModel().getLogicalPosition();

    String[] lines = LineTokenizer.tokenize(text.toCharArray(), false);
    int longestLineLength = 0;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      longestLineLength = Math.max(longestLineLength, line.length());
      editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(caretLine + i, caretToRestore.column));
      insertStringAtCaret(editor, line, false, true);
    }
    caretToRestore = new LogicalPosition(caretLine, caretToRestore.column + longestLineLength);

    editor.getCaretModel().moveToLogicalPosition(caretToRestore);
    zeroWidthBlockSelectionAtCaretColumn(editor, caretLine, caretLine);
  }

  @Nullable
  public static Transferable getContentsToPasteToEditor(@Nullable Producer<? extends Transferable> producer) {
    if (producer == null) {
      CopyPasteManager manager = CopyPasteManager.getInstance();
      return manager.areDataFlavorsAvailable(DataFlavor.stringFlavor) ? manager.getContents() : null;
    }
    else {
      return producer.produce();
    }
  } 

  @Nullable
  public static String getStringContent(@NotNull Transferable content) {
    RawText raw = RawText.fromTransferable(content);
    if (raw != null) return raw.rawText;

    try {
      return (String)content.getTransferData(DataFlavor.stringFlavor);
    }
    catch (UnsupportedFlavorException | IOException ignore) { }

    return null;
  }

  private static Transferable getTransferable(Supplier<? extends Transferable> producer) {
    Transferable content = null;
    if (producer != null) {
      content = producer.get();
    }
    else {
      CopyPasteManager manager = CopyPasteManager.getInstance();
      if (manager.areDataFlavorsAvailable(DataFlavor.stringFlavor)) {
        content = manager.getContents();
      }
    }
    return content;
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
      } else {
        EditorSettings editorSettings = editor.getSettings();
        boolean useTab = editorSettings.isUseTabCharacter(editor.getProject());
        if (useTab) {
          int tabSize = editorSettings.getTabSize(project);
          while (afterLineEnd >= tabSize) {
            buf.append('\t');
            afterLineEnd -= tabSize;
          }
        }
      }
    }

    for (int i = 0; i < afterLineEnd; i++) {
      buf.append(' ');
    }

    return buf.toString();
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
   * Inserts given string at each caret's position. Effective caret shift will be equal to {@code caretShift} for each caret.
   */
  public static void typeInStringAtCaretHonorMultipleCarets(final Editor editor, @NotNull final String str, final boolean toProcessOverwriteMode, final int caretShift)
    throws ReadOnlyFragmentModificationException
  {
    editor.getCaretModel().runForEachCaret(__ -> insertStringAtCaretNoScrolling(editor, str, toProcessOverwriteMode, true, caretShift));
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
  }

  public static void moveAllCaretsRelatively(@NotNull Editor editor, final int caretShift) {
    editor.getCaretModel().runForEachCaret(caret -> caret.moveToOffset(caret.getOffset() + caretShift));
  }

  public static void moveCaretRelatively(@NotNull Editor editor, final int caretShift) {
    CaretModel caretModel = editor.getCaretModel();
    caretModel.moveToOffset(caretModel.getOffset() + caretShift);
  }

  /**
   * This method is safe to run both in and out of {@link com.intellij.openapi.editor.CaretModel#runForEachCaret(CaretAction)} context.
   * It scrolls to primary caret in both cases, and, in the former case, avoids performing excessive scrolling in case of large number
   * of carets.
   */
  public static void scrollToCaret(@NotNull Editor editor) {
    if (editor.getCaretModel().getCurrentCaret() == editor.getCaretModel().getPrimaryCaret()) {
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
  }
  
  @NotNull
  public static List<CaretState> calcBlockSelectionState(@NotNull Editor editor, 
                                                         @NotNull LogicalPosition blockStart, @NotNull LogicalPosition blockEnd) {
    int startLine = Math.max(Math.min(blockStart.line, editor.getDocument().getLineCount() - 1), 0);
    int endLine = Math.max(Math.min(blockEnd.line, editor.getDocument().getLineCount() - 1), 0);
    int step = endLine < startLine ? -1 : 1;
    int count = 1 + Math.abs(endLine - startLine);
    List<CaretState> caretStates = new LinkedList<>();
    boolean hasSelection = false;
    for (int line = startLine, i = 0; i < count; i++, line += step) {
      int startColumn = blockStart.column;
      int endColumn = blockEnd.column;
      int lineEndOffset = editor.getDocument().getLineEndOffset(line);
      LogicalPosition lineEndPosition = editor.offsetToLogicalPosition(lineEndOffset);
      int lineWidth = lineEndPosition.column;
      if (startColumn > lineWidth && endColumn > lineWidth && !editor.isColumnMode()) {
        LogicalPosition caretPos = new LogicalPosition(line, Math.min(startColumn, endColumn));
        caretStates.add(new CaretState(caretPos,
                                       lineEndPosition,
                                       lineEndPosition));
      }
      else {
        LogicalPosition startPos = new LogicalPosition(line, editor.isColumnMode() ? startColumn : Math.min(startColumn, lineWidth));
        LogicalPosition endPos = new LogicalPosition(line, editor.isColumnMode() ? endColumn : Math.min(endColumn, lineWidth));
        int startOffset = editor.logicalPositionToOffset(startPos);
        int endOffset = editor.logicalPositionToOffset(endPos);
        caretStates.add(new CaretState(endPos, startPos, endPos));
        hasSelection |= startOffset != endOffset;
      }
    }
    if (hasSelection && !editor.isColumnMode()) { // filtering out lines without selection
      Iterator<CaretState> caretStateIterator = caretStates.iterator();
      while(caretStateIterator.hasNext()) {
        CaretState state = caretStateIterator.next();
        //noinspection ConstantConditions
        if (state.getSelectionStart().equals(state.getSelectionEnd())) {
          caretStateIterator.remove();
        }
      }
    }
    return caretStates;
  }

  public static boolean requestWriting(@NotNull Editor editor) {
    FileDocumentManager.WriteAccessStatus writeAccess =
      FileDocumentManager.getInstance().requestWritingStatus(editor.getDocument(), editor.getProject());
    if (!writeAccess.hasWriteAccess()) {
      HintManager.getInstance().showInformationHint(editor, writeAccess.getReadOnlyMessage());
      return false;
    }
    return true;
  }

  /**
   * @return true when not viewer
   *         false otherwise, additionally information hint with warning would be shown
   */
  public static boolean checkModificationAllowed(Editor editor) {
    if (!editor.isViewer()) return true;
    if (ApplicationManager.getApplication().isHeadlessEnvironment() || editor instanceof TextComponentEditor) return false;

    ReadOnlyHint hint = getReadOnlyHint(editor);
    HintManager.getInstance().showInformationHint(editor, hint.message, hint.linkListener);
    return false;
  }

  /**
   * @see #setReadOnlyHint(Editor, String, HyperlinkListener)
   */
  public static void setReadOnlyHint(@NotNull Editor editor, @Nullable String message) {
    setReadOnlyHint(editor, message, null);
  }

  /**
   * Change hint that is displayed on attempt to modify text when editor is in view mode.
   *
   * @param message      New hint message or {@code null} if default message should be used instead.
   * @param linkListener Callback for html hyperlinks that can be used in hint message.
   */
  public static void setReadOnlyHint(@NotNull Editor editor, @Nullable String message, @Nullable HyperlinkListener linkListener) {
    editor.putUserData(READ_ONLY_VIEW_HINT_KEY, message != null ? new ReadOnlyHint(message, linkListener) : null);
  }

  @NotNull
  private static ReadOnlyHint getReadOnlyHint(@NotNull Editor editor) {
    ReadOnlyHint hint = READ_ONLY_VIEW_HINT_KEY.get(editor);
    if (hint != null) return hint;

    String message = READ_ONLY_VIEW_MESSAGE_KEY.get(editor);
    if (message != null) return new ReadOnlyHint(message, null);

    return new ReadOnlyHint(EditorBundle.message("editing.viewer.hint"), null);
  }

  private static class ReadOnlyHint {

    @NotNull public final String message;
    @Nullable public final HyperlinkListener linkListener;

    private ReadOnlyHint(@NotNull String message, @Nullable HyperlinkListener linkListener) {
      this.message = message;
      this.linkListener = linkListener;
    }
  }
}

// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.textarea.TextComponentEditor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Producer;
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

public final class EditorModificationUtil extends EditorModificationUtilEx {
  private static final Key<ReadOnlyHint> READ_ONLY_VIEW_HINT_KEY = Key.create("READ_ONLY_VIEW_HINT_KEY");

  private EditorModificationUtil() { }


  public static void deleteSelectedTextForAllCarets(@NotNull final Editor editor) {
    editor.getCaretModel().runForEachCaret(__ -> deleteSelectedText(editor));
  }

  public static void zeroWidthBlockSelectionAtCaretColumn(final Editor editor, final int startLine, final int endLine) {
    int caretColumn = editor.getCaretModel().getLogicalPosition().column;
    editor.getSelectionModel().setBlockSelection(new LogicalPosition(startLine, caretColumn), new LogicalPosition(endLine, caretColumn));
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

    ReadOnlyHint hint = ObjectUtils.chooseNotNull(READ_ONLY_VIEW_HINT_KEY.get(editor), new ReadOnlyHint(EditorBundle.message("editing.viewer.hint"), null));
    HintManager.getInstance().showInformationHint(editor, hint.message, hint.linkListener);
    return false;
  }

  /**
   * @see #setReadOnlyHint(Editor, String, HyperlinkListener)
   */
  public static void setReadOnlyHint(@NotNull Editor editor, @Nullable @NlsContexts.HintText String message) {
    setReadOnlyHint(editor, message, null);
  }

  /**
   * Change hint that is displayed on attempt to modify text when editor is in view mode.
   *
   * @param message      New hint message or {@code null} if default message should be used instead.
   * @param linkListener Callback for html hyperlinks that can be used in hint message.
   */
  public static void setReadOnlyHint(@NotNull Editor editor, @Nullable @NlsContexts.HintText String message, @Nullable HyperlinkListener linkListener) {
    editor.putUserData(READ_ONLY_VIEW_HINT_KEY, message != null ? new ReadOnlyHint(message, linkListener) : null);
  }

  private static final class ReadOnlyHint {

    @NotNull public final @NlsContexts.HintText String message;
    @Nullable public final HyperlinkListener linkListener;

    private ReadOnlyHint(@NotNull @NlsContexts.HintText String message, @Nullable HyperlinkListener linkListener) {
      this.message = message;
      this.linkListener = linkListener;
    }
  }
}

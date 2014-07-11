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

import com.intellij.codeInsight.editorActions.TextBlockTransferable;
import com.intellij.codeInsight.editorActions.TextBlockTransferableData;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class CopyPasteSupport {
  private static final Logger LOG = Logger.getInstance(CopyPasteSupport.class);

  private CopyPasteSupport() { }

  public static void copySelectionToClipboard(@NotNull Editor editor) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<TextBlockTransferableData> extraData = new ArrayList<TextBlockTransferableData>();
    String s = editor.getCaretModel().supportsMultipleCarets() ? getSelectedTextForClipboard(editor, extraData)
                                                               : editor.getSelectionModel().getSelectedText();
    if (s == null) return;

    s = TextBlockTransferable.convertLineSeparators(s, "\n", extraData);
    Transferable contents = editor.getCaretModel().supportsMultipleCarets() ? new TextBlockTransferable(s, extraData, null) : new StringSelection(s);
    CopyPasteManager.getInstance().setContents(contents);
  }

  public static String getSelectedTextForClipboard(@NotNull Editor editor, @NotNull Collection<TextBlockTransferableData> extraDataCollector) {
    final StringBuilder buf = new StringBuilder();
    String separator = "";
    List<Caret> carets = editor.getCaretModel().getAllCarets();
    int[] startOffsets = new int[carets.size()];
    int[] endOffsets = new int[carets.size()];
    for (int i = 0; i < carets.size(); i++) {
      buf.append(separator);
      String caretSelectedText = carets.get(i).getSelectedText();
      startOffsets[i] = buf.length();
      if (caretSelectedText != null) {
        buf.append(caretSelectedText);
      }
      endOffsets[i] = buf.length();
      separator = "\n";
    }
    extraDataCollector.add(new CaretStateTransferableData(startOffsets, endOffsets));
    return buf.toString();
  }


  public static TextRange pasteFromClipboard(Editor editor) {
    return pasteTransferable(editor, (Producer<Transferable>)null);
  }

  public static TextRange pasteTransferable(Editor editor, final Transferable content) {
    return pasteTransferable(editor, new Producer<Transferable>() {
      @Nullable
      @Override
      public Transferable produce() {
        return content;
      }
    });
  }

  @Nullable
  public static TextRange pasteTransferable(final Editor editor, @Nullable Producer<Transferable> producer) {
    Transferable content = getTransferable(producer);
    if (content == null) return null;
    String text = getStringContent(content);
    if (text == null) return null;

    if (editor.getCaretModel().supportsMultipleCarets()) {
      int caretCount = editor.getCaretModel().getCaretCount();
      if (caretCount == 1 && editor.isColumnMode()) {
        int pastedLineCount = LineTokenizer.calcLineCount(text, true);
        EditorModificationUtil.deleteSelectedText(editor);
        Caret caret = editor.getCaretModel().getPrimaryCaret();
        for (int i = 0; i < pastedLineCount - 1; i++) {
          caret = caret.clone(false);
          if (caret == null) {
            break;
          }
        }
        caretCount = editor.getCaretModel().getCaretCount();
      }
      CaretStateTransferableData caretData = null;
      try {
        caretData = content.isDataFlavorSupported(CaretStateTransferableData.FLAVOR)
                    ? (CaretStateTransferableData)content.getTransferData(CaretStateTransferableData.FLAVOR) : null;
      }
      catch (Exception e) {
        LOG.error(e);
      }
      final Iterator<String> segments = new ClipboardTextPerCaretSplitter().split(text, caretData, caretCount).iterator();
      editor.getCaretModel().runForEachCaret(new CaretAction() {
        @Override
        public void perform(Caret caret) {
          EditorModificationUtil.insertStringAtCaret(editor, segments.next(), false, true);
        }
      });
      return null;
    }
    else {
      int caretOffset = editor.getCaretModel().getOffset();
      EditorModificationUtil.insertStringAtCaret(editor, text, false, true);
      return new TextRange(caretOffset, caretOffset + text.length());
    }
  }

  public static void pasteTransferableAsBlock(Editor editor, @Nullable Producer<Transferable> producer) {
    Transferable content = getTransferable(producer);
    if (content == null) return;
    String text = getStringContent(content);
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

      EditorModificationUtil.deleteSelectedText(editor);
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
        EditorModificationUtil.insertStringAtCaret(editor, line, false, true);
      }
      caretToRestore = new LogicalPosition(originalCaretLine, caretToRestore.column + longestLineLength);
    }
    else {
      for (int i = 0; i <= selectedLinesCount; i++) {
        editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(caretLine + i, caretToRestore.column));
        EditorModificationUtil.insertStringAtCaret(editor, text, false, true);
      }
      caretToRestore = new LogicalPosition(originalCaretLine, caretToRestore.column + text.length());
    }

    editor.getCaretModel().moveToLogicalPosition(caretToRestore);
    EditorModificationUtil.zeroWidthBlockSelectionAtCaretColumn(editor, caretLine, caretLine + selectedLinesCount);
  }

  @Nullable
  private static String getStringContent(@NotNull Transferable content) {
    RawText raw = RawText.fromTransferable(content);
    if (raw != null) return raw.rawText;

    try {
      return (String)content.getTransferData(DataFlavor.stringFlavor);
    }
    catch (UnsupportedFlavorException ignore) { }
    catch (IOException ignore) { }

    return null;
  }

  private static Transferable getTransferable(Producer<Transferable> producer) {
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
    return content;
  }
}

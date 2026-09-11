// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.editor.impl.SelectionModelImpl;
import com.intellij.openapi.ide.Sizeable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class TextBlockTransferable implements Transferable, Sizeable {
  private final Collection<? extends TextBlockTransferableData> myExtraData;
  private final RawText myRawText;
  private final String myText;
  private final DataFlavor[] myTransferDataFlavors;

  public TextBlockTransferable(@NotNull String text, @NotNull Collection<? extends TextBlockTransferableData> extraData, @Nullable RawText rawText) {
    myText = cleanFromNullsIfNeeded(text);
    myExtraData = extraData;
    myRawText = rawText;

    List<DataFlavorWithPriority> dataFlavors = new ArrayList<>();
    Collections.addAll(dataFlavors,
                       new DataFlavorWithPriority(DataFlavor.stringFlavor, TextBlockTransferableData.PLAIN_TEXT_PRIORITY),
                       new DataFlavorWithPriority(DataFlavor.plainTextFlavor, TextBlockTransferableData.PLAIN_TEXT_PRIORITY));
    final DataFlavor flavor = RawText.getDataFlavor();
    if (myRawText != null && flavor != null) {
      dataFlavors.add(new DataFlavorWithPriority(flavor, TextBlockTransferableData.PLAIN_TEXT_PRIORITY));
    }
    for(TextBlockTransferableData data: extraData) {
      final DataFlavor blockFlavor = data.getFlavor();
      if (blockFlavor != null) {
        dataFlavors.add(new DataFlavorWithPriority(blockFlavor, data.getPriority()));
      }
    }
    dataFlavors.sort(Comparator.comparingInt(value -> -value.priority));
    myTransferDataFlavors = ContainerUtil.map2Array(dataFlavors, DataFlavor.class, value -> value.flavor);
  }

  @Override
  public int getSize() {
    int size = myText.length();
    if (myRawText != null && !Strings.areSameInstance(myRawText.rawText, myText)) {
      size += StringUtil.length(myRawText.rawText);
    }
    return size;
  }

  private static @NotNull String cleanFromNullsIfNeeded(@NotNull String text) {
    // Clipboard on Windows and Linux works with null-terminated strings, on Mac nulls are not treated in a special way.
    return SystemInfo.isMac ? text : text.replace('\000', ' ');
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    return myTransferDataFlavors;
  }

  @Override
  public boolean isDataFlavorSupported(DataFlavor flavor) {
    DataFlavor[] flavors = getTransferDataFlavors();
    return ArrayUtil.contains(flavor, flavors);
  }

  @Override
  public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
    try {
      for(TextBlockTransferableData data: myExtraData) {
        if (Comparing.equal(data.getFlavor(), flavor)) {
          return data;
        }
      }
      if (myRawText != null && Comparing.equal(RawText.getDataFlavor(), flavor)) {
        return myRawText;
      }
      else if (DataFlavor.stringFlavor.equals(flavor)) {
        return myText;
      }
      else if (DataFlavor.plainTextFlavor.equals(flavor)) {
        return new StringReader(myText);
      }
    }
    catch(NoClassDefFoundError e) {
      // ignore
    }
    throw new UnsupportedFlavorException(flavor);
  }

  public static @NotNull String convertLineSeparators(@NotNull Editor editor, @NotNull String input) {
    return convertLineSeparators(editor, input, Collections.emptyList());
  }

  public static @NotNull String convertLineSeparators(@NotNull Editor editor, @NotNull String input,
                                                      @NotNull Collection<? extends TextBlockTransferableData> itemsToUpdate) {
    // converting line separators to spaces matches the behavior of Swing text components on paste
    return convertLineSeparators(input, editor.isOneLineMode() ? " " : "\n", itemsToUpdate);
  }

  public static String convertLineSeparators(String text,
                                             String newSeparator,
                                             Collection<? extends TextBlockTransferableData> itemsToUpdate) {
    if (!itemsToUpdate.isEmpty()){
      int[] offsets = getOffsets(itemsToUpdate);
      text = Strings.convertLineSeparators(text, newSeparator, offsets);
      setOffsets(itemsToUpdate, offsets);
      return text;
    }
    else{
      return StringUtil.convertLineSeparators(text, newSeparator);
    }
  }

  private static @NotNull String includeVirtualSelectionRows(@NotNull String text, @NotNull Editor editor,
                                                             @NotNull Collection<? extends TextBlockTransferableData> itemsToUpdate) {
    CaretModel model = editor.getCaretModel();
    if (!model.supportsMultipleCarets()) {
      return text;
    }

    List<Caret> carets = model.getAllCarets();
    List<TextLine> lines = getLines(text);
    BlockSelectionLines blockSelectionLines = getBlockSelectionLines(editor);
    if (blockSelectionLines == null || lines.size() != carets.size() || !fitsBlockSelectionLines(carets, blockSelectionLines)) {
      return text;
    }

    int[] offsets = getOffsets(itemsToUpdate);
    StringBuilder result = new StringBuilder(text.length() + blockSelectionLines.lineCount() - carets.size());
    int caretIndex = 0;
    int appliedShift = 0;
    for (int line = blockSelectionLines.startLine; line <= blockSelectionLines.endLine; line++) {
      if (line > blockSelectionLines.startLine) {
        result.append('\n');
      }
      if (caretIndex < carets.size() && carets.get(caretIndex).getLogicalPosition().line == line) {
        TextLine textLine = lines.get(caretIndex);
        int newShift = result.length() - textLine.startOffset;
        int shiftDelta = newShift - appliedShift;
        if (shiftDelta != 0) {
          shiftOffsets(offsets, textLine.startOffset, shiftDelta);
          appliedShift = newShift;
        }
        result.append(text, textLine.startOffset, textLine.endOffset);
        caretIndex++;
      }
    }
    setOffsets(itemsToUpdate, offsets);
    return result.toString();
  }

  @ApiStatus.Internal
  public static @NotNull String convertVirtualSelectionRowsToEmptyLines(@NotNull String text, @NotNull Editor editor) {
    return convertVirtualSelectionRowsToEmptyLines(text, editor, Collections.emptyList()).text();
  }

  @ApiStatus.Internal
  public static @NotNull VirtualSelectionText convertVirtualSelectionRowsToEmptyLines(@NotNull String rawText, @NotNull Editor editor,
                                                                                      @NotNull Collection<? extends TextBlockTransferableData> itemsToUpdate) {
    CaretModel model = editor.getCaretModel();
    if (!model.supportsMultipleCarets()) {
      return new VirtualSelectionText(rawText, rawText);
    }

    String text = removePureVirtualSelectionText(rawText, model.getAllCarets());
    rawText = includeVirtualSelectionRows(rawText, editor, itemsToUpdate);
    text = includeVirtualSelectionRows(text, editor, Collections.emptyList());
    return new VirtualSelectionText(rawText, text);
  }

  @ApiStatus.Internal
  public record VirtualSelectionText(@NotNull String rawText, @NotNull String text) {
  }

  private static @NotNull String removePureVirtualSelectionText(@NotNull String text, @NotNull List<Caret> carets) {
    StringBuilder result = new StringBuilder(text.length());
    int lineStart = 0;
    int caretIndex = 0;
    for (int i = 0; i <= text.length(); i++) {
      if (i == text.length() || text.charAt(i) == '\n') {
        if (caretIndex >= carets.size() || !isPureVirtualSelection(carets.get(caretIndex))) {
          result.append(text, lineStart, i);
        }
        if (i < text.length()) {
          result.append('\n');
        }
        lineStart = i + 1;
        caretIndex++;
      }
    }
    return result.toString();
  }

  private static @NotNull List<TextLine> getLines(@NotNull String text) {
    List<TextLine> result = new ArrayList<>();
    int lineStart = 0;
    for (int i = 0; i <= text.length(); i++) {
      if (i == text.length() || text.charAt(i) == '\n') {
        result.add(new TextLine(lineStart, i));
        lineStart = i + 1;
      }
    }
    return result;
  }

  private static @Nullable BlockSelectionLines getBlockSelectionLines(@NotNull Editor editor) {
    if (!(editor.getSelectionModel() instanceof SelectionModelImpl selectionModel)) {
      return null;
    }

    LogicalPosition blockStart = selectionModel.getBlockSelectionStart();
    LogicalPosition blockEnd = selectionModel.getBlockSelectionEnd();
    if (blockStart == null || blockEnd == null) {
      return null;
    }

    return new BlockSelectionLines(Math.min(blockStart.line, blockEnd.line), Math.max(blockStart.line, blockEnd.line));
  }

  private static boolean fitsBlockSelectionLines(@NotNull List<Caret> carets, @NotNull BlockSelectionLines blockSelectionLines) {
    int previousLine = -1;
    for (Caret caret : carets) {
      int line = caret.getLogicalPosition().line;
      if (line < blockSelectionLines.startLine || line > blockSelectionLines.endLine || line <= previousLine) {
        return false;
      }
      previousLine = line;
    }
    return true;
  }

  private static int @NotNull [] getOffsets(@NotNull Collection<? extends TextBlockTransferableData> itemsToUpdate) {
    int size = 0;
    for (TextBlockTransferableData data : itemsToUpdate) {
      size += data.getOffsetCount();
    }

    int[] offsets = new int[size];
    int index = 0;
    for (TextBlockTransferableData data : itemsToUpdate) {
      index = data.getOffsets(offsets, index);
    }
    return offsets;
  }

  private static void setOffsets(@NotNull Collection<? extends TextBlockTransferableData> itemsToUpdate, int @NotNull [] offsets) {
    int index = 0;
    for (TextBlockTransferableData data : itemsToUpdate) {
      index = data.setOffsets(offsets, index);
    }
  }

  private static void shiftOffsets(int @NotNull [] offsets, int startOffset, int shift) {
    for (int i = 0; i < offsets.length; i++) {
      if (offsets[i] >= startOffset) {
        offsets[i] += shift;
      }
    }
  }

  private static boolean isPureVirtualSelection(@NotNull Caret caret) {
    return caret.hasSelection() && caret.getSelectionStart() == caret.getSelectionEnd();
  }

  private record TextLine(int startOffset, int endOffset) {
  }

  private record BlockSelectionLines(int startLine, int endLine) {
    int lineCount() {
      return endLine - startLine + 1;
    }
  }

  private record DataFlavorWithPriority(DataFlavor flavor, int priority) {
  }
}
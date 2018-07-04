// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

public class TextBlockTransferable implements Transferable {
  private final Collection<TextBlockTransferableData> myExtraData;
  private final RawText myRawText;
  private final String myText;
  private final DataFlavor[] myTransferDataFlavors;

  public TextBlockTransferable(@NotNull String text, @NotNull Collection<TextBlockTransferableData> extraData, @Nullable RawText rawText) {
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

  @NotNull
  private static String cleanFromNullsIfNeeded(@NotNull String text) {
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
    for (DataFlavor flavor1 : flavors) {
      if (flavor.equals(flavor1)) {
        return true;
      }
    }
    return false;
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

  @NotNull
  public static String convertLineSeparators(@NotNull Editor editor, @NotNull String input) {
    return convertLineSeparators(editor, input, Collections.emptyList());
  }

  @NotNull
  public static String convertLineSeparators(@NotNull Editor editor, @NotNull String input,
                                             @NotNull Collection<TextBlockTransferableData> itemsToUpdate) {
    // converting line separators to spaces matches the behavior of Swing text components on paste
    return convertLineSeparators(input, editor.isOneLineMode() ? " " : "\n", itemsToUpdate);
  }

  public static String convertLineSeparators(String text,
                                             String newSeparator,
                                             Collection<TextBlockTransferableData> itemsToUpdate) {
    if (itemsToUpdate.size() > 0){
      int size = 0;
      for(TextBlockTransferableData data: itemsToUpdate) {
        size += data.getOffsetCount();
      }

      int[] offsets = new int[size];
      int index = 0;
      for(TextBlockTransferableData data: itemsToUpdate) {
        index = data.getOffsets(offsets, index);
      }

      text = StringUtil.convertLineSeparators(text, newSeparator, offsets);

      index = 0;
      for(TextBlockTransferableData data: itemsToUpdate) {
        index = data.setOffsets(offsets, index);
      }

      return text;
    }
    else{
      return StringUtil.convertLineSeparators(text, newSeparator);
    }
  }

  private static class DataFlavorWithPriority {
    private final DataFlavor flavor;
    private final int priority;

    private DataFlavorWithPriority(DataFlavor flavor, int priority) {
      this.flavor = flavor;
      this.priority = priority;
    }
  }
}
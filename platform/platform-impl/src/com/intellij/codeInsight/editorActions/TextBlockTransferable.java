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

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
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
import java.util.List;

public class TextBlockTransferable implements Transferable {
  private final Collection<TextBlockTransferableData> myExtraData;
  private final RawText myRawText;
  private final String myText;
  private final DataFlavor[] myTransferDataFlavors;

  public TextBlockTransferable(@NotNull String text, @NotNull Collection<TextBlockTransferableData> extraData, @Nullable RawText rawText) {
    myText = cleanFromNullsIfNeeded(text);
    myExtraData = extraData;
    myRawText = rawText;

    List<DataFlavor> dataFlavors = new ArrayList<>();
    Collections.addAll(dataFlavors, DataFlavor.stringFlavor, DataFlavor.plainTextFlavor);
    final DataFlavor flavor = RawText.getDataFlavor();
    if (myRawText != null && flavor != null) {
      dataFlavors.add(flavor);
    }
    for(TextBlockTransferableData data: extraData) {
      final DataFlavor blockFlavor = data.getFlavor();
      if (blockFlavor != null) {
        dataFlavors.add(blockFlavor);
      }
    }
    myTransferDataFlavors = dataFlavors.toArray(new DataFlavor[dataFlavors.size()]);
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
    return convertLineSeparators(editor, input, Collections.<TextBlockTransferableData>emptyList());
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
}
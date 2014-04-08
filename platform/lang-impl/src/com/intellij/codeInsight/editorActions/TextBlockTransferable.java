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

import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class TextBlockTransferable implements Transferable {
  private final Collection<TextBlockTransferableData> myExtraData;
  private final RawText myRawText;
  private final String myText;

  public TextBlockTransferable(String text, Collection<TextBlockTransferableData> extraData, RawText rawText) {
    myText = text;
    myExtraData = extraData;
    myRawText = rawText;
  }

  @Override
  public DataFlavor[] getTransferDataFlavors() {
    // We don't cache the result to allow certain dataflavors to 'expire'.
    // This is used e.g. for RTF and HTML flavors to avoid memory leaking.
    List<DataFlavor> flavors = new ArrayList<DataFlavor>(myExtraData.size() + 3);
    flavors.add(DataFlavor.stringFlavor);
    flavors.add(DataFlavor.plainTextFlavor);
    final DataFlavor flavor = RawText.getDataFlavor();
    if (myRawText != null && flavor != null) {
      flavors.add(flavor);
    }
    for(TextBlockTransferableData data : myExtraData) {
      final DataFlavor blockFlavor = data.getFlavor();
      if (blockFlavor != null) {
        flavors.add(blockFlavor);
      }
    }
    return flavors.toArray(new DataFlavor[flavors.size()]);
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

  public static String convertLineSeparators(String text,
                                             String newSeparator,
                                             Collection<TextBlockTransferableData> transferableDatas) {
    if (transferableDatas.size() > 0){
      int size = 0;
      for(TextBlockTransferableData data: transferableDatas) {
        size += data.getOffsetCount();
      }

      int[] offsets = new int[size];
      int index = 0;
      for(TextBlockTransferableData data: transferableDatas) {
        index = data.getOffsets(offsets, index);
      }

      text = StringUtil.convertLineSeparators(text, newSeparator, offsets);

      index = 0;
      for(TextBlockTransferableData data: transferableDatas) {
        index = data.setOffsets(offsets, index);
      }

      return text;
    }
    else{
      return StringUtil.convertLineSeparators(text, newSeparator);
    }
  }
}
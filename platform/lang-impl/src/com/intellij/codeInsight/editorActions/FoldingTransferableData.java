/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;

public class FoldingTransferableData implements TextBlockTransferableData, Serializable {
  private final FoldingData[] myFoldingDatas;

  public FoldingTransferableData(final FoldingData[] foldingDatas) {
    myFoldingDatas = foldingDatas;
  }

  public DataFlavor getFlavor() {
    return FoldingData.getDataFlavor();
  }

  public int getOffsetCount() {
    return myFoldingDatas.length * 2;
  }

  public int getOffsets(final int[] offsets, int index) {
    for (FoldingData data : myFoldingDatas) {
      offsets[index++] = data.startOffset;
      offsets[index++] = data.endOffset;
    }
    return index;
  }

  public int setOffsets(final int[] offsets, int index) {
    for (FoldingData data : myFoldingDatas) {
      data.startOffset = offsets[index++];
      data.endOffset = offsets[index++];
    }
    return index;
  }

  protected FoldingTransferableData clone() {
    FoldingData[] newFoldingData = new FoldingData[myFoldingDatas.length];
    for (int i = 0; i < myFoldingDatas.length; i++) {
      newFoldingData[i] = (FoldingData)myFoldingDatas[i].clone();
    }
    return new FoldingTransferableData(newFoldingData);
  }

  public FoldingData[] getData() {
    return myFoldingDatas;
  }

  public static class FoldingData implements Cloneable, Serializable {
    private static @NonNls DataFlavor ourFlavor;

    public int startOffset;
    public int endOffset;
    public final boolean isExpanded;

    public FoldingData(int startOffset, int endOffset, boolean expanded){
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      isExpanded = expanded;
    }

    public Object clone() {
      try{
        return super.clone();
      }
      catch(CloneNotSupportedException e){
        throw new RuntimeException();
      }
    }

    public static DataFlavor getDataFlavor() {
      if (ourFlavor != null) {
        return ourFlavor;
      }
      try {
        ourFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + FoldingData.class.getName(), "FoldingData");
      }
      catch (NoClassDefFoundError e) {
        return null;
      }
      catch (IllegalArgumentException e) {
        return null;
      }
      return ourFlavor;
    }
  }
}

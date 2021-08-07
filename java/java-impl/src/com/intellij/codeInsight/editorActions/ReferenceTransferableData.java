// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;


public class ReferenceTransferableData implements TextBlockTransferableData, Cloneable, Serializable {
  private final ReferenceData[] myReferenceDatas;

  public ReferenceTransferableData(ReferenceData @NotNull [] referenceDatas) {
    myReferenceDatas = referenceDatas;
  }

  @Override
  public DataFlavor getFlavor() {
    return ReferenceData.getDataFlavor();
  }

  @Override
  public int getOffsetCount() {
    return myReferenceDatas.length * 2;
  }

  @Override
  public int getOffsets(final int[] offsets, int index) {
    for (ReferenceData data : myReferenceDatas) {
      offsets[index++] = data.startOffset;
      offsets[index++] = data.endOffset;
    }
    return index;
  }

  @Override
  public int setOffsets(final int[] offsets, int index) {
    for (ReferenceData data : myReferenceDatas) {
      data.startOffset = offsets[index++];
      data.endOffset = offsets[index++];
    }
    return index;
  }

  @Override
  public ReferenceTransferableData clone() {
    ReferenceData[] newReferenceData = new ReferenceData[myReferenceDatas.length];
    for (int i = 0; i < myReferenceDatas.length; i++) {
      newReferenceData[i] = (ReferenceData)myReferenceDatas[i].clone();
    }
    return new ReferenceTransferableData(newReferenceData);
  }

  public ReferenceData @NotNull [] getData() {
    return myReferenceDatas;
  }
}

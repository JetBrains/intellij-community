// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;

@ApiStatus.Internal
public final class FoldingTransferableData implements TextBlockTransferableData, Serializable {
  private final FoldingData[] myFoldingDatas;

  public FoldingTransferableData(final FoldingData[] foldingDatas) {
    myFoldingDatas = foldingDatas;
  }

  @Override
  public @Nullable DataFlavor getFlavor() {
    return FoldingData.getDataFlavor();
  }

  @Override
  public int getOffsetCount() {
    return myFoldingDatas.length * 2;
  }

  @Override
  public int getOffsets(int @NotNull [] offsets, int index) {
    for (FoldingData data : myFoldingDatas) {
      offsets[index++] = data.startOffset;
      offsets[index++] = data.endOffset;
    }
    return index;
  }

  @Override
  public int setOffsets(int @NotNull [] offsets, int index) {
    for (FoldingData data : myFoldingDatas) {
      data.startOffset = offsets[index++];
      data.endOffset = offsets[index++];
    }
    return index;
  }

  @Override
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
}

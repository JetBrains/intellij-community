// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions;

import org.jetbrains.annotations.NonNls;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;


public class IndentTransferableData implements TextBlockTransferableData, Serializable {
  private static @NonNls DataFlavor ourFlavor;

  private final int myOffset;

  public IndentTransferableData(int offset) {
    myOffset = offset;
  }


  public int getOffset() {
    return myOffset;
  }

  @Override
  public DataFlavor getFlavor() {
    return getDataFlavorStatic();
  }

  public static DataFlavor getDataFlavorStatic() {
    if (ourFlavor != null) {
      return ourFlavor;
    }
    try {
      ourFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + IndentTransferableData.class.getName(), "Python indent transferable data", IndentTransferableData.class.getClassLoader());
    }
    catch (NoClassDefFoundError | IllegalArgumentException | ClassNotFoundException e) {
      return null;
    }
    return ourFlavor;
  }

  @Override
  public int getOffsetCount() {
    return 0;
  }

  @Override
  public int getOffsets(int[] offsets, int index) {
    return index;
  }

  @Override
  public int setOffsets(int[] offsets, int index) {
    return index;
  }

  @Override
  protected IndentTransferableData clone() {
    return new IndentTransferableData(myOffset);
  }
}

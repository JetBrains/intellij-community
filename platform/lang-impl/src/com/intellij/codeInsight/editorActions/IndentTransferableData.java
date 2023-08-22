// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;


public final class IndentTransferableData implements TextBlockTransferableData, Serializable {
  private static @NonNls DataFlavor ourFlavor;

  private final int myOffset;

  public IndentTransferableData(int offset) {
    myOffset = offset;
  }


  public int getOffset() {
    return myOffset;
  }

  @Override
  public @Nullable DataFlavor getFlavor() {
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
  protected IndentTransferableData clone() {
    return new IndentTransferableData(myOffset);
  }
}

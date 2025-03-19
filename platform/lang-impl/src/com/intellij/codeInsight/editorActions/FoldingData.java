// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

import java.awt.datatransfer.DataFlavor;
import java.io.Serializable;

@ApiStatus.Internal
public final class FoldingData implements Cloneable, Serializable {
  private static @NonNls DataFlavor ourFlavor;

  public int startOffset;
  public int endOffset;
  public final boolean isExpanded;
  public final String placeholderText;

  public FoldingData(int startOffset,
                     int endOffset,
                     boolean expanded,
                     String placeholderText){
    this.startOffset = startOffset;
    this.endOffset = endOffset;
    isExpanded = expanded;
    this.placeholderText = placeholderText;
  }

  @Override
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
      ourFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + FoldingData.class.getName(), "FoldingData", FoldingData.class.getClassLoader());
    }
    catch (NoClassDefFoundError | IllegalArgumentException | ClassNotFoundException e) {
      return null;
    }
    return ourFlavor;
  }
}

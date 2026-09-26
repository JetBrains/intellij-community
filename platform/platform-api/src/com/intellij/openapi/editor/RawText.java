// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.io.Serializable;

public class RawText implements Cloneable, Serializable {
  private static DataFlavor ourFlavor;

  public String rawText;

  public RawText(final String rawText) {
    this.rawText = rawText;
  }

  @Override
  public Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException();
    }
  }

  public static DataFlavor getDataFlavor() {
    if (ourFlavor != null) return ourFlavor;

    try {
      DataFlavor flavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + RawText.class.getName(), "Raw Text", RawText.class.getClassLoader());
      ourFlavor = flavor;
      return flavor;
    }
    catch (NoClassDefFoundError | IllegalArgumentException | ClassNotFoundException ignore) { }

    return null;
  }

  public static @Nullable RawText fromTransferable(Transferable content) {
    DataFlavor flavor = getDataFlavor();

    if (flavor != null) {
      try {
        return (RawText)content.getTransferData(flavor);
      }
      catch (UnsupportedFlavorException | IOException ignore) { }
    }

    return null;
  }
}
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.editorActions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;


public interface TextBlockTransferableData {
  int PLAIN_TEXT_PRIORITY = 0;

  @Nullable DataFlavor getFlavor();

  default int getOffsetCount() { return 0; }

  default int getOffsets(int @NotNull [] offsets, int index) {
    return index;
  }

  default int setOffsets(int @NotNull [] offsets, int index) {
    return index;
  }

  /**
   * Priority defines an order in which resulting transferable will mention available DataFlavor-s.
   *
   * @see Transferable#getTransferDataFlavors()
   */
  default int getPriority() { return PLAIN_TEXT_PRIORITY; }
}

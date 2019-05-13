// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.editorActions;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

/**
 * @author yole
 */
public interface TextBlockTransferableData {
  int PLAIN_TEXT_PRIORITY = 0;

  DataFlavor getFlavor();

  int getOffsetCount();
  int getOffsets(final int[] offsets, final int index);
  int setOffsets(final int[] offsets, final int index);

  /**
   * Priority defines an order in which resulting transferable will mention available DataFlavor-s.
   * 
   * @see Transferable#getTransferDataFlavors()
   */
  default int getPriority() { return PLAIN_TEXT_PRIORITY; }
}

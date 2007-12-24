package com.intellij.codeInsight.editorActions;

import java.awt.datatransfer.DataFlavor;

/**
 * @author yole
 */
public interface TextBlockTransferableData {
  DataFlavor getFlavor();

  int getOffsetCount();
  int getOffsets(final int[] offsets, final int index);
  int setOffsets(final int[] offsets, final int index);
}

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

public interface FoldingModel {
  FoldRegion addFoldRegion(int startOffset, int endOffset, String placeholderText);
  void removeFoldRegion(FoldRegion region);
  FoldRegion[] getAllFoldRegions();

  boolean isOffsetCollapsed(int offset);

  void runBatchFoldingOperation(Runnable operation);
}

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.util.UserDataHolder;

public interface RangeMarker extends UserDataHolder{
  Document getDocument();

  int getStartOffset();
  int getEndOffset();

  boolean isValid();

  void setGreedyToLeft(boolean greedy);
  void setGreedyToRight(boolean greedy);
}
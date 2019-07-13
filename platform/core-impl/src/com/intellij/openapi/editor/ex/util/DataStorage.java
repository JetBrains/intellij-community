// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.psi.tree.IElementType;

/**
 * A storage for storing data in {@link SegmentArrayWithData}.
 * Encapsulates segment data logic processing.
 */
public interface DataStorage {
  void setData(int segmentIndex, int data);

  void remove(int startIndex, int endIndex, int mySegmentCount);

  void replace(DataStorage storage, int startOffset, int len);

  void insert(DataStorage storageToInsert, int startIndex, int segmentCountToInsert, int segmentCount);

  int getData(int index);

  int packData(IElementType tokenType, int state, boolean isRestartableState);

  int unpackStateFromData(int data);

  IElementType unpackTokenFromData(int data);

  DataStorage copy();

  DataStorage createStorage();
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * A storage for storing data in {@link SegmentArrayWithData}.
 * Encapsulates segment data logic processing.
 */
public interface DataStorage {
  void setData(int segmentIndex, int data);

  void remove(int startIndex, int endIndex, int mySegmentCount);

  void replace(@NotNull DataStorage storage, int startOffset, int len);

  void insert(@NotNull DataStorage storageToInsert, int startIndex, int segmentCountToInsert, int segmentCount);

  int getData(int index);

  int packData(@NotNull IElementType tokenType, int state, boolean isRestartableState);

  int unpackStateFromData(int data);

  @NotNull
  IElementType unpackTokenFromData(int data);

  @NotNull
  DataStorage copy();

  @NotNull
  DataStorage createStorage();
}

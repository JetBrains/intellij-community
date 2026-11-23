// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex.util;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
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

  /**
   * Returns the {@link IElementType#getIndex() index} of the token type that {@link #unpackTokenFromData} gives for {@code data},
   * or {@code -1} when this storage does not know it.
   */
  @ApiStatus.Internal
  default int unpackTokenIndexFromData(int data) {
    return -1;
  }

  @NotNull
  DataStorage copy();

  @NotNull
  DataStorage createStorage();
}

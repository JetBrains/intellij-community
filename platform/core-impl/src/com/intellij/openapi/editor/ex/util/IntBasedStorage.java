// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * SegmentArrayWithData storage based on the int array. It allows to store one int per segment.
 * It allows to pack {@link IElementType} index and state of the lexer for segment.
 */
@ApiStatus.Internal
public class IntBasedStorage extends IntArrayDataStorage {

  private static final Logger LOG = Logger.getInstance(IntBasedStorage.class);

  public IntBasedStorage() {
  }

  private IntBasedStorage(int[] data) {
    super(data);
  }

  @Override
  public int packData(@NotNull IElementType tokenType, int state, boolean isRestartableState) {
    if (tokenType.getIndex() < 0) {
      LOG.error(new IllegalArgumentException(
        "Token type " + tokenType + " is not registered and cannot be used in a lexer editor highlighter."));
    }
    return ((state & 0xFFFF) << 16) | (tokenType.getIndex() & 0xffff);
  }

  @Override
  public int unpackStateFromData(int data) {
    return data >> 16;
  }

  @Override
  public @NotNull IElementType unpackTokenFromData(int data) {
    return IElementType.find((short)(data & 0xffff));
  }

  @Override
  public @NotNull DataStorage copy() {
    return new IntBasedStorage(myData);
  }

  @Override
  public @NotNull DataStorage createStorage() {
    return new IntBasedStorage();
  }
}

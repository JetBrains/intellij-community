// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import org.jetbrains.annotations.NotNull;

final class GuardedBlock extends PersistentRangeMarker {
  static final byte GUARD_BLOCK_FLAVOR_FLAG = IntervalTreeImpl.nextAvailableFlavorFlag();

  static boolean isGuard(@NotNull RangeMarker rangeMarker) {
    return rangeMarker instanceof GuardedBlock;
  }

  GuardedBlock(@NotNull DocumentEx document, int startOffset, int endOffset) {
    super(document, startOffset, endOffset, true);
  }

  @Override
  public byte getFlavorFlags() {
    return GUARD_BLOCK_FLAVOR_FLAG;
  }

  @Override
  public @NotNull String toString() {
    return super.toString().replace("PersistentRangeMarker", "GuardedBlock");
  }
}

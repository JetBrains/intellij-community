// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;

public interface GuardedBlocks {
  @NotNull RangeMarkerEx createGuardedBlock(@NotNull DocumentEx hostDocument, int startOffset, int endOffset);

  void removeGuardedBlock(@NotNull RangeMarker block);

  @NotNull @UnmodifiableView
  List<RangeMarker> getGuardedBlocks();

  @Nullable RangeMarker getOffsetGuard(int offset);

  @Nullable RangeMarker getRangeGuard(int start, int end);
}

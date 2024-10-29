// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

final class GuardedBlock extends PersistentRangeMarker {

  static @NotNull Processor<RangeMarkerEx> processor(Processor<? super RangeMarkerEx> processor) {
    return rangeMarker -> {
      if (isGuarded(rangeMarker)) {
        return processor.process(rangeMarker);
      }
      return true;
    };
  }

  static boolean isGuarded(@NotNull RangeMarker rangeMarker) {
    return rangeMarker instanceof GuardedBlock;
  }

  GuardedBlock(DocumentEx document, int startOffset, int endOffset) {
    super(document, startOffset, endOffset, true);
  }

  @Override
  public @NotNull String toString() {
    return super.toString().replace("PersistentRangeMarker", "GuardedBlock");
  }
}

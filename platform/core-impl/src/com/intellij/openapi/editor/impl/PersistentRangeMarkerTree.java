// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.DocumentEventDispatcher;
import com.intellij.openapi.editor.ex.RangeMarkerEx;
import org.jetbrains.annotations.NotNull;

/**
 * RangeMarkerTree that keeps all intervals on weak references except the guarded blocks.
 * This class must be static because it should not capture 'this' reference to the document.
 * Otherwise, there will be a chain of hard references {@code file -> tree -> document} and gc won't collect the document
 */
final class PersistentRangeMarkerTree extends RangeMarkerTree<RangeMarkerEx> {

  PersistentRangeMarkerTree(@NotNull DocumentEventDispatcher dispatcher) {
    super(dispatcher);
  }

  @Override
  protected boolean keepIntervalOnWeakReference(@NotNull RangeMarkerEx interval) {
    // prevent guarded blocks to be collected by gc
    return !GuardedBlock.isGuarded(interval);
  }
}

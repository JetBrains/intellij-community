// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ide;

import org.jetbrains.annotations.Range;

/**
 * Represents an entity which can estimate the amount of memory it occupies in the heap.
 * <p>
 * In particular, {@link java.awt.datatransfer.Transferable} instances implementing this interface can help {@link CopyPasteManager}
 * implementation to manage clipboard history memory footprint.
 */
public interface Sizeable {
  /**
   * Object size estimation (in bytes).
   */
  @Range(from = 0, to = Integer.MAX_VALUE)
  int getSize();
}

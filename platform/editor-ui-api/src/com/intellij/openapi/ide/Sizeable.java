// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ide;

/**
 * Represents an entity which can estimate the amount of memory it occupies in the heap.
 */
public interface Sizeable {
  /**
   * Object size estimation (in bytes).
   */
  int getSize();
}

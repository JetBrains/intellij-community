// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

/**
 * To be used in {@link IndexableEntityProvider} to notify that proper partial rescan and reindex seems impossible,
 * and it's better to make full rescan to avoid corruption of indices.
 */
public class IndexableEntityResolvingException extends Exception {
  public IndexableEntityResolvingException(String message) {
    super(message);
  }
}

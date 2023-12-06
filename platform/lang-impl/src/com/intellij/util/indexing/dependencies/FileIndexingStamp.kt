// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import org.jetbrains.annotations.VisibleForTesting
import java.util.function.IntConsumer

private const val NULL_INDEXING_STAMP: Int = 0

interface FileIndexingStamp {
  /**
   * Number representing IndexingStamp. Use [isSame] to compare this number to any other stamps.
   * Signature made complicated intentionally to make it harder to obtain int and compare it with some other int
   * obtained from another FileIndexingStamp. Comparison should onlu be made via [isSame]
   */
  fun store(storage: IntConsumer)

  /**
   * Compares this stamp to Int value obtained via [store]
   */
  fun isSame(i: Int): Boolean
}

internal object NullIndexingStamp : FileIndexingStamp {
  override fun store(storage: IntConsumer) {
    storage.accept(NULL_INDEXING_STAMP)
  }

  override fun isSame(i: Int): Boolean = false
}

@VisibleForTesting
data class ReadWriteFileIndexingStampImpl(val stamp: Int) : FileIndexingStamp {
  override fun store(storage: IntConsumer) {
    storage.accept(stamp)
  }

  override fun isSame(i: Int): Boolean {
    return i != NULL_INDEXING_STAMP && i == stamp
  }
}

@VisibleForTesting
data class WriteOnlyFileIndexingStampImpl(val stamp: Int) : FileIndexingStamp {
  override fun store(storage: IntConsumer) {
    storage.accept(stamp)
  }

  override fun isSame(i: Int): Boolean = false
}

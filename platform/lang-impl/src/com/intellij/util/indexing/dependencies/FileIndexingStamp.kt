// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import java.util.function.IntConsumer

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
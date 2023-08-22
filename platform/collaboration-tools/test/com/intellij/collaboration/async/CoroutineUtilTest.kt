// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CoroutineUtilTest {
  @Test
  fun `Collecting batches works`() = runTest {
    val collectedList = flowOf(listOf(1, 2), listOf(3), listOf(4, 5))
      .collectBatches()
      .last()

    assertEquals(listOf(1, 2, 3, 4, 5), collectedList)
  }
}
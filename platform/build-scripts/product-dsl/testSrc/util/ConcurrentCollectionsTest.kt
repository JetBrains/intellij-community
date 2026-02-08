// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class ConcurrentCollectionsTest {
  @Test
  fun `map concurrent transforms each item`() {
    runBlocking(Dispatchers.Default) {
      val input = (1..8).toList()

      val result = input.mapConcurrent(concurrency = input.size) {
        delay((input.size - it).toLong() * 5)
        it * 2
      }

      assertThat(result).hasSize(input.size)
      assertThat(result).containsExactlyElementsOf(input.map { it * 2 })
    }
  }

  @Test
  fun `map concurrent validates concurrency`() {
    assertThatThrownBy {
      runBlocking {
        listOf(1).mapConcurrent(concurrency = 0) { it }
      }
    }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Concurrency must be positive")
  }

  @Test
  fun `map concurrent propagates transform failures`() {
    assertThatThrownBy {
      runBlocking(Dispatchers.Default) {
        listOf(1, 2, 3).mapConcurrent(concurrency = 2) { item ->
          check(item != 2) { "boom" }
          item
        }
      }
    }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("boom")
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import app.cash.turbine.test
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BatchesLoaderTest {
  @Test
  fun `batches load incrementally`() = runTest {
    val batchesFlow = flowOf(listOf(1, 2), listOf(3, 4), listOf(5))
    val loader = BatchesLoader(backgroundScope, batchesFlow)

    loader.getBatches().test(timeout = 1.seconds) {
      assertThat(awaitItem()).containsExactly(1, 2)
      assertThat(awaitItem()).containsExactly(3, 4)
      assertThat(awaitItem()).containsExactly(5)
      awaitComplete()
    }
  }

  @Test
  fun `empty flow produces no batches`() = runTest {
    val batchesFlow = emptyFlow<List<Int>>()
    val loader = BatchesLoader(backgroundScope, batchesFlow)

    loader.getBatches().test(timeout = 1.seconds) {
      awaitComplete()
    }
  }

  @Test
  fun `error is propagated to collectors`() = runTest {
    val testException = RuntimeException("Test error")
    val batchesFlow = flow<List<Int>> {
      throw testException
    }
    val loader = BatchesLoader(backgroundScope, batchesFlow)

    loader.getBatches().test(timeout = 1.seconds) {
      val error = awaitError()
      assertThat(error).isInstanceOf(RuntimeException::class.java)
      assertThat(error.message).isEqualTo("Test error")
    }
  }

  @Test
  fun `cancel stops loading and allows restart`() = runTest {
    var loadCount = 0
    val batchesFlow = flow {
      loadCount++
      emit(listOf(loadCount))
    }
    val loader = BatchesLoader(backgroundScope, batchesFlow)

    val firstResults = loader.getBatches().toList()
    assertThat(firstResults).containsExactly(listOf(1))
    assertThat(loadCount).isEqualTo(1)

    loader.cancel()

    val secondResults = loader.getBatches().toList()
    assertThat(secondResults).containsExactly(listOf(2))
    assertThat(loadCount).isEqualTo(2)
  }

  @Test
  fun `multiple subscribers share the same loading process`() = runTest {
    var loadCount = 0
    val batchesFlow = flow {
      loadCount++
      emit(listOf(1, 2))
      emit(listOf(3, 4))
    }
    val loader = BatchesLoader(backgroundScope, batchesFlow)

    // First collection triggers loading
    val results1 = loader.getBatches().toList()
    assertThat(results1.flatten()).containsExactly(1, 2, 3, 4)
    assertThat(loadCount).isEqualTo(1)

    // Second collection reuses the shared flow (loading happened only once)
    val results2 = loader.getBatches().toList()
    assertThat(results2.flatten()).containsExactly(1, 2, 3, 4)
    assertThat(loadCount).isEqualTo(1)
  }

  @Test
  fun `cancel is idempotent`() = runTest {
    val batchesFlow = flowOf(listOf(1))
    val loader = BatchesLoader(backgroundScope, batchesFlow)

    loader.getBatches().toList()

    // Multiple cancels should not throw
    loader.cancel()
    loader.cancel()
    loader.cancel()

    // Should still work after multiple cancels
    val results = loader.getBatches().toList()
    assertThat(results).containsExactly(listOf(1))
  }

  @Test
  fun `loader with stopTimeout stops after a period of inactivity and reloads data from scratch on next call`() = runTest {
    val batchesFlow = flow {
      emit(listOf(1, 2))
      emit(listOf(3, 4))
    }
    val loader = BatchesLoader(backgroundScope, batchesFlow, stopTimeout = 100.milliseconds)

    loader.getBatches().test(timeout = 1.seconds) {
      assertThat(awaitItem()).containsExactly(1, 2)
      assertThat(awaitItem()).containsExactly(3, 4)
      awaitComplete()
    }
    
    loader.getBatches().test(timeout = 1.seconds) {
      assertThat(awaitItem()).containsExactly(1, 2, 3, 4)
      awaitComplete()
    }

    delay(150.milliseconds)

    loader.getBatches().test(timeout = 1.seconds) {
      assertThat(awaitItem()).containsExactly(1, 2)
      assertThat(awaitItem()).containsExactly(3, 4)
      awaitComplete()
    }
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.collaboration.async.timeoutRunBlockingWithBackgroundScope
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AsyncIncrementalListComputerTest {

  @Test
  fun `nothing is computed until more is requested`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val computer = AsyncIncrementalListComputer.createIn(bg, computerOf(listOf(1)))

    val state = computer.state.value
    assertFalse(state.isValueAvailable)
    assertFalse(state.isLoading)
    assertFalse(state.isComplete)
    assertNull(state.exceptionOrNull)
  }

  @Test
  fun `request more computes the first page`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val computer = AsyncIncrementalListComputer.createIn(bg, computerOf(listOf(1, 2)))

    computer.requestMore()

    val state = computer.state.first { it.valueOrNull != null }
    assertEquals(listOf(1, 2), state.valueOrNull)
    assertFalse(state.isLoading)
  }

  @Test
  fun `subsequent requests append the following pages`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val computer = AsyncIncrementalListComputer.createIn(bg, computerOf(listOf(1, 2), listOf(3, 4)))

    computer.requestMore()
    val afterFirst = computer.state.first { it.valueOrNull == listOf(1, 2) }
    assertFalse(afterFirst.isComplete) // there is still a page to load

    computer.requestMore()
    val afterSecond = computer.state.first { it.valueOrNull == listOf(1, 2, 3, 4) }
    assertTrue(afterSecond.isComplete)
  }

  @Test
  fun `the value is marked complete after the last page`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val computer = AsyncIncrementalListComputer.createIn(bg, computerOf(listOf(1, 2)))

    computer.requestMore()

    val state = computer.state.first { it.isComplete }
    assertEquals(listOf(1, 2), state.valueOrNull)
    assertFalse(state.isLoading)
  }

  @Test
  fun `request more does nothing once the sequence is complete`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val computer = AsyncIncrementalListComputer.createIn(bg, computerOf(listOf(1, 2)))

    computer.requestMore()
    computer.state.first { it.isComplete }

    computer.requestMore()
    repeat(3) { yield() } // give a potential extra load a chance to run

    val state = computer.state.value
    assertEquals(listOf(1, 2), state.valueOrNull)
    assertTrue(state.isComplete)
  }

  @Test
  fun `an empty sequence produces a complete empty list`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val computer = AsyncIncrementalListComputer.createIn(bg, computerOf())

    computer.requestMore()

    val state = computer.state.first { it.isValueAvailable }
    assertEquals(emptyList<Int>(), state.valueOrNull)
    assertTrue(state.isComplete)
    assertFalse(state.isLoading)
  }

  @Test
  fun `loading is reported while a page is being computed`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val loader = ManualComputer()
    val computer = AsyncIncrementalListComputer.createIn(bg, ComputableSequence { loader })

    computer.requestMore()
    val loading = computer.state.first { it.isLoading }
    assertNull(loading.valueOrNull) // still parked on the first page

    loader.emitPage(listOf(1))
    val loaded = computer.state.first { !it.isLoading }
    assertEquals(listOf(1), loaded.valueOrNull)
  }

  @Test
  fun `an error on the first page is reported and stops loading`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val error = RuntimeException("boom")
    val loader = ManualComputer()
    val computer = AsyncIncrementalListComputer.createIn(bg, ComputableSequence { loader })

    computer.requestMore()
    computer.state.first { it.isLoading }
    loader.emitError(error)

    val state = computer.state.first { it.exceptionOrNull != null }
    assertSame(error, state.exceptionOrNull)
    assertNull(state.valueOrNull)
    assertFalse(state.isLoading)
  }

  @Test
  fun `an error on a later page keeps the already computed value`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val error = RuntimeException("boom")
    val loader = ManualComputer()
    val computer = AsyncIncrementalListComputer.createIn(bg, ComputableSequence { loader })

    computer.requestMore()
    loader.emitPage(listOf(1))
    computer.state.first { it.valueOrNull == listOf(1) }

    computer.requestMore()
    computer.state.first { it.isLoading }
    loader.emitError(error)

    val state = computer.state.first { it.exceptionOrNull != null }
    assertSame(error, state.exceptionOrNull)
    assertEquals(listOf(1), state.valueOrNull)
    assertFalse(state.isLoading)
  }

  @Test
  fun `cancellation resets the state`() = timeoutRunBlockingWithBackgroundScope { bg ->
    val loaderScope = bg.childScope("loader scope")
    val loader = ManualComputer()
    val computer = AsyncIncrementalListComputer.createIn(loaderScope, ComputableSequence { loader })

    computer.requestMore()
    computer.state.first { it.isLoading }

    loaderScope.cancel()
    loaderScope.coroutineContext.job.join()

    val state = computer.state.value
    assertFalse(state.isValueAvailable)
    assertFalse(state.isLoading)
    assertFalse(state.isComplete)
    assertNull(state.exceptionOrNull)
  }

  private fun computerOf(vararg pages: List<Int>): ComputableSequence<ListPart<Int>> =
    pages.asSequence()
      .mapIndexed { index, page -> SequenceItem(page, isLast = index == pages.lastIndex) }
      .asComputedSequence()

  /**
   * An infinite [SequenceComputer] whose every `computeNext` parks until the test releases the next
   * result via [emitPage]/[emitError], so the test controls exactly when each page load completes.
   */
  private class ManualComputer : SequenceComputer<ListPart<Int>> {
    private val results = Channel<Result<List<Int>>>(Channel.UNLIMITED)

    override suspend fun computeNext(): SequenceComputer.ComputationOutcome<ListPart<Int>> =
      SequenceComputer.ComputationOutcome.Item(SequenceItem(results.receive().getOrThrow(), isLast = false))

    suspend fun emitPage(page: List<Int>) {
      results.send(Result.success(page))
    }

    suspend fun emitError(error: Exception) {
      results.send(Result.failure(error))
    }
  }
}

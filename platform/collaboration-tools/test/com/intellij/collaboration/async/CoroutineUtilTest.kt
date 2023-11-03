// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CoroutineUtilTest {
  @Test
  fun `Collecting batches works`() = runTest {
    val collectedList = flowOf(listOf(1, 2), listOf(3), listOf(4, 5))
      .collectBatches()
      .last()

    assertEquals(listOf(1, 2, 3, 4, 5), collectedList)
  }

  private sealed interface Action<T> {
    data class Emit<T>(val value: T): Action<T>
  }

  private data object SomeException : Exception() {
    private fun readResolve(): Any = SomeException
  }

  /**
   * Tries to make sure the actions given to this function are executed in-order,
   * even though they might be executed asynchronously.
   */
  private suspend fun <T, R> executeFlowInstructionsAndCollectAsListIn(
    cs: CoroutineScope,
    actions: List<Action<T>>,
    transformer: Flow<T>.() -> Flow<R>
  ): List<R> {
    val mutActions = actions.toMutableList()
    val results = mutableListOf<R>()

    val job = cs.launchNow {
      flow {
        var prevSize = mutActions.size + 1
        while (mutActions.isNotEmpty()) {
          while ((mutActions.isNotEmpty() && mutActions.first() !is Action.Emit) || mutActions.size == prevSize) {
            delay(200)
          }

          if (mutActions.isEmpty()) {
            currentCoroutineContext().cancel()
            ensureActive()
            return@flow
          }

          emit((mutActions.first() as Action.Emit).value)
          prevSize = mutActions.size
        }
      }.transformer()
        .collect {
          results.add(it)
          mutActions.removeFirstOrNull()
        }
    }

    while (mutActions.isNotEmpty()) {
      delay(200)
    }
    job.cancel()

    return results.toList()
  }

  @Test
  fun `Transforming consecutive successes works`() = runTest {
    val actions = listOf(
      Action.Emit(Result.success(listOf(1))),
      Action.Emit(Result.success(listOf(2))),
      Action.Emit(Result.failure(SomeException)),
      Action.Emit(Result.success(listOf(3))),
    )

    val results = executeFlowInstructionsAndCollectAsListIn(this, actions) {
      transformConsecutiveSuccesses { collectBatches() }
    }

    assertContentEquals(listOf(
      Result.success(listOf(1)),
      Result.success(listOf(1, 2)),
      Result.failure(SomeException),
      Result.success(listOf(3)),
    ), results.toList())
  }

  @Test
  fun `Transforming consecutive successes without interrupting works`() = runTest {
    val actions = listOf(
      Action.Emit(Result.success(listOf(1))),
      Action.Emit(Result.success(listOf(2))),
      Action.Emit(Result.failure(SomeException)),
      Action.Emit(Result.success(listOf(3))),
    )

    val results = executeFlowInstructionsAndCollectAsListIn(this, actions) {
      transformConsecutiveSuccesses(false) { collectBatches() }
    }

    assertContentEquals(listOf(
      Result.success(listOf(1)),
      Result.success(listOf(1, 2)),
      Result.failure(SomeException),
      Result.success(listOf(1, 2, 3)),
    ), results.toList())
  }

  @Test
  fun `Transforming consecutive successes resets after every failure`() = runTest {
    val actions = listOf(
      Action.Emit(Result.success(listOf(1))),
      Action.Emit(Result.failure(SomeException)),
      Action.Emit(Result.success(listOf(2))),
      Action.Emit(Result.failure(SomeException)),
      Action.Emit(Result.success(listOf(3))),
      Action.Emit(Result.success(listOf(4))),
      Action.Emit(Result.failure(SomeException)),
      Action.Emit(Result.success(listOf(5))),
    )

    val results = executeFlowInstructionsAndCollectAsListIn(this, actions) {
      transformConsecutiveSuccesses { collectBatches() }
    }

    assertContentEquals(listOf(
      Result.success(listOf(1)),
      Result.failure(SomeException),
      Result.success(listOf(2)),
      Result.failure(SomeException),
      Result.success(listOf(3)),
      Result.success(listOf(3, 4)),
      Result.failure(SomeException),
      Result.success(listOf(5)),
    ), results.toList())
  }
}
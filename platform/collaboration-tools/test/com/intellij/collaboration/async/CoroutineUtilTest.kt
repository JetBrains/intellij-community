// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import app.cash.turbine.test
import com.intellij.util.containers.HashingStrategy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
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

  @Test
  fun `associateCachingBy preserves order`() = runTest {
    val inputFlow = MutableStateFlow(listOf<Int>())
    val outputFlow = inputFlow.associateCachingBy(
      keyExtractor = { it },
      hashingStrategy = HashingStrategy.canonical(),
      valueExtractor = { it }
    )

    outputFlow.test {
      assertThat(awaitItem().values).isEmpty()

      // initial add
      inputFlow.value = listOf(1, 2, 3)
      assertThat(awaitItem().values.toList()).isEqualTo(listOf(1, 2, 3))

      // simple add
      inputFlow.value = listOf(1, 2, 3, 4)
      assertThat(awaitItem().values.toList()).isEqualTo(listOf(1, 2, 3, 4))

      // simple remove
      inputFlow.value = listOf(1, 3, 4)
      assertThat(awaitItem().values.toList()).isEqualTo(listOf(1, 3, 4))

      // simple remove + add
      inputFlow.value = listOf(1, 2, 3)
      assertThat(awaitItem().values.toList()).isEqualTo(listOf(1, 2, 3))
    }
  }

  @Test
  fun `associateCachingBy updates existing values`() = runTest {
    val inputFlow = MutableStateFlow(listOf<UpdatableValue<String>>())
    val outputFlow = inputFlow.associateCachingBy(
      keyExtractor = { it.key },
      hashingStrategy = HashingStrategy.canonical(),
      valueExtractor = { this to it },
      update = { this.second.value = it.value }
    )

    val v1 = UpdatableValue(1, "a")
    val v2 = UpdatableValue(1, "b") // different references

    outputFlow.test {
      assertThat(awaitItem().values).isEmpty()

      // initial add
      inputFlow.value = listOf(v1)

      val emittedItem1 = awaitItem().values.firstOrNull()
      assertThat(emittedItem1?.second).isEqualTo(v1)

      // simple change
      inputFlow.value = listOf(v2)

      val emittedItem2 = awaitItem().values.firstOrNull()
      assertThat(emittedItem2?.second).isEqualTo(v2)          // The right value is emitted according to equality
      assertThat(v1.value).isEqualTo(v2.value).isEqualTo("b") // The initial value was updated, rather than the v2 was emitted
      assertThat(emittedItem2).isSameAs(emittedItem1)         // Sanity-check: it's the same exact reference right?
      assertThat(emittedItem1?.first).isEqualTo(emittedItem2?.first) // The scope was not re-created
    }
  }

  @Test
  fun `associateCachingBy cancels deleted scopes`() = runTest {
    val inputFlow = MutableStateFlow(listOf<Int>())
    val outputFlow = inputFlow.associateCachingBy(
      keyExtractor = { it },
      hashingStrategy = HashingStrategy.canonical(),
      valueExtractor = { this to it },
    )

    outputFlow.test {
      assertThat(awaitItem().values).isEmpty()

      // initial add
      inputFlow.value = listOf(1)

      val emittedItem1 = awaitItem().values.firstOrNull()
      assertThat(emittedItem1?.second).isEqualTo(1)

      // simple remove
      inputFlow.value = listOf()
      assertThat(awaitItem().values).isEmpty()

      assertThat(emittedItem1?.first?.isActive).isFalse()
    }
  }

  @Test
  fun `associateCachingBy does not give two items the same scope`() = runTest {
    val inputFlow = MutableStateFlow(listOf<Int>())
    val outputFlow = inputFlow.associateCachingBy(
      keyExtractor = { it },
      hashingStrategy = HashingStrategy.canonical(),
      valueExtractor = { this to it },
    )

    outputFlow.test {
      assertThat(awaitItem().values).isEmpty()

      // initial add
      inputFlow.value = listOf(1, 2)

      val emitted1 = awaitItem().values.toList()
      assertThat(emitted1.map { it.second }).isEqualTo(listOf(1, 2))
      assertThat(emitted1[0].first).isNotSameAs(emitted1[1].first)
    }
  }

  data class UpdatableValue<T>(
    val key: Int,
    var value: T
  )
}
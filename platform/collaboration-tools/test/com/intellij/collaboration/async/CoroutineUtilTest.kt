// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import app.cash.turbine.test
import com.intellij.collaboration.async.ComputedListChange.Insert
import com.intellij.collaboration.async.ComputedListChange.Remove
import com.intellij.util.containers.HashingStrategy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CoroutineUtilTest {
  @Test
  fun `Collecting batches works`() = runTest {
    val collectedList = flowOf(listOf(1, 2), listOf(3), listOf(4, 5))
      .collectBatches()
      .last()

    assertEquals(listOf(1, 2, 3, 4, 5), collectedList)
  }

  private sealed interface Action<T> {
    data class Emit<T>(val value: T) : Action<T>
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
    transformer: Flow<T>.() -> Flow<R>,
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
    var value: T,
  )

  @Test
  fun `changesFlow sends an initial value`() = runTest {
    val underlying = MutableStateFlow(listOf(1, 2, 3))

    underlying.changesFlow().test(timeout = 1.seconds) {
      assertThat(awaitItem()).isEqualTo(listOf(Insert(0, listOf(1, 2, 3))))

      expectNoEvents()
    }
  }

  @Test
  fun `changesFlow sends an add-update`() = runTest {
    val underlying = MutableStateFlow(listOf(1, 2, 3))

    underlying.changesFlow().test(timeout = 1.seconds) {
      awaitItem() // initial

      underlying.value = listOf(1, 2, 3, 4)
      assertThat(awaitItem()).isEqualTo(listOf(Insert(3, listOf(4))))

      expectNoEvents()
    }
  }

  @Test
  fun `changesFlow sends a remove-update`() = runTest {
    val underlying = MutableStateFlow(listOf(1, 2, 3))

    underlying.changesFlow().test(timeout = 1.seconds) {
      awaitItem() // initial

      underlying.value = listOf(1, 3)
      assertThat(awaitItem()).isEqualTo(listOf(Remove(1, 1)))

      expectNoEvents()
    }
  }

  @Test
  fun `changesFlow sends a list of reproducible changes`() = runTest {
    val l1 = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    val l2 = listOf(1, 3, 2, 7, 20, 10, 2, 5, 7, 3)

    val underlying = MutableStateFlow(l1)

    underlying.changesFlow().test(timeout = 1.seconds) {
      awaitItem() // initial

      underlying.value = l2
      val updates = awaitItem()
      assertThat(applyUpdates(l1, updates)).isEqualTo(l2)

      expectNoEvents()
    }
  }

  // If flaky on TC, just ignore and use locally. It would be because of timings
  // Should take about ~5 seconds due to explicit timeouts
  @Test
  fun `changesFlow fuzzy test`() = runTest {
    // deterministic random
    val rand = Random(123456789)

    fun randomList(): MutableList<Int> {
      val l = mutableListOf(1, 2, 3, 4, 5, 6, 6, 7, 8, 9, 10, 10, 10, 11, 11, 11)
      l.shuffle(rand)
      repeat(rand.nextInt(until = l.size)) {
        l.removeAt(rand.nextInt(until = l.size))
      }
      return l
    }

    (1..10).forEach {
      val underlying = MutableStateFlow(randomList())
      val nUpdates = rand.nextInt(until = 10)

      lateinit var outputState: List<Int>

      underlying.changesFlow().test(timeout = 250.milliseconds) {
        outputState = (awaitItem().first() as Insert<Int>).values

        repeat(nUpdates) { i ->
          // update the list
          underlying.value = randomList()

          // await the coming updates or not
          if (i == nUpdates - 1 || rand.nextBoolean()) {
            var updates: List<ComputedListChange<Int>>? = awaitItem()

            while (updates != null) {
              assertThat(updates).isNotEmpty()
              outputState = applyUpdates(outputState, updates)
              updates = runCatching { awaitItem() }.getOrNull()
            }

            assertThat(outputState).isEqualTo(underlying.value)
          }
        }

        expectNoEvents()
      }
    }
  }

  private fun <V> applyUpdates(base: List<V>, changes: List<ComputedListChange<V>>): List<V> {
    val list = base.toMutableList()
    changes.forEach { change ->
      when (change) {
        is Remove -> repeat(change.length) { list.removeAt(change.atIndex) }
        is Insert -> list.addAll(change.atIndex, change.values)
      }
    }
    return list.toList()
  }
}
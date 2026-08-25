// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.impl

import com.intellij.platform.ide.navigation.impl.TwoPhaseOverflowExecutor
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.concurrent.atomic.AtomicInteger

/**
 * Semantics: a task enters the race only once its `prepare`
 * produced something to apply, the newest of those wins, and one which took the turn but applied nothing gives it back.
 */
class TwoPhaseOverflowExecutorTest {
  private val executor = TwoPhaseOverflowExecutor()

  @Test
  @Timeout(30)
  fun `a task which prepared nothing neither applies nor takes the turn`(): Unit = timeoutRunBlocking {
    val applying = submitParkedInApply()
    applying.awaitParked()

    val actionCalls = AtomicInteger()
    val noop = executor.submit<String, String>(prepare = { null }) {
      actionCalls.incrementAndGet()
      it
    }

    assertThat(noop).isNull()
    assertThat(actionCalls.get()).isEqualTo(0)

    // the task which is applying was neither superseded nor cancelled by the no-op one
    applying.release()
    assertThat(applying.awaitApplied()).isEqualTo(Applied.TOKEN)
  }

  @Test
  @Timeout(30)
  fun `a newer task supersedes the one which is already applying`(): Unit = timeoutRunBlocking {
    val older = submitParkedInApply()
    older.awaitParked()

    assertThat(executor.submit(prepare = { "newer" }) { it }).isEqualTo("newer")

    assertThat(older.awaitWasDroppedForNewer()).isTrue()
  }

  @Test
  @Timeout(30)
  fun `a newer task which prepared something drops an older preparation`(): Unit = timeoutRunBlocking {
    val older = submitParkedInPrepare()
    older.awaitParked()

    assertThat(executor.submit(prepare = { "newer" }) { it }).isEqualTo("newer")

    assertThat(older.awaitWasDroppedForNewer()).isTrue()
    assertThat(older.actionCalls.get()).isEqualTo(0)
  }

  @Test
  @Timeout(30)
  fun `an apply which produced nothing allows an older active preparation to still apply`(): Unit = timeoutRunBlocking {
    val older = submitParkedInPrepare()
    older.awaitParked()

    assertThat(executor.submit<String, String>(prepare = { "newer" }) { null }).isNull()

    older.release()
    assertThat(older.awaitApplied()).isEqualTo(Applied.TOKEN)
    assertThat(older.actionCalls.get()).isEqualTo(1)
  }

  @Test
  @Timeout(30)
  fun `any non-null result counts as legitly applied`(): Unit = timeoutRunBlocking {
    val older = submitParkedInPrepare()
    older.awaitParked()

    assertThat(executor.submit(prepare = { "newer" }) { false }).isFalse()

    assertThat(older.awaitWasDroppedForNewer()).isTrue()
    assertThat(older.actionCalls.get()).isEqualTo(0)
  }

  @Test
  @Timeout(30)
  fun `prepare runs while another task is applying`(): Unit = timeoutRunBlocking {
    val applying = submitParkedInApply()
    applying.awaitParked()

    val preparing = submitParkedInPrepare()
    // the apply phase of the older task does not hold back the prepare phase of the newer one
    preparing.awaitParked()
    assertThat(applying.isRunning).isTrue()

    preparing.release()
    assertThat(preparing.awaitApplied()).isEqualTo(Applied.TOKEN)
    assertThat(applying.awaitWasDroppedForNewer()).isTrue()
  }

  @ParameterizedTest
  @EnumSource(Phase::class)
  @Timeout(30)
  fun `a failure in either phase is rethrown and leaves the executor usable`(failingPhase: Phase): Unit = timeoutRunBlocking {
    val failure = object : Throwable() {}
    val thrown = assertThrows<Throwable> {
      executor.submit(prepare = {
        if (failingPhase == Phase.PREPARE) throw failure
        "prepared"
      }) {
        if (failingPhase == Phase.ACTION) throw failure
        it
      }
    }
    assertSame(failure, thrown)

    assertThat(executor.submit(prepare = { "next" }) { it }).isEqualTo("next")
  }

  @Test
  @Timeout(30)
  fun `cancelling a task inside its apply releases the turn`(): Unit = timeoutRunBlocking {
    val cancelled = submitParkedInApply()
    cancelled.awaitParked()
    cancelled.cancel()

    assertThat(executor.submit(prepare = { "next" }) { it }).isEqualTo("next")
  }

  @Test
  @Timeout(30)
  fun `applies never overlap and the executor stays usable afterwards`(): Unit = timeoutRunBlocking {
    val (maxConcurrentApplies, appliedCount) = executor.withConcurrentRequests(REQUESTS_COUNT)

    assertThat(maxConcurrentApplies).isEqualTo(1)
    assertThat(appliedCount).isGreaterThan(0)
    assertThat(executor.submit(prepare = { "after all tasks" }) { it }).isEqualTo("after all tasks")
  }

  /**
   * Parks inside its `prepare`, so the executor sees it
   * as a preparation which an apply may or may not drop
   */
  private fun CoroutineScope.submitParkedInPrepare(): ParkedTask {
    val parked = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val actionCalls = AtomicInteger()
    val result = async {
      executor.submit(prepare = {
        parked.complete(Unit)
        release.await()
        Applied.TOKEN
      }) {
        actionCalls.incrementAndGet()
        it
      }
    }
    return ParkedTask(result, parked, release, actionCalls)
  }

  /**
   * Starts a task which already holds the turn, parks inside its `action`
   */
  private fun CoroutineScope.submitParkedInApply(): ParkedTask {
    val parked = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val actionCalls = AtomicInteger()
    val result = async {
      executor.submit(prepare = { Applied.TOKEN }) {
        actionCalls.incrementAndGet()
        parked.complete(Unit)
        release.await()
        it
      }
    }
    return ParkedTask(result, parked, release, actionCalls)
  }

  /**
   * Submits [count] requests concurrently, each preparing and applying its own index.
   * A task superseded by a newer one counts as neither applied nor failed.
   *
   * @return (maxConcurrentApplies, appliedCount)
   */
  private suspend fun TwoPhaseOverflowExecutor.withConcurrentRequests(count: Int): Pair<Int, Int> {
    val concurrentApplies = AtomicInteger()
    val maxConcurrentApplies = AtomicInteger()
    val appliedCount = AtomicInteger()

    withContext(Dispatchers.Default) {
      repeat(count) { index ->
        launch {
          try {
            val applied = submit(prepare = {
              yield()
              index
            }) { prepared ->
              val concurrent = concurrentApplies.incrementAndGet()
              maxConcurrentApplies.accumulateAndGet(concurrent) { left, right -> maxOf(left, right) }
              try {
                yield()
                prepared
              }
              finally {
                concurrentApplies.decrementAndGet()
              }
            }
            if (applied != null) {
              appliedCount.incrementAndGet()
            }
          }
          catch (_: CancellationException) {
            // `submit` cancels a child scope when this request loses the race
            ensureActive()
          }
        }
      }
    }

    return Pair(maxConcurrentApplies.get(), appliedCount.get())
  }

  enum class Phase { PREPARE, ACTION }
  // marker value that parked job went through apply completely
  enum class Applied { TOKEN }

  /**
   * A task parked in one of [Phase]. Until [release] called it is parked,
   * which is how a task the executor is expected to drop is held
   */
  private class ParkedTask(
    private val result: Deferred<Applied?>,
    private val parked: CompletableDeferred<Unit>,
    private val release: CompletableDeferred<Unit>,
    val actionCalls: AtomicInteger,
  ) {
    val isRunning: Boolean
      get() = result.isActive

    suspend fun awaitParked() {
      parked.await()
    }

    // eg no more parked
    fun release() {
      release.complete(Unit)
    }

    suspend fun awaitApplied(): Applied? = result.await()

    suspend fun awaitWasDroppedForNewer(): Boolean {
      result.join()
      return result.isCancelled
    }

    suspend fun cancel() {
      result.cancelAndJoin()
    }
  }

  companion object {
    private const val REQUESTS_COUNT: Int = 200
  }
}


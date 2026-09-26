// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.impl

import com.intellij.platform.ide.navigation.impl.TwoPhaseOverflowExecutor
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
  fun `a newer task cancels an older preparation of the same target`(): Unit = timeoutRunBlocking {
    val older = submitParkedInPrepare(preparationKey = "target")
    older.awaitParked()
    val newer = submitParkedInPrepare(preparationKey = "target")
    newer.awaitParked()

    assertThat(older.awaitWasDroppedForNewer()).isTrue()
    assertThat(older.actionCalls.get()).isEqualTo(0)
    assertThat(newer.isRunning).isTrue()
    newer.release()
    assertThat(newer.awaitApplied()).isEqualTo(Applied.TOKEN)
  }

  @Test
  fun `a replaced preparation cannot apply after it returns without a cancellation check`(): Unit = timeoutRunBlocking {
    val parked = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val actionCalls = AtomicInteger()
    val older = async {
      executor.submit(prepare = {
        assertThat(addKey("target")).isTrue()
        withContext(NonCancellable) {
          parked.complete(Unit)
          release.await()
          Applied.TOKEN
        }
      }) {
        actionCalls.incrementAndGet()
        it
      }
    }
    try {
      parked.await()
      // The replacement cancels without taking a turn
      assertThat(executor.submit<String, String>(prepare = {
        assertThat(addKey("target")).isTrue()
        null
      }) { error("The replacement prepares nothing") }).isNull()

      release.complete(Unit)
      older.join()
      assertThat(older.isCancelled).isTrue()
      assertThat(actionCalls.get()).isEqualTo(0)
    }
    finally {
      release.complete(Unit)
    }
  }

  @Test
  fun `a different key cancels an older preparation only after applying`(): Unit = timeoutRunBlocking {
    val older = submitParkedInPrepare(preparationKey = "first target")
    older.awaitParked()
    val newer = submitParkedInPrepare(preparationKey = "second target")
    newer.awaitParked()

    assertThat(older.isRunning).isTrue()
    newer.release()
    assertThat(newer.awaitApplied()).isEqualTo(Applied.TOKEN)
    assertThat(older.awaitWasDroppedForNewer()).isTrue()
    assertThat(older.actionCalls.get()).isEqualTo(0)
  }

  @Test
  fun `a released key lets an unresolved older task prepare the target`(): Unit = timeoutRunBlocking {
    val resolving = CompletableDeferred<Unit>()
    val resolved = CompletableDeferred<Unit>()
    val older = async {
      executor.submit(prepare = {
        resolving.complete(Unit)
        resolved.await()
        assertThat(addKey("target")).isTrue()
        "older"
      }) { it }
    }
    resolving.await()
    val newer = submitParkedInPrepare(preparationKey = "target")
    newer.awaitParked()
    newer.cancel()

    resolved.complete(Unit)
    assertThat(older.await()).isEqualTo("older")
  }

  @Test
  fun `a replacement cancels the owner of any of its aliases`(): Unit = timeoutRunBlocking {
    val ready = CompletableDeferred<Unit>()
    val owner = async {
      executor.submit(prepare = {
        assertThat(addKey("initial target")).isTrue()
        assertThat(addKey("resolved target")).isTrue()
        ready.complete(Unit)
        CompletableDeferred<Unit>().await()
      }) { error("The replaced owner must not apply") }
    }
    ready.await()
    val replacement = submitParkedInPrepare(preparationKey = "resolved target")
    replacement.awaitParked()
    owner.join()
    assertThat(owner.isCancelled).isTrue()

    val otherAlias = submitParkedInPrepare(preparationKey = "initial target")
    otherAlias.awaitParked()
    assertThat(replacement.isRunning).isTrue()
    otherAlias.cancel()
    replacement.release()
    assertThat(replacement.awaitApplied()).isEqualTo(Applied.TOKEN)
  }

  @Test
  fun `the latest target wins across an intervening unresolved submission`(): Unit = timeoutRunBlocking {
    val first = submitParkedInPrepare(preparationKey = "A")
    first.awaitParked()
    val resolving = CompletableDeferred<Unit>()
    val resolved = CompletableDeferred<Unit>()
    val other = async {
      executor.submit(prepare = {
        resolving.complete(Unit)
        resolved.await()
        assertThat(addKey("B")).isTrue()
        "B"
      }) { it }
    }
    resolving.await()
    val latest = submitParkedInPrepare(preparationKey = "A")
    latest.awaitParked()
    assertThat(first.awaitWasDroppedForNewer()).isTrue()

    resolved.complete(Unit)
    assertThat(other.await()).isEqualTo("B")
    assertThat(latest.isRunning).isTrue()
    latest.release()
    assertThat(latest.awaitApplied()).isEqualTo(Applied.TOKEN)
  }

  @ParameterizedTest
  @EnumSource(Phase::class)
  fun `a keyed no-op preserves an older preparation of another target`(emptyPhase: Phase): Unit = timeoutRunBlocking {
    val older = submitParkedInPrepare(preparationKey = "user target")
    older.awaitParked()
    assertThat(executor.submit(prepare = {
      assertThat(addKey("autoscroll target")).isTrue()
      "prepared".takeUnless { emptyPhase == Phase.PREPARE }
    }) { it.takeUnless { emptyPhase == Phase.ACTION } }).isNull()

    assertThat(older.isRunning).isTrue()
    older.release()
    assertThat(older.awaitApplied()).isEqualTo(Applied.TOKEN)
  }

  @Test
  fun `a cancelled replacement leaves the executor usable for the same key`(): Unit = timeoutRunBlocking {
    val first = submitParkedInPrepare(preparationKey = "target")
    first.awaitParked()
    val second = submitParkedInPrepare(preparationKey = "target")
    second.awaitParked()
    assertThat(first.awaitWasDroppedForNewer()).isTrue()
    // The first owner's cleanup must preserve the second owner's entry.
    val third = submitParkedInPrepare(preparationKey = "target")
    third.awaitParked()
    assertThat(second.awaitWasDroppedForNewer()).isTrue()
    third.cancel()

    assertThat(executor.submit(prepare = {
      assertThat(addKey("target")).isTrue()
      42
    }) { it }).isEqualTo(42)
  }

  @Test
  @Timeout(30)
  fun `a newer submission of the same target drops one which waits for its turn`(): Unit = timeoutRunBlocking {
    val stuck = submitStuckInApply()
    try {
      stuck.awaitParked()
      val waiting = submitPreparedFor("target")
      waiting.awaitParked()
      val newest = submitPreparedFor("target")
      newest.awaitParked()

      // the queue behind a stuck apply must not grow by one submission per request of the same target
      waitUntil("a newer submission of the same target must drop the queued one", QUEUE_TIMEOUT) { !waiting.isRunning }
      assertThat(waiting.awaitWasDroppedForNewer()).isTrue()
      assertThat(waiting.actionCalls.get()).isEqualTo(0)
      assertThat(newest.isRunning).isTrue()

      stuck.release()
      assertThat(newest.awaitApplied()).isEqualTo(Applied.TOKEN)
    }
    finally {
      stuck.release()
    }
  }

  @Test
  @Timeout(30)
  fun `a submission which lost the turn does not wait for a stuck apply`(): Unit = timeoutRunBlocking {
    val stuck = submitStuckInApply()
    try {
      stuck.awaitParked()
      val superseded = submitPreparedFor("second target")
      superseded.awaitParked()
      val newest = submitPreparedFor("third target")
      newest.awaitParked()

      waitUntil("a submission which cannot apply must not wait for the stuck one", QUEUE_TIMEOUT) { !superseded.isRunning }
      assertThat(superseded.awaitWasDroppedForNewer()).isTrue()
      assertThat(superseded.actionCalls.get()).isEqualTo(0)

      stuck.release()
      assertThat(newest.awaitApplied()).isEqualTo(Applied.TOKEN)
    }
    finally {
      stuck.release()
    }
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
        assertThat(addKey("target")).isTrue()
        if (failingPhase == Phase.PREPARE) throw failure
        "prepared"
      }) {
        if (failingPhase == Phase.ACTION) throw failure
        it
      }
    }
    assertSame(failure, thrown)

    assertThat(executor.submit(prepare = {
      assertThat(addKey("target")).isTrue()
      "next"
    }) { it }).isEqualTo("next")
  }

  @Test
  @Timeout(30)
  fun `cancelling a task inside its apply releases the turn`(): Unit = timeoutRunBlocking {
    val cancelled = submitParkedInApply()
    cancelled.awaitParked()
    cancelled.cancel()

    assertThat(executor.submit(prepare = {
      assertThat(addKey("target")).isTrue()
      "next"
    }) { it }).isEqualTo("next")
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
  private fun CoroutineScope.submitParkedInPrepare(preparationKey: Any? = null): ParkedTask {
    val parked = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val actionCalls = AtomicInteger()
    val result = async {
      executor.submit(prepare = {
        if (preparationKey != null && !addKey(preparationKey)) {
          return@submit null
        }
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
   * Starts a task which holds the turn and then holds the apply phase even after a cancellation,
   * which is how a slow editor opening keeps the apply phase busy
   */
  private fun CoroutineScope.submitStuckInApply(): ParkedTask {
    val parked = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val actionCalls = AtomicInteger()
    val result = async {
      executor.submit(prepare = { Applied.TOKEN }) {
        actionCalls.incrementAndGet()
        withContext(NonCancellable) {
          parked.complete(Unit)
          release.await()
          it
        }
      }
    }
    return ParkedTask(result, parked, release, actionCalls)
  }

  /**
   * Claims [preparationKey], finishes its prepare, and then waits for its turn to apply
   */
  private fun CoroutineScope.submitPreparedFor(preparationKey: Any): ParkedTask {
    val prepared = CompletableDeferred<Unit>()
    val actionCalls = AtomicInteger()
    val result = async {
      executor.submit(prepare = {
        if (!addKey(preparationKey)) {
          return@submit null
        }
        prepared.complete(Unit)
        Applied.TOKEN
      }) {
        actionCalls.incrementAndGet()
        it
      }
    }
    return ParkedTask(result, prepared, CompletableDeferred(), actionCalls)
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
    private val QUEUE_TIMEOUT: Duration = 10.seconds
  }
}

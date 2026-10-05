// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.util.coroutines.childScope
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.ZERO

/** [awaitEnd] on real IJent scopes, in the virtual time of [runTest]. */
class IjentSessionExitReasonTest {
  @Test
  fun `a failed session returns its failure`() = runTest {
    val ijentScope = ParentOfIjentScopes(backgroundScope).createIjentScope("IjentSessionExitReasonTest")
    val failure = IjentProcessExited("IJent was killed", null)

    ijentScope.destroy(failure)

    val reason = FakeSession(ijentScope).awaitEnd()
    reason.shouldBeInstanceOf<IjentProcessExited>()
    reason.cause shouldBeSameInstanceAs failure
  }

  @Test
  fun `a session closed on purpose returns the closing reason`() = runTest {
    val ijentScope = ParentOfIjentScopes(backgroundScope).createIjentScope("IjentSessionExitReasonTest")
    val closed = EelUnavailableException.IntendedExit("The user refused the prompt", null)

    ijentScope.destroy(closed)

    val reason = FakeSession(ijentScope).awaitEnd()
    reason.shouldBeInstanceOf<EelUnavailableException.IntendedExit>()
    reason.cause shouldBeSameInstanceAs closed
  }

  @Test
  fun `a bug in the session returns a communication failure that wraps it`() = runTest {
    // A bare scope has no completion handler, so a raw throw records no exit reason.
    // `destroy` records the bug as the exit reason, and `resolveExitReason` wraps it for the caller.
    val failure = IllegalStateException("Not an IJent failure")
    val handledFailures = mutableListOf<Throwable>()
    val boundary = backgroundScope.childScope("boundary", CoroutineExceptionHandler { _, e -> handledFailures += e })
    val ijentScope = IjentScope(ParentOfIjentScopes(backgroundScope), boundary, "IjentSessionExitReasonTest")
    ijentScope.destroy(failure)
    val start = testScheduler.timeSource.markNow()

    val reason = FakeSession(ijentScope).awaitEnd()
    reason.shouldBeInstanceOf<EelUnavailableException.CommunicationFailure>()
    reason.cause shouldBeSameInstanceAs failure

    start.elapsedNow() shouldBe ZERO
    handledFailures.shouldContainExactly(failure)
  }

  @Test
  fun `a cancelled caller does not affect the session`() = runTest {
    val ijentScope = ParentOfIjentScopes(backgroundScope).createIjentScope("IjentSessionExitReasonTest")
    val caller = launch { FakeSession(ijentScope).awaitEnd() }
    testScheduler.runCurrent()

    caller.cancelAndJoin()

    caller.isCancelled shouldBe true
    ijentScope.s.coroutineContext.job.isActive shouldBe true
  }

  @Test
  fun `an ended session with a recorded reason returns it at once`() = runTest {
    val ijentScope = ParentOfIjentScopes(backgroundScope).createIjentScope("IjentSessionExitReasonTest")
    val closed = EelUnavailableException.IntendedExit("The session closed", null)
    ijentScope.destroy(closed)
    ijentScope.s.coroutineContext.job.join()
    val start = testScheduler.timeSource.markNow()

    val reason = FakeSession(ijentScope).awaitEnd()
    reason.shouldBeInstanceOf<EelUnavailableException.IntendedExit>()
    reason.cause shouldBeSameInstanceAs closed

    start.elapsedNow() shouldBe ZERO
  }

  private class FakeSession(ijentScope: IjentScope) : IjentSession {
    @DelicateCoroutinesApi
    override val sessionCoroutineScope: IjentScope = ijentScope

    override val isRunning: Boolean get() = error("Unused")
    override val platform: EelPlatform get() = error("Unused")
    override val remotePathToBinary: String get() = error("Unused")
    override suspend fun updateLogLevel(): Unit = error("Unused")
    override suspend fun setParentProcessToWatch(pid: Long): Unit = error("Unused")
    override fun close(): Unit = error("Unused")
    override fun getIjentInstance(descriptor: EelDescriptor): IjentApi = error("Unused")
    override val eventBus: IjentEventBus get() = error("Unused")
  }
}

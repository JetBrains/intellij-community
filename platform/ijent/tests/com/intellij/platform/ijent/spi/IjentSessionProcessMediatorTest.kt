// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.EelUnavailableException.CommunicationFailure
import com.intellij.platform.eel.EelUnavailableException.IntendedExit
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendApi
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.PeekableEelReceiveChannel
import com.intellij.platform.eel.channels.peekable
import com.intellij.platform.eel.provider.utils.EelPipe
import com.intellij.platform.ijent.IjentScope
import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.platform.util.coroutines.childScope
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

/**
 * Tests how [IjentSessionProcessMediator] classifies an expected exit of IJent that comes while the session waits for a root cause.
 */
class IjentSessionProcessMediatorTest {
  @Test
  fun `an expected exit after IJent closed its stdout wins over a later symptom`(): Unit = runBlocking {
    withParentScope { parent, uncaught ->
      val ijentScope = ParentOfIjentScopes(parent).createIjentScope("test-session")
      val process = FakeIjentProcess()
      val mediator = IjentSessionProcessMediator.create(ParentOfIjentScopes(parent), ijentScope, process, "test-session")
      mediator.useStdoutAsTransport()

      // IJent closes its side of the transport first, for example because its container is stopped.
      val transportRead = ijentScope.s.async { mediator.process.stdout.receive(ByteBuffer.allocate(1)) }
      process.closeStdout()
      transportRead.await() shouldBe ReadResult.EOF

      // The gRPC client sees the closed transport only after that.
      ijentScope.destroy(IllegalStateException("IJent session ended for no known reason: gRPC UNAVAILABLE"))
      process.exit(0)

      // The exit is a root cause, so it must not wait for DEAD_SESSION_RESOLVE_TIMEOUT.
      withTimeout(IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT - 1.seconds) {
        ijentScope.resolveExitReason().shouldBeInstanceOf<IntendedExit>()
      }
      parent.coroutineContext.job.children.toList().joinAll()
      uncaught.shouldBeEmpty()
    }
  }

  @Test
  fun `IJent closes its stdout just after an expected exit, and the exit wins over an earlier symptom`(): Unit = runBlocking {
    withParentScope { parent, uncaught ->
      val ijentScope = ParentOfIjentScopes(parent).createIjentScope("test-session")
      val process = FakeIjentProcess()
      val mediator = IjentSessionProcessMediator.create(ParentOfIjentScopes(parent), ijentScope, process, "test-session")
      mediator.useStdoutAsTransport()
      // The read outlives the failed IJent scope, as a transport read in the IJent thread pool does.
      val transportRead = parent.async { mediator.process.stdout.receive(ByteBuffer.allocate(1)) }

      // A write to the stdin of the ending IJent fails before the IDE reads the end of the stdout.
      ijentScope.destroy(IllegalStateException("IJent session ended for no known reason: broken pipe"))
      // The exit code comes a moment before the end of the stdout.
      process.exitWithoutClosingStdout(0)
      process.closeStdout()
      transportRead.await() shouldBe ReadResult.EOF

      withTimeout(IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT - 1.seconds) {
        ijentScope.resolveExitReason().shouldBeInstanceOf<IntendedExit>()
      }
      parent.coroutineContext.job.children.toList().joinAll()
      uncaught.shouldBeEmpty()
    }
  }

  @Test
  fun `IJent closes its stdout after a symptom and before the exit, and the symptom stays`(): Unit = runBlocking {
    withParentScope { parent, uncaught ->
      val ijentScope = ParentOfIjentScopes(parent).createIjentScope("test-session")
      val process = FakeIjentProcess()
      val mediator = IjentSessionProcessMediator.create(ParentOfIjentScopes(parent), ijentScope, process, "test-session")
      mediator.useStdoutAsTransport()
      val transportRead = parent.async { mediator.process.stdout.receive(ByteBuffer.allocate(1)) }

      // The session fails first. It cancels its transport, and IJent closes its stdout and exits normally because of it.
      val bug = IllegalStateException("A bug in the session")
      ijentScope.destroy(bug)
      process.closeStdout()
      transportRead.await() shouldBe ReadResult.EOF
      process.exit(0)

      val resolved = withTimeout(IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT + 5.seconds) {
        ijentScope.resolveExitReason(IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT + 5.seconds)
      }
      resolved.shouldBeInstanceOf<CommunicationFailure>().cause shouldBeSameInstanceAs bug
      parent.coroutineContext.job.children.toList().joinAll()
      uncaught.map { it.message }.shouldContainExactly(bug.message)
    }
  }

  @Test
  fun `an expected exit after a symptom does not hide the symptom`(): Unit = runBlocking {
    withParentScope { parent, uncaught ->
      val ijentScope = ParentOfIjentScopes(parent).createIjentScope("test-session")
      val process = FakeIjentProcess()
      IjentSessionProcessMediator.create(ParentOfIjentScopes(parent), ijentScope, process, "test-session")

      // The session fails first. It cancels its transport, and IJent exits normally because of it.
      val bug = IllegalStateException("A bug in the session")
      ijentScope.destroy(bug)
      process.exit(0)

      val resolved = withTimeout(IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT + 5.seconds) {
        ijentScope.resolveExitReason(IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT + 5.seconds)
      }
      resolved.shouldBeInstanceOf<CommunicationFailure>().cause shouldBeSameInstanceAs bug
      parent.coroutineContext.job.children.toList().joinAll()
      uncaught.map { it.message }.shouldContainExactly(bug.message)
    }
  }

  @Test
  fun `an end of the stdout before it becomes the transport is not a close by IJent`(): Unit = runBlocking {
    withParentScope { parent, uncaught ->
      val ijentScope = ParentOfIjentScopes(parent).createIjentScope("test-session")
      val process = FakeIjentProcess()
      val mediator = IjentSessionProcessMediator.create(ParentOfIjentScopes(parent), ijentScope, process, "test-session")

      // In the TCP mode, the IDE reads the port of IJent from the stdout. IJent exits with the code 0 before it writes the port.
      val portRead = ijentScope.s.async { mediator.process.stdout.receive(ByteBuffer.allocate(2)) }
      process.closeStdout()
      portRead.await() shouldBe ReadResult.EOF

      val symptom = CommunicationFailure("IJent stdout closed before TCP port was read", null)
      ijentScope.destroy(symptom)
      process.exit(0)

      val resolved = withTimeout(IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT + 5.seconds) {
        ijentScope.resolveExitReason(IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT + 5.seconds)
      }
      resolved.shouldBeInstanceOf<CommunicationFailure>().cause shouldBeSameInstanceAs symptom
      parent.coroutineContext.job.children.toList().joinAll()
      uncaught.shouldBeEmpty()
    }
  }

  @Test
  fun `a reconnect forgets an earlier close of the transport by IJent`(): Unit = runBlocking {
    withParentScope { parent, uncaught ->
      val ijentScope = ParentOfIjentScopes(parent).createIjentScope("test-session")
      val process = FakeIjentProcess()
      val mediator = IjentSessionProcessMediator.create(ParentOfIjentScopes(parent), ijentScope, process, "test-session")

      // A TCP transport lost its connection, and then connected to the same IJent again.
      mediator.onTransportClosedByIjent()
      mediator.onTransportReconnected()

      val bug = IllegalStateException("A bug in the session")
      ijentScope.destroy(bug)
      process.exit(0)

      val resolved = withTimeout(IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT + 5.seconds) {
        ijentScope.resolveExitReason(IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT + 5.seconds)
      }
      resolved.shouldBeInstanceOf<CommunicationFailure>().cause shouldBeSameInstanceAs bug
      parent.coroutineContext.job.children.toList().joinAll()
      uncaught.map { it.message }.shouldContainExactly(bug.message)
    }
  }

  @Test
  fun `finalizer destroys the process when closing stdin hangs`(): Unit = runBlocking {
    withParentScope { parent, uncaught ->
      val ijentScope = ParentOfIjentScopes(parent).createIjentScope("test-session")
      val stdinCloseEntered = CompletableDeferred<Unit>()
      val process = HangingStdinProcess(stdinCloseEntered)
      IjentSessionProcessMediator.create(ParentOfIjentScopes(parent), ijentScope, process, "test-session")

      try {
        ijentScope.destroy(IntendedExit("Session closed", null))
        // We chose 5 seconds at random above the 1.5-second stdin close timeout to avoid test flakiness.
        // You can change this value freely as long as it stays above the finalizer timeout.
        withTimeout(5.seconds) {
          stdinCloseEntered.await()
          process.exitCode.await() shouldBe 143
          ijentScope.s.coroutineContext.job.join()
        }
        parent.coroutineContext.job.children.toList().joinAll()
        uncaught.shouldBeEmpty()
      }
      finally {
        process.unblockStdinClose()
      }
    }
  }

  /** Runs [body] with a supervisor scope that collects the errors that the IJent scope reports to the application. */
  private suspend fun withParentScope(body: suspend (CoroutineScope, List<Throwable>) -> Unit): Unit = coroutineScope {
    val uncaught = Collections.synchronizedList(mutableListOf<Throwable>())
    val parent = childScope("application", CoroutineExceptionHandler { _, err -> uncaught.add(err) }, supervisor = true)
    try {
      body(parent, uncaught)
    }
    finally {
      parent.cancel()
    }
  }
}

private class FakeIjentProcess : IjentSessionProcessMediator.ProcessFacade {
  private val stdinPipe = EelPipe("fake IJent stdin", prefersDirectBuffers = false)
  private val stdoutPipe = EelPipe("fake IJent stdout", prefersDirectBuffers = false)
  private val stderrPipe = EelPipe("fake IJent stderr", prefersDirectBuffers = false)
  private val exitCodeImpl = CompletableDeferred<Int>()

  override val stdin: EelSendChannel = stdinPipe.sink
  override val stdout: PeekableEelReceiveChannel = stdoutPipe.source.peekable()
  override val stderr: EelReceiveChannel = stderrPipe.source
  override val exitCode: SafeDeferred<Int> = SafeDeferred(exitCodeImpl)
  override val destroyIsGraceful: Boolean = true

  suspend fun closeStdout() {
    stdoutPipe.sink.close(null)
  }

  suspend fun exit(code: Int) {
    stdoutPipe.sink.close(null)
    exitWithoutClosingStdout(code)
  }

  suspend fun exitWithoutClosingStdout(code: Int) {
    stderrPipe.sink.close(null)
    exitCodeImpl.complete(code)
  }

  override suspend fun destroyForcibly() {
    exit(137)
  }

  override suspend fun destroy() {
    exit(143)
  }
}

private class HangingStdinProcess(
  private val stdinCloseEntered: CompletableDeferred<Unit>,
) : IjentSessionProcessMediator.ProcessFacade {
  private val stdoutPipe = EelPipe("hanging IJent stdout", prefersDirectBuffers = false)
  private val stderrPipe = EelPipe("hanging IJent stderr", prefersDirectBuffers = false)
  private val exitCodeImpl = CompletableDeferred<Int>()
  private val stdinCloseUnblocked = CompletableDeferred<Unit>()

  override val stdin: EelSendChannel = object : EelSendChannel {
    override val isClosed: Boolean = false
    override val prefersDirectBuffers: Boolean = false

    @EelSendApi
    override suspend fun send(src: ByteBuffer) {
      src.position(src.limit())
    }

    override suspend fun close(err: Throwable?) {
      stdinCloseEntered.complete(Unit)
      stdinCloseUnblocked.await()
    }
  }
  override val stdout: PeekableEelReceiveChannel = stdoutPipe.source.peekable()
  override val stderr: EelReceiveChannel = stderrPipe.source
  override val exitCode: SafeDeferred<Int> = SafeDeferred(exitCodeImpl)
  override val destroyIsGraceful: Boolean = false

  fun unblockStdinClose() {
    stdinCloseUnblocked.complete(Unit)
  }

  private suspend fun exit(code: Int) {
    stdoutPipe.sink.close(null)
    stderrPipe.sink.close(null)
    exitCodeImpl.complete(code)
    stdinCloseUnblocked.complete(Unit)
  }

  override suspend fun destroyForcibly() {
    exit(137)
  }

  override suspend fun destroy() {
    exit(143)
  }
}

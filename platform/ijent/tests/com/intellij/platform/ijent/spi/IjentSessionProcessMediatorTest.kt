// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.EelUnavailableException.CommunicationFailure
import com.intellij.platform.eel.EelUnavailableException.IntendedExit
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelReceiveChannelException
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.PeekableEelReceiveChannel
import com.intellij.platform.eel.channels.peekable
import com.intellij.platform.eel.channels.sendWholeBuffer
import com.intellij.platform.eel.provider.utils.EelPipe
import com.intellij.platform.eel.provider.utils.readWholeText
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import java.io.FileInputStream
import java.io.FilterInputStream
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Tests how [IjentSessionProcessMediator] classifies an expected exit of IJent that comes while the session waits for a root cause.
 */
@Suppress("checkedExceptions") // An unexpected error of a channel or of an exit code fails the test, and the test does not handle it.
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
  fun `a failed read of the stdout after the exit of IJent wins over a later symptom`(): Unit = runBlocking {
    withParentScope { parent, uncaught ->
      val ijentScope = ParentOfIjentScopes(parent).createIjentScope("test-session")
      val process = FakeIjentProcess()
      val mediator = IjentSessionProcessMediator.create(
        ParentOfIjentScopes(parent), ijentScope, process, "test-session", isExpectedProcessExit = { it == 137 },
      )
      mediator.useStdoutAsTransport()
      val transportRead = ijentScope.s.async { runCatching { mediator.process.stdout.receive(ByteBuffer.allocate(1)) } }

      // The container of IJent stops. The read of the stdout fails instead of EOF, before the IDE gets the exit code.
      process.exitWithBrokenStdout()
      transportRead.await().exceptionOrNull().shouldBeInstanceOf<EelReceiveChannelException>()

      // The gRPC client sees the broken transport only after that.
      ijentScope.destroy(IllegalStateException("IJent session ended for no known reason: gRPC UNAVAILABLE"))
      process.exitWithoutClosingStdout(137)

      // The exit is a root cause, so it must not wait for DEAD_SESSION_RESOLVE_TIMEOUT.
      withTimeout(IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT - 1.seconds) {
        ijentScope.resolveExitReason().shouldBeInstanceOf<IntendedExit>()
      }
      parent.coroutineContext.job.children.toList().joinAll()
      uncaught.shouldBeEmpty()
    }
  }

  @Test
  fun `a failed read of the stdout after the exit of IJent wins over an earlier symptom`(): Unit = runBlocking {
    withParentScope { parent, uncaught ->
      val ijentScope = ParentOfIjentScopes(parent).createIjentScope("test-session")
      val process = FakeIjentProcess()
      val mediator = IjentSessionProcessMediator.create(
        ParentOfIjentScopes(parent), ijentScope, process, "test-session", isExpectedProcessExit = { it == 137 },
      )
      mediator.useStdoutAsTransport()
      // The read outlives the failed IJent scope, as a transport read in the IJent thread pool does.
      val transportRead = parent.async { runCatching { mediator.process.stdout.receive(ByteBuffer.allocate(1)) } }

      // A write to the stdin of the ending IJent fails before the IDE reads the stdout.
      ijentScope.destroy(IllegalStateException("IJent session ended for no known reason: broken pipe"))
      process.exitWithBrokenStdout()
      transportRead.await().exceptionOrNull().shouldBeInstanceOf<EelReceiveChannelException>()
      process.exitWithoutClosingStdout(137)

      withTimeout(IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT - 1.seconds) {
        ijentScope.resolveExitReason().shouldBeInstanceOf<IntendedExit>()
      }
      parent.coroutineContext.job.children.toList().joinAll()
      uncaught.shouldBeEmpty()
    }
  }

  @Test
  fun `a failed read of the stdout while IJent is alive is not a close by IJent`(): Unit = runBlocking {
    withParentScope { parent, uncaught ->
      val ijentScope = ParentOfIjentScopes(parent).createIjentScope("test-session")
      val process = FakeIjentProcess()
      val mediator = IjentSessionProcessMediator.create(ParentOfIjentScopes(parent), ijentScope, process, "test-session")
      mediator.useStdoutAsTransport()
      val transportRead = parent.async { runCatching { mediator.process.stdout.receive(ByteBuffer.allocate(1)) } }

      // The session fails first. It breaks its side of the transport, and IJent exits normally because of it.
      val bug = IllegalStateException("A bug in the session")
      ijentScope.destroy(bug)
      process.breakStdout()
      transportRead.await().exceptionOrNull().shouldBeInstanceOf<EelReceiveChannelException>()
      process.exitWithoutClosingStdout(0)

      val resolved = withTimeout(IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT + 5.seconds) {
        ijentScope.resolveExitReason(IjentScope.DEAD_SESSION_RESOLVE_TIMEOUT + 5.seconds)
      }
      resolved.shouldBeInstanceOf<CommunicationFailure>().cause shouldBeSameInstanceAs bug
      parent.coroutineContext.job.children.toList().joinAll()
      uncaught.map { it.message }.shouldContainExactly(bug.message)
    }
  }

  @Test
  fun `the stdout and the stderr of a killed process end with their last bytes and EOF`(): Unit = runBlocking {
    assumeFalse(SystemInfoRt.isWindows, "Only the JDK on Unix closes the pipes of an exited process")
    withParentScope { parent, _ ->
      val ijentScope = ParentOfIjentScopes(parent).createIjentScope("test-session")
      val process = ProcessBuilder("/bin/sh", "-c", "printf out; printf err >&2; read _; kill -9 $$").start()
      val facade = IjentSessionProcessMediator.JavaProcessFacade(ijentScope, process)

      // The process kills itself, as IJent is killed when its container stops.
      facade.stdin.sendWholeBuffer(ByteBuffer.wrap("\n".toByteArray()))
      facade.exitCode.await() shouldBe 137
      awaitPipesClosedByJdk(process)

      facade.stdout.readWholeText() shouldBe "out"
      facade.stderr.readWholeText() shouldBe "err"
    }
  }

  /** Waits until the JDK moves the unread bytes of the pipes of the exited [process] into its own streams. */
  private suspend fun awaitPipesClosedByJdk(process: Process) {
    val inField = FilterInputStream::class.java.getDeclaredField("in").apply { isAccessible = true }
    withTimeout(10.seconds) {
      while (inField.get(process.inputStream) is FileInputStream || inField.get(process.errorStream) is FileInputStream) {
        delay(10.milliseconds)
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

  @Volatile
  private var exitedWithoutExitCode = false

  override val isAlive: Boolean
    get() = !exitedWithoutExitCode && super.isAlive

  suspend fun closeStdout() {
    stdoutPipe.sink.close(null)
  }

  /** The read of the stdout fails instead of EOF. */
  suspend fun breakStdout() {
    stdoutPipe.sink.close(ClosedChannelException())
  }

  /**
   * The process exits, and the read of its stdout fails instead of EOF. The exit code comes later, see [exitWithoutClosingStdout].
   * The JDK on Unix does so: it marks the process as exited, and then closes the pipe of the stdout.
   */
  suspend fun exitWithBrokenStdout() {
    exitedWithoutExitCode = true
    breakStdout()
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

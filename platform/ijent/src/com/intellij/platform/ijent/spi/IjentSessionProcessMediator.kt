// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.ThrowsChecked
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelReceiveChannelException
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.PeekableEelReceiveChannel
import com.intellij.platform.eel.channels.peekable
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import com.intellij.platform.ijent.IjentChildProcessAdapter
import com.intellij.platform.ijent.IjentLogger
import com.intellij.platform.ijent.IjentScope
import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.platform.ijent.asyncSafeInParent
import com.intellij.platform.ijent.coroutineNameAppended
import com.intellij.platform.ijent.spi.IjentSessionProcessMediator.ProcessExitPolicy.CHECK_CODE
import com.intellij.platform.ijent.spi.IjentSessionProcessMediator.ProcessExitPolicy.NORMAL
import com.intellij.platform.ijent.spi.IjentSessionProcessMediator.ProcessExitPolicy.TRANSPORT_CLOSED_BY_IJENT
import com.intellij.platform.util.annotations.VisibleToClasses
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A wrapper for a [Process] that runs IJent. The wrapper logs stderr lines, waits for the exit code, terminates the process in case
 * of problems in the IDE.
 *
 * [processExit] never throws. When it completes, it either means that the process has finished, or that the whole scope of IJent processes
 * is canceled.
 *
 * [ijentProcessScope] should be used by the [com.intellij.platform.ijent.IjentApi] implementation for launching internal coroutines.
 * No matter if IJent exits expectedly or not, an attempt to do anything with [ijentProcessScope] after the IJent has exited
 * throws [EelUnavailableException].
 */
class IjentSessionProcessMediator private constructor(
  override val ijentProcessScope: IjentScope,
  rawProcess: ProcessFacade,
  internal val exitsOnStdinEof: Boolean,
) : IjentSessionMediator {
  /**
   * The mediator notices the end of the stdout of this facade, and calls [onTransportClosedByIjent] after [useStdoutAsTransport].
   * Before that call, the end of the stdout is a failure of the start, not a close of the transport by IJent.
   */
  val process: ProcessFacade = StdoutEndTrackingFacade(rawProcess, ::onStdoutEnd)

  @Volatile
  private var stdoutIsTransport: Boolean = false

  /**
   * The transport layer calls it when the stdout of IJent becomes the transport of the session.
   * Only then the end of the stdout is a close of the transport by IJent. See [onTransportClosedByIjent].
   *
   * In the TCP mode and in the Hyper-V mode, IJent writes only its connection details there.
   * An end of the stdout before them is a failure of the start, and it must not hide the symptom that reports it.
   */
  fun useStdoutAsTransport() {
    stdoutIsTransport = true
  }

  private fun onStdoutEnd() {
    if (stdoutIsTransport) {
      onTransportClosedByIjent()
    }
  }

  interface ProcessFacade {
    val stdin: EelSendChannel
    val stdout: PeekableEelReceiveChannel
    val stderr: EelReceiveChannel
    val exitCode: SafeDeferred<Int>
    suspend fun destroyForcibly()
    suspend fun destroy()

    val destroyIsGraceful: Boolean

    val isAlive: Boolean
      get() = when (exitCode.state) {
        SafeDeferred.State.Active -> true
        is SafeDeferred.State.Finished -> false
      }
  }

  @OptIn(DelicateCoroutinesApi::class)
  @VisibleToClasses(
    "com.intellij.platform.ijent.spi.IjentBinaryCacheTest",
    "com.intellij.platform.ijent.spi.IjentDeployingOverShellProcessStrategy",
    "com.intellij.platform.ijent.spi.IjentShellBootstrapTest",
  )
  internal class JavaProcessFacade(ijentProcessScope: IjentScope, private val process: Process) : ProcessFacade {
    init {
      require(process is IjentChildProcessAdapter || process.javaClass.name == "java.lang.ProcessImpl") {
        "This code performs black magic with internals of java.lang.ProcessImpl, this class is not supported: ${process.javaClass.name}"
      }
    }

    override val stdin: EelSendChannel =
      rawSendChannel(process.outputStream)

    override val stdout: PeekableEelReceiveChannel =
      rawReceiveChannel(process.inputStream).peekable()

    override val stderr: EelReceiveChannel =
      rawReceiveChannel(process.errorStream)

    private fun rawSendChannel(stream: OutputStream): EelSendChannel =
      if (process is IjentChildProcessAdapter) stream.asEelChannel(IjentThreadPool.coroutineContext)
      else stream.extractRawProcessStream().asEelChannel(IjentThreadPool.coroutineContext)

    private fun rawReceiveChannel(stream: InputStream): EelReceiveChannel =
      if (process is IjentChildProcessAdapter) stream.consumeAsEelChannel(IjentThreadPool.coroutineContext)
      else stream.extractRawProcessStream().consumeAsEelChannel(IjentThreadPool.coroutineContext)

    // Pin the blocking `Process.waitFor()` call to `IjentThreadPool` via the explicit
    // `runInterruptible` context. `Process.awaitExit()` would otherwise route the JDK
    // wait through `runInterruptible(Dispatchers.IO)`, parking a `DefaultDispatcher-worker-*`
    // thread that `ThreadLeakTracker.wellKnownOffenders` does not whitelist — so the
    // watcher would be reported as a leak whenever the ijent session is still alive at
    // the moment leak detection runs (e.g. an IDE Starter test on WSL where the manager
    // scope outlives the test). `IjentThreadPool-` is whitelisted, and `runInterruptible`
    // still delivers a thread interrupt on cancellation.
    override val exitCode: SafeDeferred<Int> = ijentProcessScope.asyncSafeInParent {
      runInterruptible(IjentThreadPool.coroutineContext) {
        @Suppress("UsePlatformProcessAwaitExit")
        process.waitFor()
      }
      process.exitValue()
    }
    override val isAlive: Boolean get() = process.isAlive

    override val destroyIsGraceful: Boolean =
      runCatching { process.supportsNormalTermination() }.getOrDefault(!SystemInfoRt.isWindows)

    override suspend fun destroyForcibly() {
      withContext(blockingDispatcher) {
        process.destroyForcibly()
      }
    }

    override suspend fun destroy() {
      withContext(blockingDispatcher) {
        process.destroy()
      }
    }
  }

  override val processExit: SafeDeferred<Unit> = process.exitCode.map { Unit }

  /**
   * Defines how process exits should be handled in terms of error reporting.
   * Used to determine whether a process termination should be treated as an error.
   */
  internal enum class ProcessExitPolicy {
    /**
     * Check exit code to determine if it's an error.
     * Normal termination with expected exit codes is allowed.
     */
    CHECK_CODE,

    /**
     * IJent closed its side of the transport while the session was still healthy. See [onTransportClosedByIjent].
     * So the symptoms of the session follow from the end of IJent, and not vice versa.
     * An expected exit code then becomes the exit reason, also when the session already waits for a root cause.
     *
     * Without it, the session itself can be the cause of the exit.
     * A failing session cancels its transport at once, and IJent can exit normally because of it.
     */
    TRANSPORT_CLOSED_BY_IJENT,

    /**
     * Normal shutdown, never treat as error.
     * Used during intentional process termination.
     */
    NORMAL,
  }

  @Volatile
  internal var myExitPolicy: ProcessExitPolicy = CHECK_CODE

  /** `true` after [onTransportClosedByIjent] that came after the exit of the process. */
  private val transportClosedByIjentAfterExit = MutableStateFlow(false)

  /**
   * `true` if the session started to fail before IJent closed the transport.
   * Then an expected exit code is a consequence of that failure, and it must not hide the symptom as a root cause.
   *
   * The process can report its exit code a moment before the IDE reads the end of the transport.
   * So, if IJent closes the transport within [TRANSPORT_CLOSE_AFTER_EXIT_TIMEOUT] after the exit, IJent closed it first.
   * A close before the exit that came after a symptom does not count. The failing session caused it.
   */
  internal suspend fun isExitConsequenceOfSessionFailure(): Boolean {
    if (!ijentProcessScope.isResolvingExitReason) return false
    return when (myExitPolicy) {
      NORMAL -> true
      TRANSPORT_CLOSED_BY_IJENT -> false
      CHECK_CODE -> withTimeoutOrNull(TRANSPORT_CLOSE_AFTER_EXIT_TIMEOUT) { transportClosedByIjentAfterExit.first { it } } == null
    }
  }

  /**
   * A transport of the session calls it when IJent closes its side of the transport in an orderly way.
   * For the stdio transport, it is the end of the stdout of IJent. For a TCP transport, it is a close of the connection by IJent.
   * A reset or a timeout of the connection is not a close by IJent.
   *
   * The call changes the exit policy only while the session is healthy. See [ProcessExitPolicy.TRANSPORT_CLOSED_BY_IJENT].
   * A call just after an expected exit code also counts. See [isExitConsequenceOfSessionFailure].
   */
  fun onTransportClosedByIjent() {
    if (!process.isAlive) {
      transportClosedByIjentAfterExit.value = true
    }
    val sessionIsHealthy = !ijentProcessScope.isResolvingExitReason && ijentProcessScope.exitReasonOrNull == null
    myExitPolicy = when (val myExitPolicy = myExitPolicy) {
      CHECK_CODE if sessionIsHealthy -> TRANSPORT_CLOSED_BY_IJENT
      CHECK_CODE, TRANSPORT_CLOSED_BY_IJENT, NORMAL -> myExitPolicy
    }
  }

  /**
   * A transport that can reconnect calls it when it connects to IJent again.
   * IJent is alive then, so an earlier [onTransportClosedByIjent] no longer tells anything about the end of IJent.
   */
  fun onTransportReconnected() {
    myExitPolicy = when (val myExitPolicy = myExitPolicy) {
      TRANSPORT_CLOSED_BY_IJENT -> CHECK_CODE
      CHECK_CODE, NORMAL -> myExitPolicy
    }
  }

  companion object {
    /** IJent closes its transport before it exits, but the IDE can notice it a moment after the exit code. */
    private val TRANSPORT_CLOSE_AFTER_EXIT_TIMEOUT: Duration = 100.milliseconds

    fun create(
      parentScope: ParentOfIjentScopes,
      process: Process,
      ijentLabel: String,
      isExpectedProcessExit: suspend (exitCode: Int) -> Boolean = { it == 0 },
      exitsOnStdinEof: Boolean = true,
    ): IjentSessionProcessMediator {
      val ijentProcessScope = parentScope.createIjentScope(ijentLabel)
      return create(
        parentScope,
        ijentProcessScope,
        JavaProcessFacade(ijentProcessScope, process),
        ijentLabel,
        isExpectedProcessExit,
        exitsOnStdinEof,
      )
    }

    /**
     * See the docs of [IjentSessionProcessMediator].
     *
     * [ijentLabel] is used only for logging.
     *
     * Beware that [parentScope] receives [EelUnavailableException.CommunicationFailure] if IJent _suddenly_ exits, f.i., after SIGKILL.
     * Nothing happens with [parentScope] if IJent exits expectedly, f.i., after [com.intellij.platform.ijent.IjentApi.close].
     */
    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    internal fun create(
      parentScope: ParentOfIjentScopes,
      ijentProcessScope: IjentScope,
      process: ProcessFacade,
      ijentLabel: String,
      isExpectedProcessExit: suspend (exitCode: Int) -> Boolean = { it == 0 },
      exitsOnStdinEof: Boolean = true,
      foreignStderrLog: IjentForeignStderrLog = IjentForeignStderrLog.Default,
    ): IjentSessionProcessMediator {
      val context = IjentThreadPool.coroutineContext

      val lastStderrMessages = MutableSharedFlow<String?>(
        replay = 30,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )

      // stderr logger should outlive the current scope. In case if an error appears, the scope is cancelled immediately, but the whole
      // intention of the stderr logger is to write logs of the remote process, which come from the remote machine to the local one with
      // a delay.
      GlobalScope.launch(IjentThreadPool.coroutineContext + ijentProcessScope.s.coroutineNameAppended("stderr logger")) {
        IjentSessionMediatorUtils.ijentProcessStderrLogger(process.stderr, ijentLabel, lastStderrMessages, foreignStderrLog)
      }

      val mediator = IjentSessionProcessMediator(ijentProcessScope, process, exitsOnStdinEof)

      val awaiterScope = ijentProcessScope.s.launch(
        context = context + ijentProcessScope.s.coroutineNameAppended("exit awaiter scope"),
        start = CoroutineStart.UNDISPATCHED,
      ) {
        @Suppress("checkedExceptions")
        val exitCode = try {
          process.exitCode.await()
        }
        catch (cancelled: CancellationException) {
          // A channel coroutine can fail just before the process publishes its exit code. Give finalizer cleanup a
          // bounded chance to expose the authoritative code, without letting a broken facade hold the scope forever.
          withContext(NonCancellable) {
            withTimeoutOrNull(5.seconds) { process.exitCode.await() }
          } ?: throw cancelled
        }

        // Cancellation may race the suspendable expected-exit check and stderr collection. Once the code is known,
        // classification and publication must complete atomically with respect to session cancellation.
        withContext(NonCancellable) {
          IjentLogger.LIFETIME_LOG.debug { "IJent process $ijentLabel exited with code $exitCode" }
          val isExitExpected = isExpectedProcessExit(exitCode)
          IjentSessionMediatorUtils.ijentProcessExitCodeHandler(
            ijentLabel,
            lastStderrMessages,
            exitCode,
            isExitExpected,
            exitFollowsSessionFailure = isExitExpected && mediator.isExitConsequenceOfSessionFailure(),
          )
        }
      }

      val finalizerScope = ijentProcessScope.s.launch(
        context = context + ijentProcessScope.s.coroutineNameAppended("finalizer scope"),
        start = CoroutineStart.UNDISPATCHED,
      ) {
        IjentSessionMediatorUtils.ijentProcessFinalizer(ijentLabel) { ijentProcessFinalizer(ijentLabel, mediator) }
      }

      awaiterScope.invokeOnCompletion { err ->
        val exitReason = ijentProcessScope.exitReasonOrNull
        if (exitReason is EelUnavailableException.IntendedExit) {
          ijentProcessScope.destroy(exitReason)
        }
        finalizerScope.cancel(if (err != null) CancellationException(err.message, err) else null)
      }

      return mediator
    }
  }
}

private suspend fun ijentProcessFinalizer(ijentLabel: String, mediator: IjentSessionProcessMediator) {
  mediator.myExitPolicy = NORMAL
  val process = mediator.process

  if (!process.isAlive) return

  try {
    IjentLogger.LIFETIME_LOG.debug { "Closing stdin of $ijentLabel" }
    runCatching { process.stdin.close(null) }

    // On Windows process.destroy() is an abrupt kill, so if ijent can react to stdin EOF, let's wait for a bit before destroying
    if (!process.destroyIsGraceful && mediator.exitsOnStdinEof) {
      awaitProcessExit(process, 1.5.seconds)
      if (!process.isAlive) return
    }

    process.destroy()

    awaitProcessExit(process, 1.5.seconds)

    if (process.isAlive) {
      IjentLogger.LIFETIME_LOG.warn("The process $ijentLabel is still alive, it will be killed")
      process.destroyForcibly()
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    IjentLogger.LIFETIME_LOG.warn("Failed to terminate $ijentLabel", e)
  }
}

private class StdoutEndTrackingFacade(
  private val delegate: IjentSessionProcessMediator.ProcessFacade,
  onStdoutEnd: () -> Unit,
) : IjentSessionProcessMediator.ProcessFacade by delegate {
  override val stdout: PeekableEelReceiveChannel = EndListeningReceiveChannel(delegate.stdout, onStdoutEnd).peekable()
}

private class EndListeningReceiveChannel(
  private val delegate: EelReceiveChannel,
  private val onEnd: () -> Unit,
) : EelReceiveChannel {
  @ThrowsChecked(EelReceiveChannelException::class)
  override suspend fun receive(dst: ByteBuffer): ReadResult {
    val result = delegate.receive(dst)
    when (result) {
      ReadResult.EOF -> onEnd()
      ReadResult.NOT_EOF -> Unit
    }
    return result
  }

  @ThrowsChecked(EelReceiveChannelException::class)
  @EelDelicateApi
  override fun available(): Int = delegate.available()

  override suspend fun closeForReceive() {
    delegate.closeForReceive()
  }

  override val prefersDirectBuffers: Boolean
    get() = delegate.prefersDirectBuffers
}

private suspend fun awaitProcessExit(process: IjentSessionProcessMediator.ProcessFacade, timeout: Duration) {
  val deadline = System.nanoTime() + timeout.inWholeNanoseconds
  while (process.isAlive && System.nanoTime() < deadline) {
    delay(50.milliseconds)
  }
}

private tailrec fun InputStream.extractRawProcessStream(): ReadableByteChannel {
  return when (this) {
    is FileInputStream -> channel
    is BufferedInputStream -> FilterInputStream::class.java
      .getDeclaredField("in")
      .apply { this.isAccessible = true }
      .get(this)
      .let { it as InputStream }
      .extractRawProcessStream()
    else -> throw IllegalStateException("Unexpected stream type: ${this::class.java}")
  }
}

private fun OutputStream.extractRawProcessStream(): WritableByteChannel {
  return when (this) {
    is FileOutputStream -> channel
    is BufferedOutputStream -> FilterOutputStream::class.java
      .getDeclaredField("out")
      .apply { this.isAccessible = true }
      .get(this)
      .let { it as OutputStream }
      .extractRawProcessStream()
    else -> throw IllegalStateException("Unexpected stream type: ${this::class.java}")
  }
}
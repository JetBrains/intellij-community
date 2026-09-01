// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.ReadResult.EOF
import com.intellij.platform.eel.ReadResult.NOT_EOF
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.channels.EelChannelException
import com.intellij.platform.eel.channels.sendWholeBuffer
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.platform.ijent.IjentLogger
import com.intellij.platform.ijent.IjentScope
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.IjentUnavailableException
import com.intellij.platform.ijent.IjentUnavailableException.CommunicationFailure
import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.platform.ijent.getIjentGrpcArgv
import com.intellij.platform.ijent.spi.IjentSessionMediatorUtils.readLineOrThrow
import com.intellij.platform.ijent.tcp.MutualTlsCertificates
import com.intellij.platform.ijent.tcp.TcpDeployInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.fileSize
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// The timeout is based on internal measurements done on CI (max: 21.5s, p98: 12.2s)
private val DEFAULT_SHELL_INITIALIZATION_TIMEOUT: Duration =
  (System.getProperty("ijent.shell.initialization.timeout")?.toLongOrNull() ?: 30_000L).milliseconds

private val PROCESS_CLEANUP_TIMEOUT: Duration = 3_000.milliseconds

/** See `community/platform/ijent/docs/shell-deploy-lifetime.md` for the process ownership and the error strategy. */
abstract class IjentDeployingOverShellProcessStrategy(
  scope: ParentOfIjentScopes,
  currentDispatcher: CoroutineDispatcher,
) : IjentControlledEnvironmentDeployingStrategy() {
  protected abstract val ijentLabel: String

  /**
   * If there's some bind mount, returns the path for the remote machine/container that corresponds to [path].
   * Otherwise, returns null.
   */
  protected abstract suspend fun mapPath(path: Path): String?

  protected abstract suspend fun createShellProcessFacade(ijentProcessScope: IjentScope): IjentSessionProcessMediator.ProcessFacade

  abstract class JavaProcessBasedStrategy(protected val scope: ParentOfIjentScopes, currentDispatcher: CoroutineDispatcher) :
    IjentDeployingOverShellProcessStrategy(scope, currentDispatcher) {
    protected abstract suspend fun createShellProcess(): Process

    override suspend fun createShellProcessFacade(ijentProcessScope: IjentScope): IjentSessionProcessMediator.ProcessFacade {
      return IjentSessionProcessMediator.JavaProcessFacade(ijentProcessScope, createShellProcess())
    }
  }

  protected sealed interface ExecutionStrategy {
    data object Default : ExecutionStrategy

    /**
     * [tlsCertificates] intentionally has no default value: every deployer must state explicitly whether the TCP socket
     * of IJent is protected with mutual TLS or is left plaintext and unauthenticated.
     *
     * [noShutdownOnDisconnect] makes IJent outlive the death of its gRPC peer; in exchange, an explicit close asks it
     * to terminate in-band (the flag is mirrored into [IjentConnectionContext.noShutdownOnDisconnect]). Only a deployer
     * that supervises the transport may set it: for everyone else a lost connection is the only stop signal there is,
     * and losing it would leave an orphan behind.
     */
    data class Tcp(
      val deployInfo: TcpDeployInfo,
      val tlsCertificates: MutualTlsCertificates?,
      val noShutdownOnDisconnect: Boolean = false,
    ) : ExecutionStrategy
  }

  protected open val executionStrategy: ExecutionStrategy = ExecutionStrategy.Default

  final override val noShutdownOnDisconnect: Boolean
    get() = when (val strategy = executionStrategy) {
      ExecutionStrategy.Default -> false
      is ExecutionStrategy.Tcp -> strategy.noShutdownOnDisconnect
    }

  protected enum class ShellDialect { POSIX, POWERSHELL }

  protected open suspend fun getShellDialect(): ShellDialect = ShellDialect.POSIX

  /**
   * Interruption strategy for the initial shell setup.
   *
   * Runs [block] (the `set -e` / banner-filtering handshake) and decides how/whether to abort it
   * if the target shell never becomes responsive. The base implementation aborts after a fixed
   * timeout; deployers may override to apply a different bound, a deployer-specific abort condition,
   * or none at all. The timeout is an implementation detail and is intentionally NOT part of this contract.
   */
  protected open suspend fun <T> withShellInitializationInterruption(block: suspend () -> T): T =
    withTimeout(DEFAULT_SHELL_INITIALIZATION_TIMEOUT) { block() }

  private val communicationStartedImpl = CompletableDeferred<Unit>()

  /**
   * Signals that the process has actually started the process of deploying.
   */
  val communicationStarted: SafeDeferred<Unit> = SafeDeferred(communicationStartedImpl)

  private val closed = AtomicBoolean()
  /** Non-null while the deployer owns cleanup; cleared on close or when the session takes ownership. */
  private var createdShellProcess: ShellProcessWrapper? = null

  private val myContext: Deferred<ShellSession> = scope.s.async(currentDispatcher, start = CoroutineStart.LAZY) {
    val ijentProcessScope = IjentSessionMediatorUtils.createProcessScope(scope, ijentLabel)
    val processFacade = createShellProcessFacade(ijentProcessScope)
    val mediator = IjentSessionProcessMediator.create(
      parentScope = scope,
      ijentProcessScope = ijentProcessScope,
      process = processFacade,
      ijentLabel = ijentLabel,
      isExpectedProcessExit = ::isExpectedProcessExit,
      exitsOnStdinEof = when (executionStrategy) {
        ExecutionStrategy.Default -> true
        is ExecutionStrategy.Tcp -> false
      },
    )
    val shell = ShellProcessWrapper(processFacade, mediator)
    check(createdShellProcess == null) { "The deployment shell process was already created" }
    createdShellProcess = shell
    if (closed.get()) {
      createdShellProcess = null
      shell.close()
      currentCoroutineContext().ensureActive()
    }
    withShellInitializationInterruption {
      val shellIo = when (getShellDialect()) {
        ShellDialect.POSIX -> PosixShellIo(shell)
        ShellDialect.POWERSHELL -> PowerShellIo(shell)
      }
      when (shellIo) {
        is PosixShellIo -> {
          val debugOption = if (LOG.isDebugEnabled) "x" else ""
          shell.write("set -e$debugOption")
          currentCoroutineContext().ensureActive()
          shellIo.synchronize()
        }
        is PowerShellIo -> {
          shellIo.executeCommand("\$ErrorActionPreference = 'Stop'; [Console]::OutputEncoding = [System.Text.Encoding]::UTF8")
        }
      }
      communicationStartedImpl.complete(Unit)
      when (shellIo) {
        is PosixShellIo -> PosixShellSession(shellIo, createDeployingContext(shellIo))
        is PowerShellIo -> PowerShellSession(shellIo)
      }
    }
  }

  private val myDetectedTarget = scope.s.async(currentDispatcher, start = CoroutineStart.LAZY) {
    val session = getMyContext()
    session.execCommand {
      detectTarget()
    }
  }

  override suspend fun getTargetPlatform(): EelPlatform =
    getDetectedTarget().platform

  private suspend fun getDetectedTarget(): DetectedTarget {
    return try {
      myDetectedTarget.await()
    }
    catch (e: CancellationException) {
      currentCoroutineContext().ensureActive()
      throw RuntimeException("Cancellation during target platform retrieval", e)
    }
  }

  final override suspend fun createProcess(binaryPath: String): IjentSessionProcessMediator {
    val target = getDetectedTarget()
    val launchOptions = when (val strategy = executionStrategy) {
      ExecutionStrategy.Default -> IjentLaunchOptions.Default
      is ExecutionStrategy.Tcp -> IjentLaunchOptions.Tcp(
        deployInfo = strategy.deployInfo,
        tlsCertificates = strategy.tlsCertificates,
        noShutdownOnDisconnect = strategy.noShutdownOnDisconnect,
      )
    }
    return target.session.execCommand {
      target.execIjent(
        binaryPath,
        launchOptions,
      )
    }
  }

  final override suspend fun copyFile(file: Path): String {
    return getMyContext().execCommand {
      uploadBinary(file, mapPath(file))
    }
  }

  private suspend fun getMyContext(): ShellSession =
    try {
      myContext.await()
    }
    catch (e: CancellationException) {
      currentCoroutineContext().ensureActive()
      throw RuntimeException("Cancellation during context retrieval", e)
    }

  final override fun close() {
    if (!closed.compareAndSet(false, true)) return

    myContext.cancel(CancellationException("Closed explicitly"))
    createdShellProcess?.close()
    createdShellProcess = null
  }

  final override fun onSessionConnected(ijentSession: IjentSession) {
    checkNotNull(createdShellProcess) {
      "IJent deployer '$ijentLabel': onSessionConnected ran before createProcess created the shell process. Deployer closed=${closed.get()}."
    }
    createdShellProcess = null
  }

  override suspend fun getConnectionStrategy(): IjentConnectionStrategy = IjentConnectionStrategy.Default
}

private class ShellProcessWrapper(
  private val process: IjentSessionProcessMediator.ProcessFacade,
  private val mediator: IjentSessionProcessMediator,
) {
  private val cleanupStarted = AtomicBoolean()

  suspend fun write(data: String) {
    write(data, logPayload = true)
  }

  /**
   * Same as [write], but the payload never reaches the log, because it carries a private key.
   */
  suspend fun writeSensitive(data: String) {
    write(data, logPayload = false)
  }

  /** Writes generated data that is too large to be useful in the debug log. */
  suspend fun writeUnlogged(data: String) {
    write(data, logPayload = false)
  }

  private suspend fun write(data: String, logPayload: Boolean) {
    @Suppress("NAME_SHADOWING")
    val data = if (data.endsWith("\n")) data else "$data\n"
    LOG.debug {
      val debugData =
        if (logPayload) data.replace(Regex("\n\n+")) { "<\\n ${it.value.length} times>\n" }
        else "<payload omitted, ${data.length} bytes>"
      "Executing a script inside the shell: $debugData"
    }
    try {
      withContext(Dispatchers.IO) {
        process.stdin.sendWholeText(data)
      }
    }
    catch (e: EelChannelException) {
      throw CommunicationFailure("Failed to write to the deployment shell", e)
    }
  }

  suspend fun readRawLine(): String {
    // TODO The encoding can be different.
    return process.stdout.readLineOrThrow(StandardCharsets.UTF_8)
  }

  suspend fun copyDataFrom(stream: ByteChannel) {
    val buffer =
      if (process.stdin.prefersDirectBuffers) ByteBuffer.allocateDirect(64 * 1024)
      else ByteBuffer.allocate(64 * 1024)
    val input = stream.consumeAsEelChannel()
    try {
      while (true) {
        when (input.receive(buffer)) {
          EOF -> break
          NOT_EOF -> Unit
        }
        buffer.flip()
        process.stdin.sendWholeBuffer(buffer)
        buffer.clear()
      }
    }
    catch (e: EelChannelException) {
      throw CommunicationFailure("Failed to stream the IJent binary to the deployment shell", e)
    }
  }

  /** Returns a failure observed while terminating a process that is still owned by the deployer. */
  @OptIn(InternalCoroutinesApi::class)
  suspend fun destroyForciblyAndGetError(): Throwable? = withContext(NonCancellable) {
    var cleanupFailure: Exception? = null
    val cleanupStartsNow = cleanupStarted.compareAndSet(false, true)
    val processTerminationWasRequested = when (mediator.process.exitCode.state) {
      SafeDeferred.State.Active -> cleanupStartsNow && mediator.process.isAlive
      is SafeDeferred.State.Finished -> false
    }
    val job = mediator.ijentProcessScope.s.coroutineContext.job
    val processCompleted = withTimeoutOrNull(PROCESS_CLEANUP_TIMEOUT) {
      if (processTerminationWasRequested) {
        try {
          mediator.process.destroyForcibly()
        }
        catch (e: Exception) {
          cleanupFailure = e

          val error = IjentUnavailableException.ClosedByApplication(
            "Failed to destroy the shell process during deployment cleanup",
            e,
          )

          terminateProcessScope(error)
        }
      }
      job.join()
    } != null
    if (!processCompleted && cleanupFailure == null) {
      val timeoutFailure = CommunicationFailure("Timed out while terminating the deployment shell process", null)
      cleanupFailure = timeoutFailure
      terminateProcessScope(IjentUnavailableException.ClosedByApplication(timeoutFailure.message, timeoutFailure))
    }
    val processFailure =
      if (processCompleted && !processTerminationWasRequested) {
        IjentUnavailableException.unwrapFromCancellationExceptions(job.getCancellationException())
      }
      else null
    processFailure?.also { failure ->
      cleanupFailure?.let(failure::addSuppressed)
    } ?: cleanupFailure
  }

  private fun terminateProcessScope(error: IjentUnavailableException) {
    mediator.ijentProcessScope.s.launch(start = CoroutineStart.UNDISPATCHED) {
      currentCoroutineContext()[IjentScope.IjentContext.Key]!!
        .completeExitReason(error)
      throw error
    }
  }

  fun processForConnection(): IjentSessionProcessMediator = mediator

  fun close() {
    if (cleanupStarted.compareAndSet(false, true)) {
      mediator.ijentProcessScope.s.cancel(CancellationException("Deployment closed before process handoff"))
    }
  }
}

/** Dialect-specific IO on top of [ShellProcessWrapper]: one implementation per shell dialect. */
private sealed interface ShellCommandIo {
  val process: ShellProcessWrapper

  /** Reads one output line in the dialect's line-ending convention. */
  suspend fun readLine(): String

  /** Runs [command] to completion and returns its output lines. */
  suspend fun executeCommand(command: String): List<String>

  /** Sends [command] whose process takes over the shell; returns once the shell reached the command. */
  suspend fun startProcess(command: String, sensitive: Boolean = false)

  /** Waits until the shell consumed every previously sent command. */
  suspend fun synchronize()
}

/** Reads and drops every line until [boundary]: leftovers of previous commands, banners, and other noise. */
private suspend fun ShellCommandIo.dropOutputUntil(boundary: String) {
  var line = readLine()
  while (line != boundary) {
    LOG.debug { "Dropped shell output while waiting for $boundary: $line" }
    line = readLine()
  }
}

private suspend fun ShellCommandIo.readCommandOutput(start: String, end: String): List<String> {
  dropOutputUntil(start)

  val output = mutableListOf<String>()
  while (true) {
    val line = readLine()
    if (line == end) return output
    output += line
  }
}

private class PosixShellIo(override val process: ShellProcessWrapper) : ShellCommandIo {
  override suspend fun readLine(): String = process.readRawLine()

  override suspend fun executeCommand(command: String): List<String> {
    val boundary = randomBoundary()
    val start = "${boundary}_START"
    val end = "${boundary}_END"
    process.write("echo $start; $command; echo $end")
    return readCommandOutput(start, end)
  }

  override suspend fun startProcess(command: String, sensitive: Boolean) {
    val boundary = randomBoundary()
    val script = "echo $boundary; $command"
    if (sensitive) process.writeSensitive(script) else process.write(script)
    dropOutputUntil(boundary)
  }

  override suspend fun synchronize() {
    val boundary = randomBoundary()
    process.write("echo $boundary")
    dropOutputUntil(boundary)
  }
}

private class PowerShellIo(override val process: ShellProcessWrapper) : ShellCommandIo {
  // PowerShell emits CRLF even when transported through a Unix-oriented SSH channel.
  override suspend fun readLine(): String = process.readRawLine().removeSuffix("\r")

  override suspend fun executeCommand(command: String): List<String> {
    val boundary = randomBoundary()
    val start = "${boundary}_START"
    val end = "${boundary}_END"
    process.write("Write-Output '$start'; $command; Write-Output '$end'")
    return readCommandOutput(start, end)
  }

  override suspend fun startProcess(command: String, sensitive: Boolean) {
    val boundary = randomBoundary()
    val script = "Write-Output '$boundary'; $command"
    if (sensitive) process.writeSensitive(script) else process.write(script)
    dropOutputUntil(boundary)
  }

  override suspend fun synchronize() {
    val boundary = randomBoundary()
    process.write("Write-Output '$boundary'")
    dropOutputUntil(boundary)
  }
}

private interface ShellSession {
  val io: ShellCommandIo

  suspend fun detectTarget(): DetectedTarget
  suspend fun uploadBinary(localBinary: Path, mappedPath: String?): String
}

private sealed interface DetectedTarget {
  val platform: EelPlatform
  val session: ShellSession

  suspend fun execIjent(
    remoteBinaryPath: String,
    launchOptions: IjentLaunchOptions,
  ): IjentSessionProcessMediator
}

private data class DetectedPosixTarget(
  override val session: PosixShellSession,
  override val platform: EelPlatform.Posix,
) : DetectedTarget {
  override suspend fun execIjent(
    remoteBinaryPath: String,
    launchOptions: IjentLaunchOptions,
  ): IjentSessionProcessMediator =
    session.execIjent(remoteBinaryPath, launchOptions, platform)
}

private data class DetectedWindowsTarget(
  override val session: PowerShellSession,
  override val platform: EelPlatform.Windows,
) : DetectedTarget {
  override suspend fun execIjent(
    remoteBinaryPath: String,
    launchOptions: IjentLaunchOptions,
  ): IjentSessionProcessMediator =
    session.execIjent(remoteBinaryPath, launchOptions)
}

private sealed interface IjentLaunchOptions {
  data object Default : IjentLaunchOptions

  data class Tcp(
    val deployInfo: TcpDeployInfo,
    val tlsCertificates: MutualTlsCertificates?,
    val noShutdownOnDisconnect: Boolean,
  ) : IjentLaunchOptions
}

private data class IjentLaunchCommand(
  val argv: List<String>,
  val tlsCertificates: MutualTlsCertificates?,
)

private fun IjentLaunchOptions.command(remoteBinaryPath: String): IjentLaunchCommand =
  when (this) {
    IjentLaunchOptions.Default -> IjentLaunchCommand(
      argv = getIjentGrpcArgv(remoteBinaryPath, selfDeleteOnExit = true),
      tlsCertificates = null,
    )
    is IjentLaunchOptions.Tcp -> IjentLaunchCommand(
      argv = getIjentGrpcArgv(
        remoteBinaryPath,
        selfDeleteOnExit = true,
        noShutdownOnDisconnect = noShutdownOnDisconnect,
        deployInfo = deployInfo,
        useTLS = tlsCertificates != null,
      ),
      tlsCertificates = tlsCertificates,
    )
  }

private suspend fun <T : Any> ShellSession.execCommand(block: suspend ShellSession.() -> T): T {
  return try {
    block()
  }
  catch (initialErrorFromStack: Exception) {
    val errorFromScope = io.process.destroyForciblyAndGetError()
    val errorFromStack = IjentUnavailableException.unwrapFromCancellationExceptions(initialErrorFromStack)

    // A process failure may be hidden behind CancellationException. Prefer the canonical failure from the process scope in that case.
    // Other errors may be programmer bugs and must retain their original type so that they reach the error reporter.
    // A null errorFromScope means the process was killed by this cleanup itself, so the stack error is the root cause.
    val mainError =
      when {
        errorFromScope == null -> errorFromStack
        errorFromStack is IjentUnavailableException -> errorFromStack
        errorFromStack is CancellationException -> errorFromScope
        else -> errorFromStack
      }

    for (secondaryError in listOfNotNull(errorFromStack, errorFromScope)) {
      if (mainError !== secondaryError && mainError.suppressed.none { it === secondaryError }) {
        mainError.addSuppressed(secondaryError)
      }
    }

    throw if (mainError is IOException && mainError !is IjentUnavailableException) {
      CommunicationFailure("Deployment shell command failed", mainError)
    }
    else {
      mainError
    }
  }
}

@VisibleForTesting
internal data class DeployingContext(
  val chmod: String,
  val cp: String,
  val cut: String,
  val env: String,
  val head: String,
  val mktemp: String,
  val rm: String,
  val sed: String,
  val tail: String,
  val uname: String,
  val whoami: String,

  /** `getent` is a part of glibc, therefore it is absent on macOS and other BSD-like systems. */
  val getent: String?,

  /** Although `id` is defined by POSIX, there's no guarantee that a stripped down system has it. */
  val id: String?,
)

/**
 * There are distributions like rancher-desktop-data where /bin/busybox exists, but there are no symlinks to uname, head, etc.
 *
 * This tricky function checks if the necessary core utils exist and tries to substitute them with busybox otherwise.
 */
private suspend fun createDeployingContext(
  shellIo: PosixShellIo,
): DeployingContext =
  createDeployingContext { commands ->
    val whichCmd = buildString {
      append("set +e; ")
      for (command in commands) {
        append("type $command 1>&2 && echo $command; ")
      }
      append("set -e")
    }
    shellIo.executeCommand(whichCmd)
  }

@VisibleForTesting
internal suspend fun createDeployingContext(filterAvailableBinariesCmd: suspend (commands: Collection<String>) -> Collection<String>): DeployingContext {
  // This strange at first glance code helps reduce copy-paste errors.
  val requiredCommands: Set<String> = setOf(
    "busybox",
    "chmod",
    "cp",
    "cut",
    "env",
    "head",
    "mktemp",
    "rm",
    "sed",
    "tail",
    "uname",
    "whoami",
  )

  // Not every operating system has these utilities, and the deployment can be performed without any of them.
  val optionalCommands: Set<String> = setOf(
    "getent",
    "id",
  )

  val outputOfWhich = mutableListOf<String>()

  fun getOptionalCommandPath(name: String): String? {
    assert(name in optionalCommands)
    return when {
      name in outputOfWhich -> name
      "busybox" in outputOfWhich -> "busybox $name"
      else -> null
    }
  }

  fun getCommandPath(name: String): String {
    assert(name in requiredCommands)
    return when {
      name in outputOfWhich -> name
      "busybox" in outputOfWhich -> "busybox $name"
      else -> throw CommunicationFailure(setOf("busybox", name).joinToString(prefix = "The remote machine has none of: "), null)
    }
  }

  outputOfWhich += filterAvailableBinariesCmd(requiredCommands + optionalCommands)

  return DeployingContext(
    chmod = getCommandPath("chmod"),
    cp = getCommandPath("cp"),
    cut = getCommandPath("cut"),
    env = getCommandPath("env"),
    head = getCommandPath("head"),
    mktemp = getCommandPath("mktemp"),
    rm = getCommandPath("rm"),
    sed = getCommandPath("sed"),
    tail = getCommandPath("tail"),
    uname = getCommandPath("uname"),
    whoami = getCommandPath("whoami"),
    getent = getOptionalCommandPath("getent"),
    id = getOptionalCommandPath("id"),
  )
}

private class PosixShellSession(
  override val io: PosixShellIo,
  private val context: DeployingContext,
) : ShellSession {
  override suspend fun detectTarget(): DetectedPosixTarget {
    // There are two arguments in `uname` that can show the process architecture: `-m` and `-p`. According to `man uname`, `-p` is more
    // verbose, and that information may be sufficient for choosing the right binary.
    // https://man.freebsd.org/cgi/man.cgi?query=uname&sektion=1
    //
    // All known implementations of `uname`, including busybox and GNU coreutils, print the fields in the same order regardless of the order
    // of the options: the operating system, the machine, the processor. A single call saves a round trip, and round trips are expensive
    // on slow connections.
    val unameOutput = io.executeCommand("${context.uname} -spm")
      .flatMap { it.split(" ") }
      .filter(String::isNotEmpty)
    if (unameOutput.isEmpty()) {
      throw CommunicationFailure("Empty output of `uname`", null)
    }

    val osName = unameOutput.first()
    val arch = unameOutput.drop(1).toSet()

    // Linux calls the 64-bit ARM architecture `aarch64`, while macOS calls the same architecture `arm64`.
    val targetArchName = arch.firstOrNull {
      when (EelPlatform.resolveArch(it)) {
        EelPlatform.Arch.X86_64, EelPlatform.Arch.ARM_64 -> true
        EelPlatform.Arch.X86, EelPlatform.Arch.ARM_32, EelPlatform.Arch.Unknown, null -> false
      }
    } ?: throw CommunicationFailure("No binary for architecture $arch", null)

    val platform = EelPlatform.getFor(osName, targetArchName) as? EelPlatform.Posix
                   ?: throw CommunicationFailure("No binary for the operating system $osName", null)
    return DetectedPosixTarget(this, platform)
  }

  override suspend fun uploadBinary(localBinary: Path, mappedPath: String?): String {
    // TODO Don't upload a new binary every time if the binary is already on the server. However, hashes must be checked.
    val ijentBinarySize = localBinary.fileSize()

    // This trap owns the temporary directory until IJent is launched. It also cleans up when upload or shell setup fails midway.
    io.process.write(context.run {
      "BINARY_DIR=\"\$($mktemp -d)\"; BINARY=\"\$BINARY_DIR/ijent\"; trap '$rm -rf \"\$BINARY_DIR\"' 0;\n"
    })

    val chmodAndEcho = context.run {
      "$chmod 500 \"\$BINARY\"; echo \"\$BINARY\";\n"
    }

    if (mappedPath != null) {
      io.process.write(context.run {
        "$cp ${posixQuote(mappedPath)} \$BINARY; $chmodAndEcho"
      })
    }
    else {
      io.process.write(context.run {
        // The file `$BINARY.tmp` will contain the following content:
        // <\n * (random number in 0..filler_size)> + useful data + <\n + filler_size>
        // The script below extracts the useful data and puts it into `$BINARY`.
        // It hasn't been checked if `LC_ALL` really needed for sed/head/tail, this variable
        // is overridden just in case.
        //
        // It is important to send all these commands with a single expression and wait for the output.
        // `head` on macOS (not on BSD) reads data greedily.
        // Even though there's a correct limiter in the source code of `head`
        //    https://github.com/apple-oss-distributions/text_cmds/blob/592aaf8a50aa5810ee8183df20f0ba48bb23aa7e/head/head.c#L210-L214
        // it uses `fread` that reads data ahead
        //    https://github.com/apple-oss-distributions/Libc/blob/main/stdio/FreeBSD/makebuf.c#L250
        // It's not clear how to trigger unbuffered I/O (env var STDBUF0=U does not work). Also, the MAXSIZE from the source above
        // is also unreliable, macOS can make this buffer for Unix sockets bigger.
        // Therefore, exploiting the trick with BUGGY_DASH_BUFFER_FILLER again would be unreliable.
        $$"""
        $$head -c $${ijentBinarySize + BUGGY_DASH_BUFFER_FILLER.length} > $BINARY.tmp; \
        BYTES_TO_SKIP=$(LC_ALL=C $$sed -n -e '/./{=;q;}' $BINARY.tmp | LC_ALL=C $$head -n1); \
        LC_ALL=C $$tail -c+$BYTES_TO_SKIP $BINARY.tmp | LC_ALL=C $$head -c $$ijentBinarySize > $BINARY; \
        $$rm -f $BINARY.tmp; \
        $$chmodAndEcho
        """.trimIndent()
      })

      LOG.debug { "Writing workaround command for Dash (1 of 2)" }
      io.process.write(BUGGY_DASH_BUFFER_FILLER)
      LOG.debug { "Writing $ijentBinarySize bytes of IJent binary into the stream" }
      withContext(Dispatchers.IO) {
        Files.newByteChannel(localBinary, StandardOpenOption.READ).use { stream ->
          io.process.copyDataFrom(stream)
        }
      }
      LOG.debug { "Sent the IJent binary $localBinary" }
      LOG.debug { "Writing workaround command for Dash (2 of 2)" }
      io.process.write(BUGGY_DASH_BUFFER_FILLER)
    }

    return io.readLine()
  }

  suspend fun execIjent(
    remoteBinaryPath: String,
    launchOptions: IjentLaunchOptions,
    targetPlatform: EelPlatform.Posix,
  ): IjentSessionProcessMediator {
    val launchCommand = launchOptions.command(remoteBinaryPath)
    val joinedCommand = launchCommand.argv.joinToString(" ")
    val tlsBootstrap =
      if (launchCommand.tlsCertificates != null) {
        " <<'$TLS_HEREDOC_DELIMITER'\n${launchCommand.tlsCertificates.serverBootstrapPem()}$TLS_HEREDOC_DELIMITER"
      }
      else ""
    val command = context.run {
      val remoteBinaryDir = remoteBinaryPath.substringBeforeLast('/')
      val cleanupCommand = "$rm -rf ${posixQuote(remoteBinaryDir)}"
      // Every `exec` replaces the current shell and its traps, so install the same exit cleanup at each shell layer.
      val innerCommand = "trap ${posixQuote(cleanupCommand)} 0; exec $joinedCommand"
      val loginShellCommand = "exec /bin/sh -c ${posixQuote(innerCommand)}"
      """
      | cd ${posixQuote(remoteBinaryDir)};
      | export SHELL=${getLoginShellCmd(targetPlatform)?.let { "\"${'$'}($it)\"" } ?: "''"};
      | if [ -z "${'$'}SHELL" ]; then export SHELL='/bin/sh' ; fi;
      | trap ${posixQuote(cleanupCommand)} 0;
      | exec "${'$'}SHELL" -c ${posixQuote(loginShellCommand)}
      """.trimMargin() + tlsBootstrap
    }
    io.startProcess(command, sensitive = launchCommand.tlsCertificates != null)
    return io.process.processForConnection()
  }
}

/**
 * Returns a shell command that writes the login shell of the current user into stdout, or null if there's no known way to get it
 * on [targetPlatform].
 */
private fun DeployingContext.getLoginShellCmd(targetPlatform: EelPlatform.Posix): String? =
  when (targetPlatform) {
    is EelPlatform.Linux, is EelPlatform.HarmonyOS ->
      getent?.let { """$it passwd "${'$'}($whoami)" | $cut -d: -f7""" }

    // BSD-like systems, including macOS, don't have `getent`, but their `id` prints the whole passwd entry with the option `-P`.
    // GNU coreutils, in contrast, don't have such an option in `id`.
    is EelPlatform.Darwin, is EelPlatform.FreeBSD ->
      id?.let { """$it -P "${'$'}($whoami)" | $cut -d: -f10""" }
  }

private class PowerShellSession(
  override val io: PowerShellIo,
) : ShellSession {
  private var uploadedBinaryDirectory: String? = null

  override suspend fun detectTarget(): DetectedWindowsTarget {
    val arch = io.executeCommand("Write-Output \$env:PROCESSOR_ARCHITECTURE").lastOrNull { it.isNotBlank() }
               ?: throw CommunicationFailure("Empty output of \$env:PROCESSOR_ARCHITECTURE", null)
    val resolvedArch = EelPlatform.resolveArch(arch.trim())
                       ?: throw CommunicationFailure("No binary for architecture $arch", null)
    return DetectedWindowsTarget(this, EelPlatform.Windows(resolvedArch))
  }

  override suspend fun uploadBinary(localBinary: Path, mappedPath: String?): String {
    if (mappedPath != null) return mappedPath

    val binarySize = localBinary.fileSize()
    val readyBoundary = randomBoundary()
    val pathMarker = randomBoundary()
    val directoryMarker = randomBoundary()
    val doneBoundary = randomBoundary()
    io.process.write(
      "\$ijentDir = Join-Path ([IO.Path]::GetTempPath()) ('ijent-' + [Guid]::NewGuid().ToString('N')); " +
      "[IO.Directory]::CreateDirectory(\$ijentDir) | Out-Null; " +
      "\$ijentBinary = Join-Path \$ijentDir 'ijent.exe'; " +
      "Write-Output ('$directoryMarker' + \$ijentDir); " +
      "Write-Output ('$pathMarker' + \$ijentBinary); " +
      "Write-Output '$readyBoundary'; " +
      "\$ijentInput = [Console]::OpenStandardInput(); " +
      "try { \$ijentOutput = [IO.File]::Open(\$ijentBinary, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None); " +
      "try { \$ijentRemaining = [long]$binarySize; \$ijentBuffer = New-Object byte[] 65536; " +
      "while (\$ijentRemaining -gt 0) { \$ijentToRead = [int][Math]::Min(\$ijentBuffer.Length, \$ijentRemaining); " +
      "\$ijentRead = \$ijentInput.Read(\$ijentBuffer, 0, \$ijentToRead); " +
      "if (\$ijentRead -eq 0) { throw 'Unexpected end of IJent binary stream' }; " +
      "\$ijentOutput.Write(\$ijentBuffer, 0, \$ijentRead); \$ijentRemaining -= \$ijentRead } } " +
      "finally { \$ijentOutput.Dispose() } } " +
      "catch { Remove-Item -LiteralPath \$ijentDir -Recurse -Force -ErrorAction SilentlyContinue; throw }; " +
      "Write-Output '$doneBoundary'"
    )

    var remoteBinaryDirectory: String? = null
    var remoteBinaryPath: String? = null
    while (remoteBinaryPath == null) {
      val line = io.readLine()
      if (line.startsWith(directoryMarker)) {
        remoteBinaryDirectory = line.removePrefix(directoryMarker)
        continue
      }
      if (line.startsWith(pathMarker)) {
        remoteBinaryPath = line.removePrefix(pathMarker)
      }
      else {
        LOG.debug { "Dropped shell output while waiting for the uploaded IJent binary path: $line" }
      }
    }
    uploadedBinaryDirectory = remoteBinaryDirectory
      ?: throw CommunicationFailure("PowerShell did not report the uploaded IJent binary directory", null)

    // Only raw bytes follow the ready marker. PowerShell has parsed the complete command and is already waiting in its binary reader.
    io.dropOutputUntil(readyBoundary)
    withContext(Dispatchers.IO) {
      Files.newByteChannel(localBinary, StandardOpenOption.READ).use { stream ->
        io.process.copyDataFrom(stream)
      }
    }
    io.dropOutputUntil(doneBoundary)
    return remoteBinaryPath
  }

  suspend fun execIjent(
    remoteBinaryPath: String,
    launchOptions: IjentLaunchOptions,
  ): IjentSessionProcessMediator {
    val launchCommand = launchOptions.command(remoteBinaryPath)
    if (launchCommand.tlsCertificates != null) {
      throw CommunicationFailure("Mutual TLS is not supported for PowerShell targets", null)
    }
    val command = launchCommand.argv.joinToString(" ") { powerShellQuote(it) }
    val cleanupCommand = uploadedBinaryDirectory?.let { "Remove-Item -LiteralPath ${powerShellQuote(it)} -Recurse -Force -ErrorAction SilentlyContinue; " }.orEmpty()
    io.startProcess(
      "try { & $command; \$ijentExitCode = \$LASTEXITCODE } " +
      "catch { \$ijentExitCode = 1; [Console]::Error.WriteLine(\$_.Exception.ToString()) } " +
      "finally { $cleanupCommand" + "exit \$ijentExitCode }"
    )
    return io.process.processForConnection()
  }
}
/**
 * [Dash-based shells up to 0.5.12 inclusively have a problem](https://lore.kernel.org/dash/CAMQsgbSZnEac=ETYnR6a_ysnAysaHThwY03pnoDxC=p5FqtAag@mail.gmail.com/).
 *
 * [According to IEEE Std 1003.1-2024](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/sh.html#tag_20_110_06),
 * `sh` must read user input byte by byte and execute commands
 * as soon as a valid expression can be constructed right after reading a byte.
 * In contrast, Dash used to read ahead user input into a buffer with the size of `BUFSIZ`.
 * It broke our workflow of writing binary data right after executing the command for reading binary data.
 *
 * [The fix was committed at the beginning of 2023](https://git.kernel.org/pub/scm/utils/dash/dash.git/commit/?id=5f094d08c5bcee876191404a4f3dd2d075571215),
 * so we expect a lot of problematic shell versions in the wild.
 *
 * [GlibC defines BUFSIZ as 8192](https://sourceware.org/git/?p=glibc.git;a=blob;f=libio/stdio.h;h=da9d4eebcf013f1bf4fa11accf14e391c6029aff;hb=HEAD#l100),
 * [musl defines it as an even smaller constant](http://git.musl-libc.org/cgit/musl/tree/include/stdio.h).
 * Although there can be some systems with greater `BUFSIZ`,
 * we see the situation of compiling a shell with a problematic version and increased global buffer as improbable.
 */
private val BUGGY_DASH_BUFFER_FILLER: String get() = "\n".repeat(8192)
private val LOG = IjentLogger.LIFETIME_LOG

private const val TLS_HEREDOC_DELIMITER: String = "~IJENT_TLS_BOOTSTRAP"

private fun randomBoundary(): String =
  String(CharArray(32) { BOUNDARY_CHARACTERS.random() })

private val BOUNDARY_CHARACTERS: CharArray = (('a'..'z') + ('0'..'9')).toCharArray()

private val SHELL_UNSAFE_CHARACTERS: Set<Char> = setOf(
  '|', '&', ';', '<', '>', '(', ')', '$', '`', '\\', '"', '\'', ' ', '\t', '\n', '*', '?', '[', '#', '~', '=', '%',
)

/**
 * Wraps [argument] in single quotes for safe use as a single token in a POSIX shell command line, escaping any
 * embedded single quote as `'"'"'`. Returns [argument] unchanged when it has no shell-unsafe characters.
 */
private fun posixQuote(argument: String): String =
  if (argument.isEmpty() || argument.any { it in SHELL_UNSAFE_CHARACTERS })
    "'" + argument.replace("'", "'\"'\"'") + "'"
  else
    argument

private fun powerShellQuote(argument: String): String =
  "'" + argument.replace("'", "''") + "'"

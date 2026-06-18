// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.ReadResult.EOF
import com.intellij.platform.eel.ReadResult.NOT_EOF
import com.intellij.platform.eel.ThrowsChecked
import com.intellij.platform.eel.channels.EelChannelException
import com.intellij.platform.eel.channels.EelReceiveChannelException
import com.intellij.platform.eel.channels.EelSendChannelException
import com.intellij.platform.eel.channels.sendWholeBuffer
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.platform.ijent.IjentLog
import com.intellij.platform.ijent.IjentScope
import com.intellij.platform.ijent.IjentUnavailableException
import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.platform.ijent.getIjentGrpcArgv
import com.intellij.platform.ijent.spi.IjentSessionMediatorUtils.readLineOrThrow
import com.intellij.platform.ijent.tcp.TcpDeployInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.VisibleForTesting
import java.nio.ByteBuffer
import java.nio.channels.ByteChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.fileSize
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

// The timeout is based on internal measurements done on CI (max: 21.5s, p98: 12.2s)
private val DEFAULT_SHELL_INITIALIZATION_TIMEOUT: Duration =
  (System.getProperty("ijent.shell.initialization.timeout")?.toLongOrNull() ?: 30_000L).milliseconds

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
    data class Tcp(val deployInfo: TcpDeployInfo) : ExecutionStrategy
  }

  protected open val executionStrategy: ExecutionStrategy = ExecutionStrategy.Default

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

  private val myContext: Deferred<DeployingContextAndShell> = run {
    var createdShellProcess: ShellProcessWrapper? = null
    val context = scope.s.async(currentDispatcher, start = CoroutineStart.LAZY) {
      val ijentProcessScope = IjentSessionMediatorUtils.createProcessScope(scope, ijentLabel, LOG)
      val processFacade = createShellProcessFacade(ijentProcessScope)
      val mediator = IjentSessionProcessMediator.create(
        parentScope = scope,
        ijentProcessScope = ijentProcessScope,
        process = processFacade,
        ijentLabel = ijentLabel,
        isExpectedProcessExit = ::isExpectedProcessExit,
        exitsOnStdinEof = executionStrategy !is ExecutionStrategy.Tcp,
      )
      val shellProcess = ShellProcessWrapper(processFacade, mediator)
      createdShellProcess = shellProcess
      createDeployingContext(shellProcess.apply {
        withShellInitializationInterruption {
          val debugOption = if (LOG.isDebugEnabled) "x" else ""
          write("set -e$debugOption")
          currentCoroutineContext().ensureActive()
          filterOutBanners()
        }
      })
    }
    context
  }

  private val myTargetPlatform = scope.s.async(currentDispatcher, start = CoroutineStart.LAZY) {
    getMyContext().execCommand {
      getTargetPlatform()
    }
  }

  override suspend fun getTargetPlatform(): EelPlatform.Posix {
    return try {
      myTargetPlatform.await()
    }
    catch (e: CancellationException) {
      currentCoroutineContext().ensureActive()
      throw RuntimeException("Cancellation during target platform retrieval", e)
    }
  }

  final override suspend fun createProcess(binaryPath: String): IjentSessionProcessMediator {
    return getMyContext().execCommand {
      when (val strategy = executionStrategy) {
        is ExecutionStrategy.Tcp -> execIjentWithTcp(binaryPath, strategy.deployInfo)
        else -> execIjent(binaryPath)
      }
    }
  }

  final override suspend fun copyFile(file: Path): String {
    return getMyContext().execCommand {
      uploadIjentBinary(file, ::mapPath)
    }
  }

  private suspend fun getMyContext(): DeployingContextAndShell =
    try {
      myContext.await()
    }
    catch (e: CancellationException) {
      currentCoroutineContext().ensureActive()
      throw RuntimeException("Cancellation during context retrieval", e)
    }

  final override fun close() {
    if (myContext.isActive) {
      myContext.cancel(CancellationException("Closed explicitly"))
    }
  }

  override suspend fun getConnectionStrategy(): IjentConnectionStrategy = IjentConnectionStrategy.Default
}

private class ShellProcessWrapper(
  private val process: IjentSessionProcessMediator.ProcessFacade?,
  private var mediator: IjentSessionProcessMediator?,
) {
  @ThrowsChecked(EelSendChannelException::class)
  suspend fun write(data: String) {
    @Suppress("NAME_SHADOWING")
    val data = if (data.endsWith("\n")) data else "$data\n"
    LOG.debug {
      val debugData = data.replace(Regex("\n\n+")) { "<\\n ${it.value.length} times>\n" }
      "Executing a script inside the shell: $debugData"
    }
    withContext(Dispatchers.IO) {
      process!!.stdin.sendWholeText(data)
    }
  }

  @ThrowsChecked(EelReceiveChannelException::class)
  suspend fun readLine(): String {
    // TODO The encoding can be different.
    return process!!.stdout.readLineOrThrow(StandardCharsets.UTF_8)
  }

  @ThrowsChecked(EelChannelException::class)
  suspend fun copyDataFrom(stream: ByteChannel) {
    val buffer =
      if (process!!.stdin.prefersDirectBuffers) ByteBuffer.allocateDirect(64 * 1024)
      else ByteBuffer.allocate(64 * 1024)
    val input = stream.consumeAsEelChannel()
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

  @OptIn(InternalCoroutinesApi::class)
  suspend fun destroyForciblyAndGetError(): Throwable {
    mediator!!.process.destroyForcibly()
    try {
      val job = mediator!!.ijentProcessScope.s.coroutineContext.job
      job.join()
      throw job.getCancellationException()
    }
    catch (err: Throwable) {
      return IjentUnavailableException.unwrapFromCancellationExceptions(err)
    }
  }

  fun extractProcess(): IjentSessionProcessMediator {
    val result = mediator!!
    mediator = null
    return result
  }
}

private suspend fun <T : Any> DeployingContextAndShell.execCommand(block: suspend DeployingContextAndShell.() -> T): T {
  return try {
    block()
  }
  catch (initialErrorFromStack: Throwable) {
    val errorFromScope = process.destroyForciblyAndGetError()
    val errorFromStack = IjentUnavailableException.unwrapFromCancellationExceptions(initialErrorFromStack)

    // It happens sometimes that some valuable error is wrapped into CancellationException.
    // However, if there's no valuable error,
    // then it's a fair CancellationException that should be thrown futher, to cancel the context.
    val (mainError, suppressedError) =
      when (errorFromStack) {
        is IjentUnavailableException, is CancellationException -> errorFromStack to errorFromScope
        else -> errorFromScope to errorFromStack
      }

    if (mainError != suppressedError) {
      mainError.addSuppressed(suppressedError)
    }

    throw when (mainError) {
      is IjentUnavailableException, is CancellationException -> mainError
      else -> IjentStartupError.CommunicationError(mainError)
    }
  }
}

private suspend fun ShellProcessWrapper.filterOutBanners() {
  // The boundary is for skipping various banners, greeting messages, PS1, etc.
  val boundary = (0..31).joinToString("") { "abcdefghijklmnopqrstuvwxyz0123456789".random().toString() }
  write("echo $boundary")
  do {
    val line = this@filterOutBanners.readLine()
  }
  while (line != boundary)
}

private class DeployingContextAndShell(
  val process: ShellProcessWrapper,
  val context: DeployingContext,
)

@VisibleForTesting
internal data class DeployingContext(
  val chmod: String,
  val cp: String,
  val cut: String,
  val env: String,
  val getent: String,
  val head: String,
  val mktemp: String,
  val rm: String,
  val sed: String,
  val tail: String,
  val uname: String,
  val whoami: String,
)

/**
 * There are distributions like rancher-desktop-data where /bin/busybox exists, but there are no symlinks to uname, head, etc.
 *
 * This tricky function checks if the necessary core utils exist and tries to substitute them with busybox otherwise.
 */
private suspend fun createDeployingContext(
  shellProcess: ShellProcessWrapper,
): DeployingContextAndShell {
  val deployingContext = createDeployingContext { commands ->
    val outputOfWhich = mutableListOf<String>()

    val done = "done"
    val whichCmd = buildString {
      append("set +e; ")
      for (command in commands) {
        append("type $command 1>&2 && echo $command; ")
      }
      append("echo $done; set -e")
    }

    shellProcess.write(whichCmd)

    while (true) {
      val line = shellProcess.readLine()
      if (line == done) break
      outputOfWhich += line
    }

    outputOfWhich
  }
  return DeployingContextAndShell(shellProcess, deployingContext)
}

@VisibleForTesting
internal suspend fun createDeployingContext(filterAvailableBinariesCmd: suspend (commands: Collection<String>) -> Collection<String>): DeployingContext {
  // This strange at first glance code helps reduce copy-paste errors.
  val commands: Set<String> = setOf(
    "busybox",
    "chmod",
    "cp",
    "cut",
    "env",
    "getent",
    "head",
    "mktemp",
    "rm",
    "sed",
    "tail",
    "uname",
    "whoami",
  )
  val outputOfWhich = mutableListOf<String>()

  fun getCommandPath(name: String): String {
    assert(name in commands)
    return when {
      name in outputOfWhich -> name
      "busybox" in outputOfWhich -> "busybox $name"
      else -> throw IjentStartupError.IncompatibleTarget(setOf("busybox", name).joinToString(prefix = "The remote machine has none of: "))
    }
  }

  outputOfWhich += filterAvailableBinariesCmd(commands)

  return DeployingContext(
    chmod = getCommandPath("chmod"),
    cp = getCommandPath("cp"),
    cut = getCommandPath("cut"),
    env = getCommandPath("env"),
    getent = getCommandPath("getent"),
    head = getCommandPath("head"),
    mktemp = getCommandPath("mktemp"),
    rm = getCommandPath("rm"),
    sed = getCommandPath("sed"),
    tail = getCommandPath("tail"),
    uname = getCommandPath("uname"),
    whoami = getCommandPath("whoami"),
  )
}

private suspend fun DeployingContextAndShell.getTargetPlatform(): EelPlatform.Posix = run {
  // There are two arguments in `uname` that can show the process architecture: `-m` and `-p`. According to `man uname`, `-p` is more
  // verbose, and that information may be sufficient for choosing the right binary.
  // https://man.freebsd.org/cgi/man.cgi?query=uname&sektion=1
  process.write("${context.uname} -pm")

  val arch = process.readLine().split(" ").filterTo(linkedSetOf(), String::isNotEmpty)

  val targetPlatform = when {
    arch.isEmpty() -> throw IjentStartupError.IncompatibleTarget("Empty output of `uname`")
    "x86_64" in arch -> EelPlatform.Linux(EelPlatform.Arch.X86_64)
    "aarch64" in arch -> EelPlatform.Linux(EelPlatform.Arch.ARM_64)
    else -> throw IjentStartupError.IncompatibleTarget("No binary for architecture $arch")
  }
  return targetPlatform
}

private suspend fun DeployingContextAndShell.uploadIjentBinary(
  ijentBinaryOnLocalDisk: Path,
  pathMapper: suspend (Path) -> String?,
): String {
  // TODO Don't upload a new binary every time if the binary is already on the server. However, hashes must be checked.
  val ijentBinarySize = ijentBinaryOnLocalDisk.fileSize()

  val ijentBinaryPreparedOnTarget = pathMapper(ijentBinaryOnLocalDisk)

  process.write(context.run {
    "BINARY=\"\$($mktemp -d)/ijent\";\n"
  })

  if (ijentBinaryPreparedOnTarget != null) {
    process.write(context.run {
      "$cp ${posixQuote(ijentBinaryPreparedOnTarget)} \$BINARY;\n"
    })
  }
  else {
    process.write(context.run {
      "$head -c ${ijentBinarySize + BUGGY_DASH_BUFFER_FILLER.length} > \$BINARY.tmp;\n"
    })

    // Old versions of busybox with bundled problematic versions of dash don't handle arguments
    // like `-v` or `--version`.
    // While it's not easy to figure out if the workaround filler is actually required,
    // it's used with every shell. This also makes code a bit simpler.
    LOG.debug { "Writing workaround command for Dash (1 of 2)" }
    process.write(BUGGY_DASH_BUFFER_FILLER)
    LOG.debug { "Writing $ijentBinarySize bytes of IJent binary into the stream" }
    withContext(Dispatchers.IO) {
      Files.newByteChannel(ijentBinaryOnLocalDisk, StandardOpenOption.READ).use { stream ->
        process.copyDataFrom(stream)
      }
    }
    LOG.debug { "Sent the IJent binary $ijentBinaryOnLocalDisk" }
    LOG.debug { "Writing workaround command for Dash (2 of 2)" }
    process.write(BUGGY_DASH_BUFFER_FILLER)

    // Now the file `$BINARY.tmp` contains the following content:
    // <\n * (random number in 0..filler_size)> + useful data + <\n + filler_size>
    // The script below extracts the useful data and puts it into `$BINARY`.
    // It wasn't checked if `LC_ALL` really needed for sed/head/tail, this variable
    // was overridden just in case.
    process.write(context.run {
      """
      | BYTES_TO_SKIP=${'$'}(LC_ALL=C $sed -e '/^${'$'}/d; =; q' ${'$'}BINARY.tmp | LC_ALL=C $head -n1);
      | LC_ALL=C $tail -c+${'$'}BYTES_TO_SKIP ${'$'}BINARY.tmp | LC_ALL=C $head -c ${ijentBinarySize} > ${'$'}BINARY;
      | $rm -f ${'$'}BINARY.tmp;
      """.trimMargin()
    })
  }

  process.write(context.run {
    "$chmod 500 \"\$BINARY\"; echo \"\$BINARY\";\n"
  })

  return process.readLine()
}


private suspend fun DeployingContextAndShell.execIjent(remotePathToBinary: String): IjentSessionProcessMediator {
  val joinedCmd = getIjentGrpcArgv(remotePathToBinary, selfDeleteOnExit = true).joinToString(" ")
  return createMediator(remotePathToBinary, joinedCmd)
}

private suspend fun DeployingContextAndShell.createMediator(
  remotePathToBinary: String,
  joinedCmd: String,
): IjentSessionProcessMediator {
  val commandLineArgs = context.run {
    """
    | cd ${posixQuote(remotePathToBinary.substringBeforeLast('/'))};
    | export SHELL="${'$'}($getent passwd "${'$'}($whoami)" | $cut -d: -f7)";
    | if [ -z "${'$'}SHELL" ]; then export SHELL='/bin/sh' ; fi;
    | exec "${'$'}SHELL" -c ${posixQuote(joinedCmd)}
    """.trimMargin()
  }
  process.write(commandLineArgs)
  return process.extractProcess()
}


private suspend fun DeployingContextAndShell.execIjentWithTcp(remotePathToBinary: String, deployInfo: TcpDeployInfo): IjentSessionProcessMediator {
  val joinedCmd = getIjentGrpcArgv(remotePathToBinary,
                                   selfDeleteOnExit = true,
                                   deployInfo = deployInfo).joinToString(" ")
  return createMediator(remotePathToBinary, joinedCmd)
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
private val LOG = IjentLog.getInstance<IjentDeployingOverShellProcessStrategy>()

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
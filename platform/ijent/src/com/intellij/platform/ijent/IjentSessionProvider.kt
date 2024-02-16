// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.execution.CommandLineUtil.posixQuote
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.util.io.computeDetached
import com.intellij.util.io.copyToAsync
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.time.Duration.Companion.seconds

/**
 * Given that there is some IJent process launched, this extension gets handles to stdin+stdout of the process and returns
 * an [IjentApi] instance for calling procedures on IJent side.
 */
@ApiStatus.Internal
interface IjentSessionProvider {
  /**
   * Supposed to be used inside [IjentSessionRegistry.register].
   *
   * [ijentCoroutineScope] must be the scope generated inside [IjentSessionRegistry.register]
   */
  suspend fun connect(
    ijentId: IjentId,
    platform: IjentPlatform,
    mediator: IjentSessionMediator
  ): IjentApi

  companion object {
    suspend fun instanceAsync(): IjentSessionProvider = serviceAsync()
  }
}

sealed class IjentStartupError : RuntimeException {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)

  class MissingImplPlugin : IjentStartupError("The plugin `intellij.platform.ijent.impl` is not installed")

  sealed class BootstrapOverShell : IjentStartupError {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
  }

  class IncompatibleTarget(message: String) : BootstrapOverShell(message)
  class CommunicationError(cause: Throwable) : BootstrapOverShell(cause.message.orEmpty(), cause)
}

internal class DefaultIjentSessionProvider : IjentSessionProvider {
  override suspend fun connect(ijentId: IjentId, platform: IjentPlatform, mediator: IjentSessionMediator): IjentApi {
    throw IjentStartupError.MissingImplPlugin()
  }
}

/** A shortcut for terminating an [IjentApi] when the [coroutineScope] completes. */
@ApiStatus.Experimental
fun IjentApi.bindToScope(coroutineScope: CoroutineScope) {
  coroutineScope.coroutineContext.job.invokeOnCompletion {
    this@bindToScope.close()
  }
}

/**
 * Make [IjentApi] from an already running [process].
 * [ijentName] is used for debugging utilities like logs and thread names.
 *
 * The process terminates automatically only when the IDE exits, or if [IjentApi.close] is called explicitly.
 * [bindToScope] may be useful for terminating the IJent process earlier.
 */
@ApiStatus.Experimental
suspend fun connectToRunningIjent(ijentName: String, platform: IjentPlatform, process: Process): IjentApi =
  IjentSessionRegistry.instanceAsync().register(ijentName) { ijentId ->
    val mediator = IjentSessionMediator.create(process, ijentId)
    mediator.expectedErrorCode = IjentSessionMediator.ExpectedErrorCode.ZERO
    IjentSessionProvider.instanceAsync().connect(ijentId, platform, mediator)
  }

suspend fun connectToRunningIjent(
  ijentName: String,
  platform: IjentPlatform.Posix,
  process: Process,
): IjentPosixApi =
  connectToRunningIjent(ijentName, platform as IjentPlatform, process) as IjentPosixApi

suspend fun connectToRunningIjent(
  ijentName: String,
  platform: IjentPlatform.Windows,
  process: Process,
): IjentWindowsApi =
  connectToRunningIjent(ijentName, platform as IjentPlatform, process) as IjentWindowsApi

/**
 * Interactively requests IJent through a running POSIX-compliant command interpreter: sh, bash, ash, ksh, zsh.
 *
 * After determination of the remote operating system and architecture, an appropriate IJent binary is uploaded and executed.
 * All requests and data transfer with the remote machine is performed through stdin and stdout of [shellProcess].
 *
 * It is recommended to always use `/bin/sh` for [shellProcess], but any other POSIX-compliant interpreter is accepted too. The shell
 * is later changed to the default user's shell before starting IJent, in order that [IjentExecApi.fetchLoginShellEnvVariables] returns
 * the variables from the appropriate shell configs.
 *
 * [shellProcess] must have stdin, stdout and stderr piped.
 *
 * [shellProcess] must NOT run inside a PTY.
 *
 * The line delimiter must be '\n'.
 *
 * The function takes the ownership of [shellProcess]: it invokes `exec(1)` inside the process and terminates [shellProcess]
 * in case of problems.
 *
 * [ijentName] is used for debugging utilities like logs and thread names.
 *
 * The process terminates automatically only when the IDE exits, or if [IjentApi.close] is called explicitly.
 * [bindToScope] may be useful for terminating the IJent process earlier.
 *
 * [pathMapper] is a workaround function that allows to upload IJent to the remote target explicitly.
 * The argument passed to the function is the path to the corresponding IJent binary on the local machine.
 * The function must return a path on the remote machine.
 * If the function returns null, the binary is transferred to the server directly via the same shell process,
 * which turned out to be unreliable unfortunately.
 */
// TODO Change string paths to IjentPath.Absolute.
@ApiStatus.Experimental
@Throws(IjentStartupError::class)
suspend fun bootstrapOverShellSession(
  ijentName: String,
  shellProcess: Process,
  pathMapper: suspend (Path) -> String?,
): Pair<String, IjentPosixApi> {
  val remoteIjentPath: String
  val ijentApi = IjentSessionRegistry.instanceAsync().register(ijentName) { ijentId ->
    val mediator = IjentSessionMediator.create(shellProcess, ijentId)

    val (path, targetPlatform) =
      try {
        mediator.attachStderrOnError {
          mediator.expectedErrorCode = IjentSessionMediator.ExpectedErrorCode.ANY
          doBootstrapOverShellSession(shellProcess, pathMapper)
        }
      }
      catch (err: Throwable) {
        runCatching { shellProcess.destroyForcibly() }.exceptionOrNull()?.let(err::addSuppressed)
        throw err
      }
    mediator.expectedErrorCode = IjentSessionMediator.ExpectedErrorCode.ZERO
    remoteIjentPath = path

    try {
      IjentSessionProvider.instanceAsync().connect(
        ijentId = ijentId,
        platform = targetPlatform,
        mediator = mediator
      )
    }
    catch (err: Throwable) {
      try {
        shellProcess.destroy()
      }
      catch (err2: Throwable) {
        err.addSuppressed(err)
      }
      throw err
    }
  }
  return remoteIjentPath to (ijentApi as IjentPosixApi)
}

@OptIn(DelicateCoroutinesApi::class)
private suspend fun doBootstrapOverShellSession(
  shellProcess: Process,
  pathMapper: suspend (Path) -> String?,
): Pair<String, IjentPlatform> = computeDetached {
  try {
    @Suppress("NAME_SHADOWING") val shellProcess = ShellProcess(shellProcess)

    // The timeout is taken at random.
    withTimeout(10.seconds) {
      shellProcess.write("set -ex")
      ensureActive()

      filterOutBanners(shellProcess)
      val commands = getCommandPaths(shellProcess)

      with(commands) {
        val targetPlatform = getTargetPlatform(shellProcess)
        val remotePathToIjent = uploadIjentBinary(shellProcess, targetPlatform, pathMapper)
        execIjent(shellProcess, remotePathToIjent)
        remotePathToIjent to targetPlatform
      }
    }
  }
  catch (err: Throwable) {
    throw when (err) {
      is TimeoutCancellationException, is IOException -> IjentStartupError.CommunicationError(err)
      else -> err
    }
  }
}

private suspend fun filterOutBanners(shellProcess: ShellProcess) {
  // The boundary is for skipping various banners, greeting messages, PS1, etc.
  val boundary = (0..31).joinToString("") { "abcdefghijklmnopqrstuvwxyz0123456789".random().toString() }
  shellProcess.write("echo $boundary")
  do {
    val line = shellProcess.readLineWithoutBuffering()
  }
  while (line != boundary)
}

private class Commands(
  val chmod: String,
  val cp: String,
  val cut: String,
  val env: String,
  val getent: String,
  val head: String,
  val mktemp: String,
  val uname: String,
  val whoami: String,
)

/**
 * There are distributions like rancher-desktop-data where /bin/busybox exists, but there are no symlinks to uname, head, etc.
 *
 * This tricky function checks if the necessary core utils exist and tries to substitute them with busybox otherwise.
 */
private suspend fun getCommandPaths(shellProcess: ShellProcess): Commands {
  var busybox: Lazy<String>? = null

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
    "uname",
    "whoami",
  )
  val outputOfWhich = mutableListOf<String>()

  fun getCommandPath(name: String): String {
    assert(name in commands)
    return outputOfWhich.firstOrNull { it.endsWith("/$name") }
           ?: busybox?.value?.let { "$it $name" }
           ?: throw IjentStartupError.IncompatibleTarget(setOf("busybox", name).joinToString(prefix = "The remote machine has none of: "))
  }

  val done = "done"
  val whichCmd = commands.joinToString(" ").let { joined ->
    "set +e; which $joined || /bin/busybox which $joined || /usr/bin/busybox which $joined; echo $done; set -e"
  }

  shellProcess.write(whichCmd)

  while (true) {
    val line = shellProcess.readLineWithoutBuffering()
    if (line == done) break
    outputOfWhich += line
  }

  busybox = lazy { getCommandPath("busybox") }

  return Commands(
    chmod = getCommandPath("chmod"),
    cp = getCommandPath("cp"),
    cut = getCommandPath("cut"),
    env = getCommandPath("env"),
    getent = getCommandPath("getent"),
    head = getCommandPath("head"),
    mktemp = getCommandPath("mktemp"),
    uname = getCommandPath("uname"),
    whoami = getCommandPath("whoami"),
  )
}

private suspend fun Commands.getTargetPlatform(shellProcess: ShellProcess): IjentPlatform {
  // There are two arguments in `uname` that can show the process architecture: `-m` and `-p`. According to `man uname`, `-p` is more
  // verbose, and that information may be sufficient for choosing the right binary.
  // https://man.freebsd.org/cgi/man.cgi?query=uname&sektion=1
  shellProcess.write("$uname -pm")

  val arch = shellProcess.readLineWithoutBuffering().split(" ").filterTo(linkedSetOf(), String::isNotEmpty)

  val targetPlatform = when {
    arch.isEmpty() -> throw IjentStartupError.IncompatibleTarget("Empty output of `uname`")
    "x86_64" in arch -> IjentPlatform.X8664Linux
    "aarch64" in arch -> IjentPlatform.Aarch64Linux
    else -> throw IjentStartupError.IncompatibleTarget("No binary for architecture $arch")
  }
  return targetPlatform
}

private suspend fun Commands.uploadIjentBinary(
  shellProcess: ShellProcess,
  targetPlatform: IjentPlatform,
  pathMapper: suspend (Path) -> String?,
): String {
  val ijentBinaryOnLocalDisk = IjentExecFileProvider.getInstance().getIjentBinary(targetPlatform)
  // TODO Don't upload a new binary every time if the binary is already on the server. However, hashes must be checked.
  val ijentBinarySize = ijentBinaryOnLocalDisk.fileSize()

  val ijentBinaryPreparedOnTarget = pathMapper(ijentBinaryOnLocalDisk)

  val script = run {
    val ijentPathUploadScript =
      pathMapper(ijentBinaryOnLocalDisk)
        ?.let { "$cp ${posixQuote(it)} \$BINARY" }
      ?: run {
        "LC_ALL=C $head -c $ijentBinarySize > \$BINARY"
      }

    "BINARY=\"$($mktemp -d)/ijent\" ; $ijentPathUploadScript ; $chmod 500 \"\$BINARY\" ; echo \"\$BINARY\" "
  }

  shellProcess.write(script)

  if (ijentBinaryPreparedOnTarget == null) {
    LOG.debug { "Writing $ijentBinarySize bytes of IJent binary into the stream" }
    ijentBinaryOnLocalDisk.inputStream().use { stream ->
      shellProcess.copyDataFrom(stream)
    }
    LOG.debug { "Sent the IJent binary for $targetPlatform" }
  }

  return shellProcess.readLineWithoutBuffering()
}

private suspend fun Commands.execIjent(shellProcess: ShellProcess, remotePathToBinary: String) {
  val joinedCmd = getIjentGrpcArgv(remotePathToBinary, selfDeleteOnExit = true, usrBinEnv = env).joinToString(" ")
  val commandLineArgs =
    """
    | cd ${posixQuote(remotePathToBinary.substringBeforeLast('/'))};
    | export SHELL="${'$'}($getent passwd "${'$'}($whoami)" | $cut -d: -f7)";
    | if [ -z "${'$'}SHELL" ]; then export SHELL='/bin/sh' ; fi;
    | exec "${'$'}SHELL" -c ${posixQuote(joinedCmd)}
    """.trimMargin()
  shellProcess.write(commandLineArgs)
}

@JvmInline
private value class ShellProcess(private val process: Process) {
  suspend fun write(data: String) {
    @Suppress("NAME_SHADOWING")
    val data = if (data.endsWith("\n")) data else "$data\n"
    LOG.debug { "Executing a script inside the shell: $data" }
    withContext(Dispatchers.IO) {
      process.outputStream.write(data.toByteArray())
      ensureActive()
      process.outputStream.flush()
      ensureActive()
    }
  }

  /** The same stdin and stdout will be used for transferring binary data. Some buffering wrapper may occasionally consume too much data. */
  suspend fun readLineWithoutBuffering(): String =
    withContext(Dispatchers.IO) {
      val buffer = StringBuilder()
      val stream = process.inputStream
      while (true) {
        ensureActive()
        val c = stream.read()
        if (c < 0 || c == '\n'.code) {
          break
        }
        buffer.append(c.toChar())
      }
      LOG.trace { "Read line from stdout: $buffer" }
      buffer.toString()
    }

  suspend fun copyDataFrom(stream: InputStream) {
    withContext(Dispatchers.IO) {
      stream.copyToAsync(process.outputStream)
      ensureActive()
      process.outputStream.flush()
    }
  }
}

private val LOG = logger<IjentSessionProvider>()
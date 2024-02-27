// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.execution.CommandLineUtil.posixQuote
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.util.io.copyToAsync
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.io.OutputStream
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.time.Duration.Companion.milliseconds
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
    ijentCoroutineScope: CoroutineScope,
    ijentId: IjentId,
    platform: IjentExecFileProvider.SupportedPlatform,
    inputStream: InputStream,
    outputStream: OutputStream,
  ): IjentApi

  /**
   * See also [doBootstrapOverShellSession].
   */
  suspend fun connect(
    ijentCoroutineScope: CoroutineScope,
    ijentId: IjentId,
    platform: IjentExecFileProvider.SupportedPlatform,
    watcher: IjentProcessWatcher,
  ): IjentApi {
    watcher.expectedErrorCode = IjentProcessWatcher.ExpectedErrorCode.ZERO
    return connect(ijentCoroutineScope, ijentId, platform, watcher.process.inputStream, watcher.process.outputStream)
  }

  companion object {
    suspend fun instanceAsync(): IjentSessionProvider = serviceAsync()
  }
}

internal class DefaultIjentSessionProvider : IjentSessionProvider {
  override suspend fun connect(
    ijentCoroutineScope: CoroutineScope,
    ijentId: IjentId,
    platform: IjentExecFileProvider.SupportedPlatform,
    inputStream: InputStream,
    outputStream: OutputStream,
  ): IjentApi {
    throw UnsupportedOperationException()
  }
}

/** A shortcut for terminating an [IjentApi] when the [coroutineScope] completes. */
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
suspend fun connectToRunningIjent(ijentName: String, platform: IjentExecFileProvider.SupportedPlatform, process: Process): IjentApi =
  IjentSessionRegistry.instanceAsync().register(ijentName) { ijentCoroutineScope, ijentId ->
    val watcher = IjentProcessWatcher.launch(ijentCoroutineScope, process, ijentId)
    IjentSessionProvider.instanceAsync().connect(ijentCoroutineScope, ijentId, platform, watcher)
  }

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
 */
// TODO Change string paths to IjentPath.Absolute.
suspend fun bootstrapOverShellSession(ijentName: String, shellProcess: Process): Pair<String, IjentApi> {
  val remoteIjentPath: String
  val ijentApi = IjentSessionRegistry.instanceAsync().register(ijentName) { ijentCoroutineScope, ijentId ->
    val processWatcher = IjentProcessWatcher.launch(ijentCoroutineScope, shellProcess, ijentId)

    val (path, targetPlatform) =
      try {
        processWatcher.attachStderrOnError {
          processWatcher.expectedErrorCode = IjentProcessWatcher.ExpectedErrorCode.ANY
          doBootstrapOverShellSession(shellProcess)
        }
      }
      catch (err: Throwable) {
        runCatching { shellProcess.destroyForcibly() }.exceptionOrNull()?.let(err::addSuppressed)
        throw err
      }
    processWatcher.expectedErrorCode = IjentProcessWatcher.ExpectedErrorCode.ZERO
    remoteIjentPath = path

    try {
      IjentSessionProvider.instanceAsync().connect(
        ijentCoroutineScope,
        ijentId,
        targetPlatform,
        shellProcess.inputStream,
        shellProcess.outputStream,
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
  return remoteIjentPath to ijentApi
}

private suspend fun doBootstrapOverShellSession(
  shellProcess: Process,
): Pair<String, IjentExecFileProvider.SupportedPlatform> = withContext(Dispatchers.IO) {
  // The boundary is for skipping various banners, greeting messages, PS1, etc.
  val boundary = (0..31).joinToString("") { "abcdefghijklmnopqrstuvwxyz0123456789".random().toString() }

  // The timeout is taken at random.
  val arch = withTimeout(10.seconds) {
    // There are two arguments in `uname` that can show the process architecture: `-m` and `-p`. According to `man uname`, `-p` is more
    // verbose, and that information may be sufficient for choosing the right binary.
    // https://man.freebsd.org/cgi/man.cgi?query=uname&sektion=1
    shellProcess.outputStream.write("set -ex; echo $boundary; uname -pm\n".toByteArray())
    shellProcess.outputStream.flush()

    do {
      val line = readLineWithoutBuffering(shellProcess)
      LOG.trace { "Received greeting line from stdout: $line" }
    }
    while (line != boundary)

    readLineWithoutBuffering(shellProcess)
  }
    .split(" ")
    .filterTo(linkedSetOf(), String::isNotEmpty)

  val targetPlatform = when {
    arch.isEmpty() -> error("Empty output of `uname`")
    "x86_64" in arch -> IjentExecFileProvider.SupportedPlatform.X86_64__LINUX
    "aarch64" in arch -> IjentExecFileProvider.SupportedPlatform.AARCH64__LINUX
    else -> error("No binary for architecture $arch")
  }

  val ijentBinaryOnLocalDisk = IjentExecFileProvider.getInstance().getIjentBinary(targetPlatform)
  // TODO Don't upload a new binary every time if the binary is already on the server. However, hashes must be checked.
  val ijentBinarySize = ijentBinaryOnLocalDisk.fileSize()

  val script =
    """BINARY="$(mktemp -d)/ijent" """ +
    """; LC_ALL=C head -c $ijentBinarySize > "${"$"}BINARY" """ +
    """; chmod 500 "${"$"}BINARY" """ +
    """; echo "${"$"}BINARY" """ +
    "\n"

  LOG.trace { "Executing script inside a shell: ${script.trimEnd()}" }
  shellProcess.outputStream.write(script.toByteArray())
  yield()
  shellProcess.outputStream.flush()

  LOG.debug { "Sending the IJent binary for $targetPlatform" }
  ijentBinaryOnLocalDisk.inputStream().copyToAsync(shellProcess.outputStream)
  shellProcess.outputStream.flush()
  LOG.debug { "Sent the IJent binary for $targetPlatform" }

  val remotePathToBinary = readLineWithoutBuffering(shellProcess)

  val joinedCmd = getIjentGrpcArgv(remotePathToBinary, selfDeleteOnExit = true).joinToString(" ")
  val commandLineArgs =
    """cd ${posixQuote(remotePathToBinary.substringBeforeLast('/'))}""" +
    """; exec "$(getent passwd "${'$'}(whoami)" | cut -d: -f7)" -c ${posixQuote(joinedCmd)}""" +
    "\n"
  LOG.trace { "Executing IJent inside a shell: ${commandLineArgs.trimEnd()}" }

  shellProcess.outputStream.write(commandLineArgs.toByteArray())
  shellProcess.outputStream.flush()

  remotePathToBinary to targetPlatform
}

/** The same stdin and stdout will be used for transferring binary data. Some buffering wrapper may occasionally consume too much data. */
private suspend fun readLineWithoutBuffering(process: Process): String =
  withContext(Dispatchers.IO) {
    val buffer = StringBuilder()
    val stream = process.inputStream
    while (process.isAlive) {
      val available = stream.available()
      if (available > 0) {
        val c = stream.read()
        if (c < 0 || c == '\n'.code) {
          break
        }
        buffer.append(c.toChar())
      }
      else {
        delay(50.milliseconds) // Just a random timeout, which was chosen without any research.
      }
    }
    LOG.trace { "Read line from stdout: $buffer" }
    buffer.toString()
  }

private val LOG = logger<IjentSessionProvider>()
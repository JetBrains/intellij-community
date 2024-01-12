// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.execution.CommandLineUtil.posixQuote
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.util.coroutines.attachAsChildTo
import com.intellij.platform.util.coroutines.namedChildScope
import com.intellij.util.io.awaitExit
import com.intellij.util.io.copyToAsync
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.OverrideOnly
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Given that there is some IJent process launched, this extension gets handles to stdin+stdout of the process and returns
 * an [IjentApi] instance for calling procedures on IJent side.
 */
@ApiStatus.Experimental
interface IjentSessionProvider {
  @get:OverrideOnly
  val epCoroutineScope: CoroutineScope

  /**
   * When calling the method, there's no need to wire [communicationCoroutineScope] to [epCoroutineScope],
   * since it is already performed by factory methods.
   *
   * [communicationCoroutineScope] must be a supervisor scope.
   *
   * Automatically registers the result in [IjentSessionRegistry].
   */
  @OverrideOnly
  suspend fun connect(
    id: IjentId,
    communicationCoroutineScope: CoroutineScope,
    platform: IjentExecFileProvider.SupportedPlatform,
    inputStream: InputStream,
    outputStream: OutputStream,
  ): IjentApi

  companion object {
    /**
     * The session exits when one of the following happens:
     * * The job corresponding to [communicationCoroutineScope] is finished.
     * * [epCoroutineScope] is finished.
     * * [inputStream] is closed.
     */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun connect(
      communicationCoroutineScope: CoroutineScope,
      platform: IjentExecFileProvider.SupportedPlatform,
      process: Process,
    ): IjentApi {
      val provider = serviceAsync<IjentSessionProvider>()
      val ijentsRegistry = IjentSessionRegistry.instanceAsync()
      val ijentId = ijentsRegistry.makeNewId()
      val epCoroutineScope = provider.epCoroutineScope
      val childScope = communicationCoroutineScope
        .namedChildScope(ijentId.toString(), supervisor = false)
        .apply { attachAsChildTo(epCoroutineScope) }

      childScope.coroutineContext.job.invokeOnCompletion {
        ijentsRegistry.ijentsInternal.remove(ijentId)
      }

      childScope.launch(Dispatchers.IO + childScope.coroutineNameAppended("$ijentId > watchdog")) {
        while (true) {
          if (process.waitFor(10, TimeUnit.MILLISECONDS)) {
            val exitValue = process.exitValue()
            LOG.debug { "$ijentId exit code $exitValue" }
            val message = "Process has exited with code $exitValue"
            if (exitValue == 0) {
              cancel(message)
            }
            else {
              LOG.error(message)
              error(message)
            }
            break
          }
          delay(100)
        }
      }
      childScope.launch(Dispatchers.IO + childScope.coroutineNameAppended("$ijentId > finalizer")) {
        try {
          awaitCancellation()
        }
        catch (err: Exception) {
          LOG.debug(err) { "$ijentId is going to be terminated due to receiving an error" }
          throw err
        }
        finally {
          if (process.isAlive) {
            GlobalScope.launch(Dispatchers.IO + coroutineNameAppended("actual destruction")) {
              try {
                if (process.waitFor(5, TimeUnit.SECONDS)) {
                  LOG.debug { "$ijentId exit code ${process.exitValue()}" }
                }
              }
              finally {
                if (process.isAlive) {
                  LOG.warn("The process $ijentId is still alive, it will be killed")
                  process.destroy()
                }
              }
            }
            GlobalScope.launch(Dispatchers.IO) {
              LOG.debug { "Closing stdin of $ijentId" }
              process.outputStream.close()
            }
          }
        }
      }

      val processScopeNamePrefix = childScope.coroutineContext[CoroutineName]?.let { "$it >" } ?: ""

      epCoroutineScope.launch(Dispatchers.IO) {
        withContext(coroutineNameAppended("$processScopeNamePrefix $ijentId > logger")) {
          process.errorReader().use { errorReader ->
            for (line in errorReader.lineSequence()) {
              // TODO It works incorrectly with multiline log messages.
              when (line.splitToSequence(Regex(" +")).drop(1).take(1).firstOrNull()) {
                "TRACE" -> LOG.trace { "$ijentId log: $line" }
                "DEBUG" -> LOG.debug { "$ijentId log: $line" }
                "INFO" -> LOG.info("$ijentId log: $line")
                "WARN" -> LOG.warn("$ijentId log: $line")
                "ERROR" -> LOG.error("$ijentId log: $line")
                else -> LOG.trace { "$ijentId log: $line" }
              }
              yield()
            }
          }
        }
      }

      val result = provider.connect(ijentId, childScope, platform, process.inputStream, process.outputStream)
      ijentsRegistry.ijentsInternal[ijentId] = result
      return result
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
     */
    suspend fun bootstrapOverShellSession(communicationCoroutineScope: CoroutineScope, shellProcess: Process): Pair<String, IjentApi> =
      doBootstrapOverShellSession(shellProcess, communicationCoroutineScope)
  }
}

internal class DefaultIjentSessionProvider(override val epCoroutineScope: CoroutineScope) : IjentSessionProvider {
  override suspend fun connect(
    id: IjentId,
    communicationCoroutineScope: CoroutineScope,
    platform: IjentExecFileProvider.SupportedPlatform,
    inputStream: InputStream,
    outputStream: OutputStream,
  ): IjentApi {
    throw UnsupportedOperationException()
  }
}

@OptIn(DelicateCoroutinesApi::class)
private suspend fun doBootstrapOverShellSession(shellProcess: Process, communicationCoroutineScope: CoroutineScope) =
  withContext(Dispatchers.IO) {
    // stderr logger should outlive the current scope. In case if an error appears, the scope is cancelled immediately, but the whole
    // intention of the stderr logger is to write logs of the remote process, which come from the remote machine to the local one with
    // a delay.
    val stderrLoggerScope = GlobalScope

    val stderrLogger = stderrLoggerScope.launch {
      val line = StringBuilder()
      try {
        while (true) {
          readLineWithoutBuffering(shellProcess.errorStream, line)
          LOG.debug { "IJent bootstrap shell session stderr: $line" }
          line.clear()
        }
      }
      catch (err: IOException) {
        LOG.debug { "IJent bootstrap shell session got an error: $err" }
      }
      finally {
        if (line.isNotEmpty()) {
          LOG.debug { "IJent bootstrap shell session stderr: $line" }
        }
      }
    }

    val exitCodeAwaiter = launch {
      val exitCode = shellProcess.awaitExit()
      if (isActive) {
        error("The process suddenly exited with the code $exitCode")
      }
    }

    val (remoteIjentPath, targetPlatform) =
      try {
        bootstrapOverShellSession(shellProcess.outputStream, shellProcess.inputStream)
      }
      finally {
        stderrLoggerScope.launch {
          try {
            delay(5.seconds)  // A random timeout.
          }
          finally {
            stderrLogger.cancel()
          }
        }
      }
    exitCodeAwaiter.cancel()

    try {
      remoteIjentPath to IjentSessionProvider.connect(communicationCoroutineScope, targetPlatform, shellProcess)
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

private suspend fun bootstrapOverShellSession(
  outputStream: OutputStream,
  inputStream: InputStream,
): Pair<String, IjentExecFileProvider.SupportedPlatform> = withContext(Dispatchers.IO) {
  // The boundary is for skipping various banners, greeting messages, PS1, etc.
  val boundary = (0..31).joinToString("") { "abcdefghijklmnopqrstuvwxyz0123456789".random().toString() }

  // The timeout is taken at random.
  val arch = withTimeout(10.seconds) {
    // There are two arguments in `uname` that can show the process architecture: `-m` and `-p`. According to `man uname`, `-p` is more
    // verbose, and that information may be sufficient for choosing the right binary.
    // https://man.freebsd.org/cgi/man.cgi?query=uname&sektion=1
    outputStream.write("set -ex; echo $boundary; uname -pm\n".toByteArray())
    outputStream.flush()

    do {
      val line = readLineWithoutBuffering(inputStream, tracingLabel = "stdout")
      LOG.trace { "Received greeting line from stdout: $line" }
    }
    while (line != boundary)

    readLineWithoutBuffering(inputStream, tracingLabel = "stdout")
  }.split(" ")

  val targetPlatform = when  {
    "x86_64" in arch -> IjentExecFileProvider.SupportedPlatform.X86_64__LINUX
    "aarch64" in arch -> IjentExecFileProvider.SupportedPlatform.AARCH64__LINUX
    else -> error("No binary for architecture $arch")  // TODO Some good exception class with an error message in UI.
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
  outputStream.write(script.toByteArray())
  yield()
  outputStream.flush()

  LOG.debug { "Sending the IJent binary for $targetPlatform" }
  ijentBinaryOnLocalDisk.inputStream().copyToAsync(outputStream)
  outputStream.flush()
  LOG.debug { "Sent the IJent binary for $targetPlatform" }

  val remotePathToBinary = readLineWithoutBuffering(inputStream, tracingLabel = "stdout")

  val joinedCmd = getIjentGrpcArgv(remotePathToBinary, selfDeleteOnExit = true).joinToString(" ")
  val commandLineArgs =
    """cd ${posixQuote(remotePathToBinary.substringBeforeLast('/'))}""" +
    """; exec "$(getent passwd "${'$'}(whoami)" | cut -d: -f7)" -c ${posixQuote(joinedCmd)}""" +
    "\n"
  LOG.trace { "Executing IJent inside a shell: ${commandLineArgs.trimEnd()}" }

  outputStream.write(commandLineArgs.toByteArray())
  outputStream.flush()

  remotePathToBinary to targetPlatform
}

/** The same stdin and stdout will be used for transferring binary data. Some buffering wrapper may occasionally consume too much data. */
private suspend fun readLineWithoutBuffering(
  stream: InputStream,
  buffer: StringBuilder = StringBuilder(),
  tracingLabel: String? = null,
): String =
  withContext(Dispatchers.IO) {
    while (true) {
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
    if (tracingLabel != null) {
      LOG.trace { "Read line from $tracingLabel: $buffer" }
    }
    buffer.toString()
  }

private val LOG = logger<IjentSessionProvider>()
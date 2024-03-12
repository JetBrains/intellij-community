// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.*
import com.intellij.util.io.awaitExit
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.takeWhile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * A wrapper for a [Process] that runs IJent. The wrapper logs stderr lines, waits for the exit code, terminates the process in case
 * of problems in the IDE.
 */
@ApiStatus.Internal
class IjentProcessWatcher private constructor(internal val process: Process, private val lastStderrMessages: SharedFlow<String?>) {
  enum class ExpectedErrorCode {
    /** During initialization, even a sudden successful exit is an error. */
    NO,

    /** IJent should exit with code 0 only if it has been terminated explicitly from the IDE side. */
    ZERO,

    /** If the process is being destroyed explicitly, on demand, there's no reason to report errors. */
    ANY,
  }

  @Volatile
  var expectedErrorCode = ExpectedErrorCode.NO

  /**
   * If an error happens, it is rethrown, but also the function waits for [lastStderrMessagesTimeout] and attaches all stderr lines
   * received since the start of execution of [body].
   */
  suspend fun <T> attachStderrOnError(body: suspend () -> T): T =
    coroutineScope {
      val stderr = StringBuilder()
      val collector = launch(CoroutineName("attachStderrOnError")) {
        collectLines(lastStderrMessages, stderr)
      }
      try {
        val result = body()
        collector.cancel()
        result
      }
      catch (err: Throwable) {
        runCatching {
          withTimeoutOrNull(lastStderrMessagesTimeout) {
            collector.join()
          }
        }.exceptionOrNull()?.let(err::addSuppressed)

        collector.cancel()

        // TODO Suppress RuntimeExceptionWithAttachments instead of wrapping when KT-66006 is resolved.
        //err.addSuppressed(RuntimeExceptionWithAttachments(
        //  "The error happened during handling $process",
        //  Attachment("stderr", stderr.toString()).apply { isIncluded = isIncluded or ApplicationManager.getApplication().isInternal },
        //))
        //throw err

        throw RuntimeExceptionWithAttachments(
          err.message ?: "",
          err,
          Attachment("stderr", stderr.toString()).apply { isIncluded = isIncluded or ApplicationManager.getApplication().isInternal },
        )
      }
    }

  companion object {
    /** See the docs of [IjentProcessWatcher] */
    @OptIn(DelicateCoroutinesApi::class)
    fun launch(coroutineScope: CoroutineScope, process: Process, ijentId: IjentId): IjentProcessWatcher {
      val lastStderrMessages = MutableSharedFlow<String?>(
        replay = 0,
        extraBufferCapacity = 30,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )

      // stderr logger should outlive the current scope. In case if an error appears, the scope is cancelled immediately, but the whole
      // intention of the stderr logger is to write logs of the remote process, which come from the remote machine to the local one with
      // a delay.
      GlobalScope.launch(blockingDispatcher + coroutineScope.coroutineNameAppended("$ijentId > stderr logger")) {
        ijentProcessStderrLogger(process, ijentId, lastStderrMessages)
      }

      val processWatcher = IjentProcessWatcher(process, lastStderrMessages)

      coroutineScope.launch(coroutineScope.coroutineNameAppended("$ijentId > exit code awaiter")) {
        ijentProcessExitCodeAwaiter(coroutineScope, ijentId, processWatcher, lastStderrMessages)
      }

      coroutineScope.launch(coroutineScope.coroutineNameAppended("$ijentId > finalizer")) {
        ijentProcessFinalizer(ijentId, processWatcher)
      }

      return processWatcher
    }

    @VisibleForTesting
    val lastStderrMessagesTimeout = 5.seconds // A random timeout.
  }
}

private suspend fun ijentProcessStderrLogger(process: Process, ijentId: IjentId, lastStderrMessages: MutableSharedFlow<String?>) {
  try {
    process.errorStream.reader().useLines { lines ->
      for (line in lines) {
        yield()
        if (line.isNotEmpty()) {
          logIjentStderr(ijentId, line)
          lastStderrMessages.emit(line)
        }
      }
    }
  }
  catch (err: IOException) {
    LOG.debug { "$ijentId bootstrap got an error: $err" }
  }
  finally {
    lastStderrMessages.emit(null)
  }
}

private fun logIjentStderr(ijentId: IjentId, line: String) {
  when (line.splitToSequence(spacesRegex).drop(1).take(1).firstOrNull()) {
    "TRACE" -> LOG.trace { "$ijentId log: $line" }
    "DEBUG" -> LOG.debug { "$ijentId log: $line" }
    "INFO" -> LOG.info("$ijentId log: $line")
    "WARN" -> LOG.warn("$ijentId log: $line")
    "ERROR" -> LOG.error("$ijentId log: $line")
    else -> LOG.debug { "$ijentId log: $line" }
  }
}

@OptIn(DelicateCoroutinesApi::class)
private suspend fun ijentProcessExitCodeAwaiter(
  ijentCoroutineScope: CoroutineScope,
  ijentId: IjentId,
  processWatcher: IjentProcessWatcher,
  lastStderrMessages: MutableSharedFlow<String?>,
) {
  val exitCode = processWatcher.process.awaitExit()
  LOG.debug { "IJent process $ijentId exited with code $exitCode" }

  val isExitExpected = when (processWatcher.expectedErrorCode) {
    IjentProcessWatcher.ExpectedErrorCode.NO -> false
    IjentProcessWatcher.ExpectedErrorCode.ZERO -> exitCode == 0
    IjentProcessWatcher.ExpectedErrorCode.ANY -> true
  }

  if (isExitExpected) {
    ijentCoroutineScope.cancel(CancellationException("The process expectedly exited with code $exitCode"))
  }
  else {
    val message = "The process $ijentId suddenly exited with the code $exitCode"

    // This coroutine must be bound to something that outlives `coroutineScope`, in order to not block its cancellation and
    // to not truncate the last lines of the logs, which are usually the most important.
    GlobalScope.launch {
      val stderr = StringBuilder()
      try {
        withTimeout(IjentProcessWatcher.lastStderrMessagesTimeout) {
          collectLines(lastStderrMessages, stderr)
        }
      }
      finally {
        // There's `LOG.error(message, Attachment)`, but it doesn't work well with `LoggedErrorProcessor.executeAndReturnLoggedError`.
        LOG.error(RuntimeExceptionWithAttachments(message, Attachment("stderr", stderr.toString())))
      }
    }
    ijentCoroutineScope.cancel(CancellationException(message))
  }
}

private suspend fun collectLines(lastStderrMessages: SharedFlow<String?>, stderr: StringBuilder) {
  lastStderrMessages
    .takeWhile { it != null }
    .filterNotNull()
    .collect { msg ->
      stderr.append(msg)
      stderr.append("\n")
    }
}

private val spacesRegex = Regex(" +")

@OptIn(DelicateCoroutinesApi::class)
private suspend fun ijentProcessFinalizer(ijentId: IjentId, watcher: IjentProcessWatcher) {
  try {
    awaitCancellation()
  }
  catch (err: Exception) {
    LOG.debug(err) { "$ijentId is going to be terminated due to receiving an error" }
    throw err
  }
  finally {
    watcher.expectedErrorCode = IjentProcessWatcher.ExpectedErrorCode.ANY
    val process = watcher.process
    if (process.isAlive) {
      GlobalScope.launch(Dispatchers.IO + CoroutineName("$ijentId destruction")) {
        try {
          process.waitFor(5, TimeUnit.SECONDS)  // A random timeout.
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

private val LOG = logger<IjentProcessWatcher>()
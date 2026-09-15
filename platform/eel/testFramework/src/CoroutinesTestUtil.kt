// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.testFramework

import com.intellij.execution.process.ProcessOutput
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.provider.utils.CopyError
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.platform.eel.provider.utils.copy
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.util.io.awaitExit
import com.intellij.util.io.computeDetached
import com.intellij.util.io.copyToAsync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
suspend fun <T> bodyLimitedCoroutineScope(body: suspend CoroutineScope.() -> T): T {
  contract {
    callsInPlace(body, InvocationKind.EXACTLY_ONCE)
  }
  return coroutineScope {
    try {
      val result = try {
        body()
      }
      catch (err: CancellationException) {
        throw err
      }
      catch (err: Throwable) {
        coroutineContext.cancelChildren(CancellationException("A sudden end of bodyLimitedCoroutineScope due to an error", err))
        throw err
      }
      coroutineContext.cancelChildren(CancellationException("A successful end of bodyLimitedCoroutineScope"))
      result
    }
    finally {
      for (child in coroutineContext.job.children) {
        runCatching { child.join() }
      }
    }
  }
}

@OptIn(DelicateCoroutinesApi::class)
suspend fun Process.getOutput(): ProcessOutput =
  withContext(Dispatchers.IO + CoroutineName("getOutput of $this")) {
    val (stdout, stderr) = listOf(inputStream, errorStream).map { stream ->
      async {
        val baos = ByteArrayOutputStream()
        try {
          stream.copyToAsync(baos)
        }
        catch (_: IOException) {
          // Ignore it. This function managed to read as much as possible and must return the result.
        }
        computeDetached { stream.close() }
        baos.toString(StandardCharsets.UTF_8)
      }
    }
    ProcessOutput(
      try {
        stdout.await()
      }
      catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        throw RuntimeException(e)
      },
      try {
        stderr.await()
      }
      catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
        throw RuntimeException(e)
      },
      awaitExit(),
      false,
      false,
    )
  }

suspend fun EelProcess.getOutput(): ProcessOutput {
  val stdout = ByteArrayOutputStream()
  val stderr = ByteArrayOutputStream()
  val exitCode: Int
  coroutineScope {
    suspend fun tryCopy(src: EelReceiveChannel, dst: EelSendChannel) {
      try {
        copy(src, dst)
      }
      catch (error: CopyError) {
        when (val e = error) {
          is CopyError.InError -> RuntimeException("Input error", e.cause)
          is CopyError.OutError -> RuntimeException("Output error", e.cause)
        }
      }
    }
    launch {
      tryCopy(this@getOutput.stdout, stdout.asEelChannel())
    }
    launch {
      tryCopy(this@getOutput.stderr, stderr.asEelChannel())
    }
    exitCode = this@getOutput.exitCode.await()
  }

  return ProcessOutput(
    stdout.toString(StandardCharsets.UTF_8),
    stderr.toString(StandardCharsets.UTF_8),
    exitCode,
    false,
    false,
  )
}

suspend fun executeAndReturnLoggedError(
  collectWarnings: Boolean = false,
  collectMessagesWithoutExceptions: Boolean = false,
  body: suspend () -> Unit,
): Throwable? =
  mutableListOf<Throwable>()
    .also {
      executeAndCollectLoggedErrors(
        it,
        limit = 1,
        collectWarnings = collectWarnings,
        collectMessagesWithoutExceptions = collectMessagesWithoutExceptions,
        body = body,
      )
    }
    .firstOrNull()

suspend fun executeAndCollectLoggedErrors(
  collection: MutableCollection<Throwable>,
  limit: Int = Int.MAX_VALUE,
  collectWarnings: Boolean = false,
  collectMessagesWithoutExceptions: Boolean = false,
  body: suspend () -> Unit,
) {
  // It's easier and more reliable than coroutines in this particular case.
  val startupLatch = CountDownLatch(1)
  val shutdownLatch = CountDownLatch(1)

  val loggedErrorProcessor = object : LoggedErrorProcessor() {
    override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
      processThrowable(message, t)
      return Action.NONE
    }

    override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
      return if (collectWarnings) {
        processThrowable(message, t)
        false
      }
      else {
        true
      }
    }

    private fun processThrowable(message: String, t: Throwable?) {
      val err = when {
        t != null -> t
        collectMessagesWithoutExceptions -> Throwable(message).also { it.stackTrace = arrayOf() }
        else -> null
      }
      if (err != null) {
        synchronized(collection) {
          collection += err
        }
        if (collection.size >= limit) {
          shutdownLatch.countDown()
        }
      }
    }
  }

  thread(isDaemon = true) {
    try {
      LoggedErrorProcessor.executeWith<Throwable>(loggedErrorProcessor) {
        startupLatch.countDown()
        shutdownLatch.await()
      }
    }
    finally {
      startupLatch.countDown()
    }
  }

  try {
    startupLatch.await()
    body()
  }
  finally {
    shutdownLatch.countDown()
  }
}

suspend fun ignoreLogError(body: suspend () -> Unit) {
  executeAndCollectLoggedErrors(mutableListOf(), limit = Int.MAX_VALUE, body = body)
}

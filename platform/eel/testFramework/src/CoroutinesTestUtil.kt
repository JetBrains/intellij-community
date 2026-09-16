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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs [body] in a test scope and cancels its remaining children when [body] completes.
 * Unlike [coroutineScope], this scope does not wait for children to complete before it starts cancellation.
 * The scope inherits the caller's context, but uses a separate job and an exception handler that collects uncaught failures.
 * If the caller is cancelled while waiting for [body], this function requests cancellation of the scope and propagates that cancellation.
 *
 * After [body] completes, waits for the children in a [NonCancellable] context for up to [finalizeTimeout].
 * Children that do not stop within this timeout can outlive this function.
 * If the timeout expires, throws [kotlinx.coroutines.TimeoutCancellationException]
 * unless a body failure or a collected uncaught failure takes precedence.
 *
 * Returns the result of [body] if cleanup completes without a timeout or a failure.
 * Otherwise, throws a body failure, a collected uncaught failure, or the timeout exception, in that order of precedence.
 * If cancellation wraps a failure, throws that failure instead.
 * Adds further failures as suppressed exceptions and wraps unexpected [CancellationException] instances in [Exception].
 *
 * @param finalizeTimeout the total time allowed to wait for children after cancellation; does not limit [body].
 * @param body the test code to invoke exactly once in the scope.
 */
@OptIn(ExperimentalContracts::class, DelicateCoroutinesApi::class)
@Suppress("LEAKED_IN_PLACE_LAMBDA", "WRONG_INVOCATION_KIND")  // False positive: the function is actually invoked in place and exactly once.
suspend fun <T> bodyLimitedCoroutineScope(
  finalizeTimeout: Duration = 3.seconds,  // The 3 seconds constant was taken at random
  body: suspend CoroutineScope.() -> T,
): T {
  contract {
    callsInPlace(body, InvocationKind.EXACTLY_ONCE)
  }

  val testResultDeferred = CompletableDeferred<Pair<Result<T>, Collection<Job>>>()

  val collectedRawErrors = ConcurrentHashMap.newKeySet<Throwable>()

  val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
    collectedRawErrors += exception
  }

  val testJob = GlobalScope.launch(
    context = currentCoroutineContext().minusKey(Job) + coroutineExceptionHandler,
    start = CoroutineStart.UNDISPATCHED,
  ) {
    val result = runCatching {
      val r = body()
      coroutineContext.ensureActive()
      r
    }
    testResultDeferred.complete(result to coroutineContext.job.children.toList())
    // The function has returned the result, but the coroutine can keep running because of children jobs.
  }

  var (finalResult, children) = try {
    testResultDeferred.await()
  }
  catch (validCeFromParent: CancellationException) {
    // `cancel` may block in certain scenarios. It's code for tests, we assume bizarre bugs here.
    GlobalScope.launch { testJob.cancel(validCeFromParent) }
    throw validCeFromParent
  }

  val allSeenErrors = hashSetOf<Throwable>()
  finalResult.exceptionOrNull()?.let(allSeenErrors::add)

  finalResult = finalResult.fold(
    onSuccess = { Result.success(it) },
    onFailure = { caughtError ->
      Result.failure(
        generateSequence(caughtError, Throwable::cause).find { it !is CancellationException }
        ?: Exception("Unexpected CancellationException: $caughtError", caughtError)
      )
    }
  )
  finalResult.exceptionOrNull()?.let(allSeenErrors::add)

  val errorForChildren: CancellationException = finalResult.fold(
    onSuccess = {
      CancellationException("A successful end of bodyLimitedCoroutineScope")
    },
    onFailure = { cause ->
      CancellationException("A sudden end of bodyLimitedCoroutineScope due to an error: $cause", cause)
    }
  )

  for (child in children) {
    // `cancel` may block in certain scenarios. It's code for tests, we assume bizarre bugs here.
    GlobalScope.launch { child.cancel(errorForChildren) }
  }

  // This awaiting loop is required for preventing false positive results.
  // Some child coroutine may throw an important exception after cancelling. This code catches them.
  try {
    withContext(NonCancellable) {
      withTimeout(finalizeTimeout) {
        for (child in children) {
          child.join()
        }
      }
    }
  }
  finally {
    val parentCancellationException = runCatching { currentCoroutineContext().ensureActive() }.exceptionOrNull()

    for (rawError in collectedRawErrors) {
      val errorToMention = when {
        rawError == parentCancellationException -> null
        !allSeenErrors.add(rawError) -> null
        rawError is CancellationException -> Exception("Unexpected CancellationException: $rawError", rawError)
        else -> rawError
      }

      if (errorToMention != null) {
        finalResult = finalResult.fold(
          onSuccess = { Result.failure(errorToMention) },
          onFailure = { firstErr ->
            firstErr.addSuppressed(errorToMention)
            Result.failure(firstErr)
          }
        )
      }
    }
    finalResult.getOrThrow()
  }

  return finalResult.getOrThrow()
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

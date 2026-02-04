// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionThreadingBase.Companion.isInBatchUpdate
import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * There are two implementations of completion threading:
 * - [SyncCompletion] - runs [startThread] in the current thread
 * - [AsyncCompletion] - runs [startThread] in a coroutine
 *
 * @see CompletionProgressIndicator.getCompletionThreading
 */
internal sealed interface CompletionThreading {
  // Deferred and not Job - client should get error
  fun startThread(progressIndicator: ProgressIndicator?, runnable: Runnable): Deferred<*>

  fun createConsumer(indicator: CompletionProgressIndicator): CompletionConsumer
}

/**
 * Completion result consumer that weighs items according to specified weights
 */
@Suppress("UsagesOfObsoleteApi")
internal interface CompletionConsumer : Consumer<CompletionResult> {
  /**
   * await for weighing thread to finish
   */
  fun waitFor()
}

internal class SyncCompletion : CompletionThreadingBase() {
  private val batchList = ArrayList<CompletionResult>()

  override fun startThread(progressIndicator: ProgressIndicator?, runnable: Runnable): Deferred<*> {
    ProgressManager.getInstance().runProcess(runnable, progressIndicator)
    // we must not return null - `null` has a special meaning
    return CompletableDeferred(Unit)
  }

  override fun createConsumer(indicator: CompletionProgressIndicator): CompletionConsumer =
    SyncConsumer(indicator, batchList)

  override fun flushBatchResult(indicator: CompletionProgressIndicator) {
    try {
      indicator.withSingleUpdate {
        for (result in batchList) {
          indicator.addItem(result)
        }
      }
    }
    finally {
      batchList.clear()
    }
  }
}

private class SyncConsumer(
  val indicator: CompletionProgressIndicator,
  val batchList: ArrayList<CompletionResult>,
) : CompletionConsumer {
  override fun waitFor() {
    indicator.addDelayedMiddleMatches()
  }

  override fun consume(result: CompletionResult) {
    if (isInBatchUpdate.get()) {
      batchList.add(result)
    }
    else {
      indicator.addItem(result)
    }
  }
}

internal fun tryReadOrCancel(indicator: ProgressIndicator, runnable: Runnable) {
  val wasReadActionStarted = ApplicationManagerEx.getApplicationEx().tryRunReadAction {
    indicator.checkCanceled()
    runnable.run()
  }

  if (wasReadActionStarted) return

  indicator.cancel()
  indicator.checkCanceled()
}

internal class AsyncCompletion(project: Project?) : CompletionThreadingBase() {
  private val batchList = ArrayList<CompletionResult>()
  private val workingQueue = LinkedBlockingQueue<AddingEvent>()
  private val coroutineScope = ((project ?: ApplicationManagerEx.getApplicationEx()) as ComponentManagerEx).getCoroutineScope()

  override fun startThread(progressIndicator: ProgressIndicator?, runnable: Runnable): Deferred<*> {
    val startSemaphore = Semaphore()
    startSemaphore.down()
    val task = {
      ProgressManager.getInstance().runProcess(
        {
          try {
            startSemaphore.up()
            ProgressManager.checkCanceled()
            runnable.run()
          }
          catch (@Suppress("IncorrectCancellationExceptionHandling") _: ProcessCanceledException) {
          }
        }, progressIndicator)
    }
    val future = coroutineScope.async(Dispatchers.IO + ClientId.coroutineContext()) {
      try {
        task()
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        logger<AsyncCompletion>().error(e)
      }
    }
    startSemaphore.waitFor()
    return future
  }

  override fun createConsumer(indicator: CompletionProgressIndicator): CompletionConsumer {
    val addItemJob = AddItemJob(workingQueue, indicator)
    val addItemJobHook = startThread(ProgressWrapper.wrap(indicator), addItemJob)
    return CompletionConsumerImpl(workingQueue, addItemJobHook, batchList)
  }

  override fun flushBatchResult(indicator: CompletionProgressIndicator) {
    if (batchList.isEmpty()) {
      return
    }

    val batchListCopy = ArrayList(batchList)
    batchList.clear()

    workingQueue.offer(AddBatch(batchListCopy))
  }
}

@Suppress("SSBasedInspection")
@TestOnly
internal fun checkForExceptions(future: Deferred<*>) {
  runBlocking {
    future.await()
  }
}

/**
 * The job that processes [AddingEvent]s in a separate thread from [CompletionContributor]s.
 */
private class AddItemJob(
  val workingQueue: LinkedBlockingQueue<AddingEvent>,
  val indicator: CompletionProgressIndicator,
) : Runnable {
  override fun run() {
    try {
      val batch = mutableListOf<AddingEvent>()
      while (true) {
        indicator.checkCanceled()

        workingQueue.drainTo(batch)
        if (batch.isEmpty()) {
          // try awaiting the next event
          val next = workingQueue.poll(30, TimeUnit.MILLISECONDS) ?: continue
          batch.add(next)
        }

        var stop = false
        tryReadOrCancel(indicator) {
          indicator.withSingleUpdate {
            for (event in batch) {
              indicator.checkCanceled()
              when (event) {
                is AddItem -> {
                  indicator.addItem(event.result)
                }
                is AddBatch -> {
                  for (result in event.results) {
                    indicator.addItem(result)
                  }
                }
                Stop -> {
                  indicator.addDelayedMiddleMatches()
                  stop = true
                  break
                }
              }
            }
          }
        }

        if (stop) {
          return
        }

        batch.clear()
      }
    }
    catch (e: InterruptedException) {
      logger<AsyncCompletion>().error(e)
    }
  }
}

/**
 * Delegates all the work to [AddItemJob] working on another thread by passing events to [workingQueue].
 */
private class CompletionConsumerImpl(
  val workingQueue: LinkedBlockingQueue<AddingEvent>,
  val addItemJob: Deferred<*>,
  val batchList: ArrayList<CompletionResult>,
) : CompletionConsumer {

  override fun consume(result: CompletionResult) {
    if (isInBatchUpdate.get()) {
      batchList.add(result)
    }
    else {
      workingQueue.offer(AddItem(result))
    }
  }

  override fun waitFor() {
    workingQueue.offer(Stop)
    awaitAddItemJobToStop()
  }

  fun awaitAddItemJobToStop() {
    try {
      @Suppress("SSBasedInspection")
      runBlocking {
        addItemJob.await()
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      logger<AsyncCompletion>().error(e)
    }
  }
}

private sealed interface AddingEvent
private class AddItem(val result: CompletionResult) : AddingEvent
private class AddBatch(val results: List<CompletionResult>) : AddingEvent
private object Stop : AddingEvent
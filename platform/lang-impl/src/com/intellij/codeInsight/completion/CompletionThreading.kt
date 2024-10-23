// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

internal sealed interface CompletionThreading {
  // Deferred and not Job - client should get error
  fun startThread(progressIndicator: ProgressIndicator?, runnable: Runnable): Deferred<*>

  fun delegateWeighing(indicator: CompletionProgressIndicator): WeighingDelegate
}

@Suppress("UsagesOfObsoleteApi")
internal interface WeighingDelegate : com.intellij.util.Consumer<CompletionResult> {
  fun waitFor()
}

internal class SyncCompletion : CompletionThreadingBase() {
  private val batchList = ArrayList<CompletionResult>()

  override fun startThread(progressIndicator: ProgressIndicator?, runnable: Runnable): Deferred<*> {
    ProgressManager.getInstance().runProcess(runnable, progressIndicator)
    // we must not return null - `null` has a special meaning
    return CompletableDeferred(Unit)
  }

  override fun delegateWeighing(indicator: CompletionProgressIndicator): WeighingDelegate {
    return object : WeighingDelegate {
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
  }

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

internal fun tryReadOrCancel(indicator: ProgressIndicator, runnable: Runnable) {
  if (!ApplicationManagerEx.getApplicationEx().tryRunReadAction {
      indicator.checkCanceled()
      runnable.run()
    }) {
    indicator.cancel()
    indicator.checkCanceled()
  }
}

internal class AsyncCompletion(project: Project?) : CompletionThreadingBase() {
  private val batchList = ArrayList<CompletionResult>()
  private val queue = LinkedBlockingQueue<() -> Boolean>()

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
          catch (_: ProcessCanceledException) {
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

  override fun delegateWeighing(indicator: CompletionProgressIndicator): WeighingDelegate {
    class WeighItems : Runnable {
      override fun run() {
        try {
          while (true) {
            val next = queue.poll(30, TimeUnit.MILLISECONDS)
            if (next != null && !next()) {
              tryReadOrCancel(indicator) { indicator.addDelayedMiddleMatches() }
              return
            }
            indicator.checkCanceled()
          }
        }
        catch (e: InterruptedException) {
          logger<AsyncCompletion>().error(e)
        }
      }
    }

    val future = startThread(ProgressWrapper.wrap(indicator), WeighItems())
    return object : WeighingDelegate {
      override fun waitFor() {
        queue.offer { false }
        try {
          @Suppress("SSBasedInspection")
          runBlocking {
            future.await()
          }
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Exception) {
          logger<AsyncCompletion>().error(e)
        }
      }

      override fun consume(result: CompletionResult) {
        if (isInBatchUpdate.get()) {
          batchList.add(result)
        }
        else {
          queue.offer {
            tryReadOrCancel(indicator) { indicator.addItem(result) }
            true
          }
        }
      }
    }
  }

  override fun flushBatchResult(indicator: CompletionProgressIndicator) {
    if (batchList.isEmpty()) {
      return
    }

    val batchListCopy = ArrayList(batchList)
    batchList.clear()

    queue.offer {
      tryReadOrCancel(indicator) {
        indicator.withSingleUpdate {
          for (result in batchListCopy) {
            indicator.addItem(result)
          }
        }
      }
      true
    }
  }
}

@Suppress("SSBasedInspection")
@TestOnly
internal fun checkForExceptions(future: Deferred<*>) {
  runBlocking {
    future.await()
  }
}

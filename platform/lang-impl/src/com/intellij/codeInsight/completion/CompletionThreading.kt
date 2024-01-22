// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Computable.PredefinedValueComputable
import com.intellij.util.concurrency.Semaphore
import java.util.concurrent.*

internal sealed interface CompletionThreading {
  fun startThread(progressIndicator: ProgressIndicator?, runnable: Runnable): Future<*>?

  fun delegateWeighing(indicator: CompletionProgressIndicator): WeighingDelegate
}

@Suppress("UsagesOfObsoleteApi")
internal interface WeighingDelegate : com.intellij.util.Consumer<CompletionResult> {
  fun waitFor()
}

internal class SyncCompletion : CompletionThreadingBase() {
  private val batchList = ArrayList<CompletionResult>()

  override fun startThread(progressIndicator: ProgressIndicator?, runnable: Runnable): Future<*> {
    ProgressManager.getInstance().runProcess(runnable, progressIndicator)
    return CompletableFuture.completedFuture(true)
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

internal class AsyncCompletion : CompletionThreadingBase() {
  private val batchList = ArrayList<CompletionResult>()
  private val queue = LinkedBlockingQueue<Computable<Boolean>>()

  companion object {
    private val LOG = logger<AsyncCompletion>()

    @JvmStatic
    fun tryReadOrCancel(indicator: ProgressIndicator, runnable: Runnable) {
      if (!ApplicationManagerEx.getApplicationEx().tryRunReadAction {
          indicator.checkCanceled()
          runnable.run()
        }) {
        indicator.cancel()
        indicator.checkCanceled()
      }
    }
  }

  override fun startThread(progressIndicator: ProgressIndicator?, runnable: Runnable): Future<*> {
    val startSemaphore = Semaphore()
    startSemaphore.down()
    val future = ApplicationManager.getApplication().executeOnPooledThread {
      ProgressManager.getInstance().runProcess(
        {
          try {
            startSemaphore.up()
            ProgressManager.checkCanceled()
            runnable.run()
          }
          catch (ignored: ProcessCanceledException) {
          }
        }, progressIndicator)
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
            if (next != null && !next.compute()) {
              tryReadOrCancel(indicator) { indicator.addDelayedMiddleMatches() }
              return
            }
            indicator.checkCanceled()
          }
        }
        catch (e: InterruptedException) {
          LOG.error(e)
        }
      }
    }

    val future = startThread(ProgressWrapper.wrap(indicator), WeighItems())
    return object : WeighingDelegate {
      override fun waitFor() {
        queue.offer(PredefinedValueComputable(false))
        try {
          future.get()
        }
        catch (e: InterruptedException) {
          LOG.error(e)
        }
        catch (e: ExecutionException) {
          LOG.error(e)
        }
      }

      override fun consume(result: CompletionResult) {
        if (isInBatchUpdate.get()) {
          batchList.add(result)
        }
        else {
          queue.offer(Computable {
            tryReadOrCancel(indicator) { indicator.addItem(result) }
            true
          })
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


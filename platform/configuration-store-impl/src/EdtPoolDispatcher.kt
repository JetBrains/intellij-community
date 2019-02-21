// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.application.coroutineExceptionHandler
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

/**
 * Only for configuration store usages.
 */
internal val storeEdtCoroutineContext: CoroutineContext by lazy {
  EdtPoolDispatcher(emptyList()) + coroutineExceptionHandler
}

internal fun createStoreEdtCoroutineContext(rules: List<InTransactionRule>): CoroutineContext {
  return when {
    rules.isEmpty() -> storeEdtCoroutineContext
    else -> EdtPoolDispatcher(rules) + coroutineExceptionHandler
  }
}

internal abstract class EdtTaskRule {
  abstract fun dispatch(rules: List<EdtTaskRule>, ruleIndex: Int, block: Runnable)

  protected fun computeRunnable(nextRuleIndex: Int, rules: List<EdtTaskRule>, block: Runnable): Runnable {
    return when (nextRuleIndex) {
      rules.size -> block
      else -> Runnable { rules.get(nextRuleIndex).dispatch(rules, nextRuleIndex + 1, block) }
    }
  }
}

// opposite to write actions and so on, submitted transaction is not executed immediately, so, we need dispatcher
internal class InTransactionRule(private val disposable: Disposable) : EdtTaskRule() {
  private val transactionId = TransactionGuard.getInstance().contextTransaction

  override fun dispatch(rules: List<EdtTaskRule>, ruleIndex: Int, block: Runnable) {
    TransactionGuard.getInstance().submitTransaction(ApplicationManager.getApplication(), transactionId, computeRunnable(ruleIndex, rules, Runnable {
      if (Disposer.isDisposed(disposable)) {
        throw CancellationException()
      }
      block.run()
    }))
  }
}

internal class EdtPoolDispatcherManager {
  private val queue = ArrayDeque<Runnable>()
  private var isScheduled = AtomicBoolean()

  fun dispatch(block: Runnable) {
    synchronized(queue) {
      queue.add(block)
    }

    scheduleFlush()
  }

  private fun scheduleFlush() {
    if (isScheduled.compareAndSet(false, true)) {
      ApplicationManager.getApplication().invokeLater(this::processQueue)
    }
  }

  private fun getNextTask(): Runnable? {
    synchronized(queue) {
      return queue.pollFirst()
    }
  }

  fun processTasks() {
    ApplicationManager.getApplication().assertIsDispatchThread()
    isScheduled.set(true)
    processQueue()
  }

  private fun processQueue() {
    try {
      while (true) {
        val task = getNextTask() ?: return
        LOG.runAndLogException {
          // exception not expected here because kotlin must handle it (runnable here is kotlin wrapper around user task)
          task.run()
        }
      }
    }
    finally {
      isScheduled.set(false)
      val isFlushNeeded = synchronized(queue) {
        // or error occurred and we need to process rest of tasks,
        // or new tasks were added but flush not scheduled because isScheduled is setting to false on the end of processing, not on begin.
        !queue.isEmpty()
      }

      if (isFlushNeeded) {
        // do not process again - as LaterInvocator, prefer to process in small batches
        scheduleFlush()
      }
    }
  }
}

private class EdtPoolDispatcher(private val rules: List<EdtTaskRule>) : CoroutineDispatcher() {
  private val edtPoolDispatcherManager: EdtPoolDispatcherManager
    get() = (SaveAndSyncHandler.getInstance() as BaseSaveAndSyncHandler).edtPoolDispatcherManager

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    if (rules.isEmpty()) {
      edtPoolDispatcherManager.dispatch(block)
    }
    else {
      val wrappedTask = Runnable {
        rules.get(0).dispatch(rules, 1, block)
      }

      if (ApplicationManager.getApplication().isDispatchThread) {
        wrappedTask.run()
      }
      else {
        edtPoolDispatcherManager.dispatch(wrappedTask)
      }
    }
  }

  @ExperimentalCoroutinesApi
  override fun isDispatchNeeded(context: CoroutineContext): Boolean {
    return !rules.isEmpty() || !ApplicationManager.getApplication().isDispatchThread
  }

  override fun toString() = "store EDT dispatcher"
}
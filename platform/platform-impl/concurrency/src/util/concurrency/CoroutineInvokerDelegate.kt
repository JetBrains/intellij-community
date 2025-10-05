// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.getOrHandleException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

internal abstract class CoroutineInvokerDelegate(
  override val description: String,
  protected val scope: CoroutineScope,
) : InvokerDelegate {
  override fun dispose() {
    scope.cancel()
  }

  override fun offer(runnable: Runnable, delay: Int, promise: Promise<*>) {
    val job = doLaunch(CoroutineName(runnable.toString()), delay) {
      kotlin.runCatching {
        runnable.run()
      }.getOrHandleException {
        Invoker.LOG.error("$description: Task $runnable threw an unexpected exception", it)
      }
    }
    promise.onProcessed { job.cancel("Promise was cancelled") }
  }

  protected abstract fun doLaunch(coroutineName: CoroutineName, delay: Int, runnable: () -> Unit): Job

  override fun run(task: Runnable, promise: AsyncPromise<*>): Boolean {
    task.run()
    return true
  }
}

internal class EdtCoroutineInvokerDelegate(
  description: String,
  scope: CoroutineScope,
) : CoroutineInvokerDelegate(description, scope) {
  override fun doLaunch(coroutineName: CoroutineName, delay: Int, runnable: () -> Unit): Job {
    // For running on the EDT we can't use a semaphore because of modality.
    // Reentrant calls are possible while somewhere up the stack some code
    // is already running inside the same Invoker.
    // Instead, for ordering we rely on withContext(Dispatchers.EDT),
    // as it'll queue invocation events in the order it's called.
    return scope.launch(coroutineName, start = CoroutineStart.UNDISPATCHED) {
      delay(delay.toLong())
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        yield()
        runnable()
      }
    }
  }
}

internal abstract class BgtCoroutineInvokerDelegate(
  description: String,
  scope: CoroutineScope,
  private val useReadAction: Boolean,
) : CoroutineInvokerDelegate(description, scope) {
  protected suspend fun withProperContext(runnable: () -> Unit) {
    if (useReadAction) {
      readAction {
        runnable()
      }
    }
    else {
      runnable()
    }
  }
}

internal class SequentialBgtCoroutineInvokerDelegate(
  description: String,
  scope: CoroutineScope,
  useReadAction: Boolean,
) : BgtCoroutineInvokerDelegate(description, scope, useReadAction) {
  private val semaphore = Semaphore(1)

  override fun doLaunch(coroutineName: CoroutineName, delay: Int, runnable: () -> Unit): Job {
    return scope.launch(coroutineName, start = CoroutineStart.UNDISPATCHED) {
      delay(delay.toLong())
      semaphore.withPermit {
        yield() // The usual UNDISPATCHED-yield thing to ensure FIFO execution order.
        withProperContext {
          runnable()
        }
      }
    }
  }
}

internal class ConcurrentBgtCoroutineInvokerDelegate(
  description: String,
  scope: CoroutineScope,
  useReadAction: Boolean,
  concurrency: Int,
) : BgtCoroutineInvokerDelegate(description, scope, useReadAction) {
  private val semaphore = Semaphore(concurrency)

  override fun doLaunch(coroutineName: CoroutineName, delay: Int, runnable: () -> Unit): Job {
    // No need for UNDISPATCHED-yield here because concurrent invokers don't and can't gurantee ordering.
    return scope.launch(coroutineName) {
      delay(delay.toLong())
      semaphore.withPermit {
        withProperContext {
          runnable()
        }
      }
    }
  }
}

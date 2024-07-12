// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm.ThreadToUse
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val LOG: Logger = logger<SingleAlarm>()

/**
 * Use a [kotlinx.coroutines.flow.Flow] with [kotlinx.coroutines.flow.debounce] and [kotlinx.coroutines.flow.sample] instead.
 * Alarm is deprecated.
 * Allows scheduling a single `Runnable` instance ([task]) to be executed after a specific time interval on a specific thread.
 * [request] adds a request if it's not scheduled yet, i.e., it does not delay execution of the request
 * [cancelAndRequest] cancels the current request and schedules a new one instead, i.e., it delays execution of the request
 *
 */
class SingleAlarm @Internal constructor(
  private val task: Runnable,
  private val delay: Int,
  parentDisposable: Disposable?,
  threadToUse: ThreadToUse = ThreadToUse.SWING_THREAD,
  modalityState: ModalityState? = if (threadToUse == ThreadToUse.SWING_THREAD) ModalityState.nonModal() else null,
  coroutineScope: CoroutineScope? = null,
) : Disposable {
  // it is a supervisor coroutine scope
  private val taskCoroutineScope: CoroutineScope
  private val taskContext: CoroutineContext

  private val isScopeShared: Boolean

  private val LOCK = Any()

  // guarded by LOCK
  private var currentJob: Job? = null

  private val inEdt: Boolean = threadToUse == ThreadToUse.SWING_THREAD

  constructor(task: Runnable, delay: Int, threadToUse: ThreadToUse, parentDisposable: Disposable) : this(
    task = task,
    delay = delay,
    parentDisposable = parentDisposable,
    threadToUse = threadToUse,
    modalityState = if (threadToUse == ThreadToUse.SWING_THREAD) ModalityState.nonModal() else null,
  )

  constructor(
    task: Runnable,
    delay: Int,
    parentDisposable: Disposable?,
    threadToUse: ThreadToUse,
    modalityState: ModalityState?,
  ) : this(
    task = task,
    delay = delay,
    parentDisposable = parentDisposable,
    threadToUse = threadToUse,
    modalityState = modalityState,
    coroutineScope = null,
  )

  constructor(
    task: Runnable,
    delay: Int,
    parentDisposable: Disposable,
  ) : this(
    task = task,
    delay = delay,
    parentDisposable = parentDisposable,
    threadToUse = ThreadToUse.SWING_THREAD,
    modalityState = ModalityState.nonModal(),
    coroutineScope = null,
  )

  @Deprecated("Please use flow instead of SingleAlarm")
  constructor(
    task: Runnable,
    delay: Int,
    parentDisposable: Disposable,
    threadToUse: ThreadToUse,
  ) : this(
    task = task,
    delay = delay,
    parentDisposable = parentDisposable,
    threadToUse = threadToUse,
    modalityState = if (threadToUse == ThreadToUse.SWING_THREAD) ModalityState.nonModal() else null,
    coroutineScope = null,
  )

  constructor(task: Runnable, delay: Int) : this(
    task = task,
    delay = delay,
    parentDisposable = null,
    threadToUse = ThreadToUse.SWING_THREAD,
    modalityState = ModalityState.nonModal(),
  )

  init {
    var context = ClientId.currentOrNull?.asContextElement() ?: EmptyCoroutineContext
    if (inEdt) {
      if (modalityState == null) {
        throw IllegalArgumentException("modalityState must be not null if threadToUse == ThreadToUse.SWING_THREAD")
      }

      // maybe not defined in tests
      context += Dispatchers.EDT + modalityState.asContextElement()
    }
    // todo fix clients and remove NonCancellable
    taskContext = context + NonCancellable

    if (coroutineScope == null) {
      val app = ApplicationManager.getApplication()
      if (app == null) {
        LOG.error("Do not use an alarm in an early executing code")
        @file:OptIn(DelicateCoroutinesApi::class)
        taskCoroutineScope = GlobalScope
      }
      else {
        taskCoroutineScope = service<SingleAlarmSharedCoroutineScopeHolder>().coroutineScope
      }
      isScopeShared = true

      parentDisposable?.let {
        Disposer.register(it, this)
      }
    }
    else {
      isScopeShared = false
      taskCoroutineScope = coroutineScope
    }
  }

  companion object {
    fun pooledThreadSingleAlarm(delay: Int, parentDisposable: Disposable, task: () -> Unit): SingleAlarm {
      return SingleAlarm(
        task = task,
        delay = delay,
        threadToUse = ThreadToUse.POOLED_THREAD,
        parentDisposable = parentDisposable,
        coroutineScope = null,
      )
    }

    fun pooledThreadSingleAlarm(delay: Int, coroutineScope: CoroutineScope, task: () -> Unit): SingleAlarm {
      return SingleAlarm(
        task = task,
        delay = delay,
        threadToUse = ThreadToUse.POOLED_THREAD,
        parentDisposable = null,
        coroutineScope = coroutineScope,
      )
    }

    @JvmStatic
    fun singleAlarm(delay: Int, coroutineScope: CoroutineScope, task: Runnable): SingleAlarm {
      return SingleAlarm(
        task = task,
        delay = delay,
        parentDisposable = null,
        threadToUse = ThreadToUse.POOLED_THREAD,
        coroutineScope = coroutineScope,
      )
    }

    @Internal
    @JvmStatic
    fun singleEdtAlarm(delay: Int, coroutineScope: CoroutineScope, task: Runnable): SingleAlarm {
      return SingleAlarm(
        task = task,
        delay = delay,
        parentDisposable = null,
        threadToUse = ThreadToUse.SWING_THREAD,
        coroutineScope = coroutineScope,
      )
    }

    @Internal
    fun singleEdtAlarm(delay: Int, parentDisposable: Disposable, task: Runnable): SingleAlarm {
      return SingleAlarm(
        task = task,
        delay = delay,
        parentDisposable = parentDisposable,
        threadToUse = ThreadToUse.SWING_THREAD,
        coroutineScope = null,
      )
    }
  }

  override fun dispose() {
    cancel()
  }

  val isDisposed: Boolean
    get() = !taskCoroutineScope.isActive

  val isEmpty: Boolean
    get() = synchronized(LOCK) { currentJob == null }

  @TestOnly
  fun waitForAllExecuted(timeout: Long, timeUnit: TimeUnit) {
    assert(ApplicationManager.getApplication().isUnitTestMode)

    val currentJob = currentJob ?: return
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      try {
        withTimeout(timeUnit.toMillis(timeout)) {
          currentJob.join()
        }
      }
      catch (e: TimeoutCancellationException) {
        // compatibility - throw TimeoutException as before
        throw TimeoutException(e.message)
      }
    }
  }

  @JvmOverloads
  fun request(forceRun: Boolean = false, delay: Int = this@SingleAlarm.delay) {
    val effectiveDelay = if (forceRun) 0 else delay.toLong()
    synchronized(LOCK) {
      if (currentJob != null) {
        return
      }

      currentJob = taskCoroutineScope.launch {
        delay(effectiveDelay)
        withContext(taskContext) {
          try {
            task.run()
          }
          catch (e: CancellationException) {
            throw e
          }
          catch (e: Throwable) {
            LOG.error(e)
          }
        }
      }.also { job ->
        job.invokeOnCompletion {
          synchronized(LOCK) {
            if (currentJob === job) {
              currentJob = null
            }
          }
        }
      }
    }
  }

  /**
   * Cancel doesn't interrupt already running task.
   */
  fun cancel() {
    synchronized(LOCK) {
      currentJob?.also {
        currentJob = null
      }
    }?.cancel()
  }

  /**
   * Cancel doesn't interrupt already running task.
   */
  @JvmOverloads
  fun cancelAndRequest(forceRun: Boolean = false) {
    cancel()
    request(forceRun = forceRun)
  }

  @Deprecated("Use cancel")
  fun cancelAllRequests(): Int {
    val currentJob = synchronized(LOCK) {
      currentJob?.also {
        currentJob = null
      }
    } ?: return 0
    currentJob.cancel()
    return 1
  }
}

@Internal
@Service
private class SingleAlarmSharedCoroutineScopeHolder(@JvmField val coroutineScope: CoroutineScope)
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DeprecatedCallableAddReplaceWith")

package com.intellij.util

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.CoroutineSupport
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm.ThreadToUse
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.TestOnly
import java.awt.EventQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

private val LOG: Logger = logger<SingleAlarm>()

/**
 * Use a [kotlinx.coroutines.flow.Flow] with [kotlinx.coroutines.flow.debounce] and [kotlinx.coroutines.flow.sample] instead.
 * Alarm is deprecated.
 * Allows scheduling a single `Runnable` instance ([task]) to be executed after a specific time interval on a specific thread.
 * [request] adds a request if it's not scheduled yet, i.e., it does not delay execution of the request
 * [cancelAndRequest] cancels the current request and schedules a new one instead, i.e., it delays execution of the request.
 */
@Obsolete
class SingleAlarm @Internal constructor(
  private val task: Runnable,
  private val delay: Int,
  parentDisposable: Disposable?,
  threadToUse: ThreadToUse,
  modalityState: ModalityState? = if (threadToUse == ThreadToUse.SWING_THREAD) ModalityState.nonModal() else null,
  coroutineScope: CoroutineScope? = null,
) : Disposable {
  // it is a supervisor coroutine scope
  private val taskCoroutineScope: CoroutineScope

  private val taskContext: CoroutineContext

  private val LOCK = Any()

  // guarded by LOCK
  private var currentJob: Job? = null

  @Deprecated("Please use flow instead of SingleAlarm")
  constructor(task: Runnable, delay: Int, threadToUse: ThreadToUse, parentDisposable: Disposable) : this(
    task = task,
    delay = delay,
    parentDisposable = parentDisposable,
    threadToUse = threadToUse,
    modalityState = if (threadToUse == ThreadToUse.SWING_THREAD) ModalityState.nonModal() else null,
    coroutineScope = null,
  )

  @Deprecated("Please use flow instead of SingleAlarm")
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

  @Internal
  constructor(
    task: Runnable,
    delay: Int,
    parentDisposable: Disposable,
    modalityState: ModalityState,
  ) : this(
    task = task,
    delay = delay,
    parentDisposable = parentDisposable,
    threadToUse = ThreadToUse.SWING_THREAD,
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
    coroutineScope = null,
    threadToUse = ThreadToUse.SWING_THREAD,
    modalityState = ModalityState.nonModal(),
  )

  init {
    var context = ClientId.currentOrNull?.asContextElement() ?: EmptyCoroutineContext
    if (threadToUse == ThreadToUse.SWING_THREAD) {
      // maybe not defined in tests
      context += getEdtDispatcher()
      if (modalityState != null) {
        context += modalityState.asContextElement()
      }
    }

    taskContext = context

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

      parentDisposable?.let {
        Disposer.register(it, this)
      }
    }
    else {
      taskCoroutineScope = coroutineScope
    }
  }

  companion object {
    @Deprecated("Please use flow instead of SingleAlarm")
    fun pooledThreadSingleAlarm(delay: Int, parentDisposable: Disposable, task: () -> Unit): SingleAlarm {
      return SingleAlarm(
        task = task,
        delay = delay,
        threadToUse = ThreadToUse.POOLED_THREAD,
        parentDisposable = parentDisposable,
        coroutineScope = null,
      )
    }

    fun singleAlarm(delay: Int, coroutineScope: CoroutineScope, task: () -> Unit): SingleAlarm {
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

    @Internal
    fun getEdtDispatcher(): CoroutineContext {
      val edtDispatcher = ApplicationManager.getApplication()?.serviceOrNull<CoroutineSupport>()?.edtDispatcher()
      if (edtDispatcher == null) {
        // cannot be as error - not clear what to do in case of `RangeTimeScrollBarTest`
        LOG.warn("Do not use an alarm in an early executing code")
        return object : CoroutineDispatcher() {
          override fun dispatch(context: CoroutineContext, block: Runnable) {
            EventQueue.invokeLater(block)
          }

          override fun toString() = "Swing"
        }
      }
      else {
        return edtDispatcher
      }
    }
  }

  override fun dispose() {
    isDisposed = true
    cancel()
  }

  // SingleAlarm can be created without `parentDisposable`.
  // So, we cannot create child scope. So, we cannot use `!taskCoroutineScope.isActive` to implement `isDisposed`.
  @Volatile
  var isDisposed: Boolean = false
    get() = field || !taskCoroutineScope.isActive
    private set

  val isEmpty: Boolean
    get() = synchronized(LOCK) { currentJob == null }

  @TestOnly
  fun waitForAllExecuted(timeout: Long, timeUnit: TimeUnit) {
    require(ApplicationManager.getApplication().isUnitTestMode)

    val currentJob = currentJob ?: return
    @Suppress("RAW_RUN_BLOCKING", "RedundantSuppression")
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

  @TestOnly
  internal fun waitForAllExecutedInEdt(timeout: Duration) {
    require(ApplicationManager.getApplication().isUnitTestMode)

    if (currentJob == null) {
      return
    }

    @Suppress("RAW_RUN_BLOCKING", "RedundantSuppression")
    runBlocking {
      withTimeout(timeout) {
        while (currentJob != null) {
          EDT.dispatchAllInvocationEvents()
        }
      }
    }
  }

  fun request() {
    request(forceRun = false, delay = delay)
  }

  fun request(forceRun: Boolean) {
    request(forceRun = forceRun, delay = delay)
  }

  fun requestWithCustomDelay(delay: Int) {
    request(forceRun = false, delay = delay)
  }

  private fun request(forceRun: Boolean, delay: Int, cancelCurrent: Boolean = false) {
    if (isDisposed) {
      return
    }

    val effectiveDelay = if (forceRun) 0 else delay.toLong()
    synchronized(LOCK) {
      var prevCurrentJob = currentJob
      if (prevCurrentJob != null) {
        if (cancelCurrent) {
          prevCurrentJob.cancel()
        }
        else {
          return
        }
      }

      currentJob = taskCoroutineScope.launch {
        prevCurrentJob?.join()
        prevCurrentJob = null

        delay(effectiveDelay)

        withContext(taskContext) {
          //todo fix clients and remove NonCancellable
          try {
            if (!isDisposed) {
              Cancellation.withNonCancelableSection().use {
                task.run()
              }
            }
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
   * If [cancelCurrent] is true, it behaves as `debounce`.
   * If [cancelCurrent] is false, it behaves as `throttle` (that's how SingleAlarm works, if you call [request] several times).
   */
  internal fun scheduleTask(
    delay: Long,
    customModality: CoroutineContext?,
    cancelCurrent: Boolean = true,
    task: suspend () -> Unit,
  ) {
    if (isDisposed) {
      return
    }

    synchronized(LOCK) {
      var prevCurrentJob = currentJob
      if (prevCurrentJob != null) {
        if (cancelCurrent) {
          prevCurrentJob.cancel()
        }
        else {
          return
        }
      }

      currentJob = taskCoroutineScope.launch {
        prevCurrentJob?.join()
        prevCurrentJob = null

        delay(delay)
        withContext(if (customModality == null) taskContext else (taskContext + customModality)) {
          try {
            task()
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
    request(forceRun = forceRun, delay = delay, cancelCurrent = true)
  }

  // required, if we need to call it from the task
  @Internal
  fun scheduleCancelAndRequest() {
    taskCoroutineScope.launch {
      cancelAndRequest()
    }
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
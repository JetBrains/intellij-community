// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.util

import com.intellij.codeWithMe.ClientId.Companion.getCurrentValue
import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.concurrency.installThreadContext
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ChildContext
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.createChildContext
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector.Companion.installOn
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Async
import org.jetbrains.annotations.TestOnly
import java.awt.EventQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JComponent
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val LOG: Logger = logger<Alarm>()

/**
 * Use a [kotlinx.coroutines.flow.Flow] with [kotlinx.coroutines.flow.debounce] and [kotlinx.coroutines.flow.sample] instead.
 * Alarm is deprecated.
 *
 * Allows scheduling `Runnable` instances (requests) to be executed after a specific time interval on a specific thread.
 * Use [.addRequest] methods to schedule the requests.
 * Two requests scheduled with the same delay are executed sequentially, one after the other.
 * [.cancelAllRequests] and [.cancelRequest] allow canceling already scheduled requests.
 */
open class Alarm @Internal constructor(
  private val threadToUse: ThreadToUse,
  parentDisposable: Disposable?,
  // accessed in EDT only
  private val activationComponent: JComponent?,
) : Disposable {
  // it is a supervisor coroutine scope
  private val taskCoroutineScope: CoroutineScope

  // scheduled requests scheduled
  private val requests = SmartList<Request>() // guarded by LOCK

  // not yet scheduled requests (because, e.g., the corresponding component isn't active yet)
  // guarded by LOCK
  private val pendingRequests = SmartList<Request>()

  private val LOCK = Any()

  enum class ThreadToUse {
    /**
     * Run request in Swing event dispatch thread; this is the default.
     * NB: *Requests shouldn't take long to avoid UI freezes.*
     */
    SWING_THREAD,

    @Deprecated("Use {@link #POOLED_THREAD} instead ")
    SHARED_THREAD,

    /**
     * Run requests in one of application pooled threads.
     */
    POOLED_THREAD,
  }

  /**
   * Creates an alarm that works in EDT.
   */
  constructor(parentDisposable: Disposable) : this(threadToUse = ThreadToUse.SWING_THREAD, parentDisposable = parentDisposable)

  constructor(threadToUse: ThreadToUse) : this(threadToUse = threadToUse, parentDisposable = null, activationComponent = null)

  constructor() : this(threadToUse = ThreadToUse.SWING_THREAD, parentDisposable = null, activationComponent = null)

  constructor(threadToUse: ThreadToUse, parentDisposable: Disposable?)
    : this(threadToUse = threadToUse, parentDisposable = parentDisposable, activationComponent = null)

  /**
   * Creates alarm for EDT which executes its requests only when the {@param activationComponent} is shown on screen
   */
  constructor(activationComponent: JComponent, parent: Disposable)
    : this(threadToUse = ThreadToUse.SWING_THREAD, parentDisposable = parent, activationComponent = activationComponent) {
    installOn(activationComponent, object : Activatable {
      override fun showNotify() {
        flushPending()
      }
    })
  }

  /**
   * Creates an alarm that works in EDT.
   */
  init {
    @Suppress("DEPRECATION")
    if (threadToUse == ThreadToUse.SHARED_THREAD) {
      PluginException.reportDeprecatedUsage("Alarm.ThreadToUse#SHARED_THREAD", "Please use `POOLED_THREAD` instead")
    }

    taskCoroutineScope = createCoroutineScope(inEdt = threadToUse == ThreadToUse.SWING_THREAD)

    if (parentDisposable == null) {
      if (threadToUse != ThreadToUse.SWING_THREAD) {
        LOG.error(IllegalArgumentException("You must provide parent Disposable for non-swing thread Alarm"))
      }
    }
    else {
      @Suppress("LeakingThis")
      Disposer.register(parentDisposable, this)
    }
  }

  override fun dispose() {
    if (taskCoroutineScope.isActive) {
      taskCoroutineScope.cancel()
      cancelAllRequests()
    }
  }

  fun addRequest(request: Runnable, delayMillis: Int, runWithActiveFrameOnly: Boolean) {
    if (runWithActiveFrameOnly && !ApplicationManager.getApplication().isActive) {
      val connection = ApplicationManager.getApplication().messageBus.connect(this)
      connection.subscribe(ApplicationActivationListener.TOPIC, object : ApplicationActivationListener {
        override fun applicationActivated(ideFrame: IdeFrame) {
          connection.disconnect()
          addRequest(request, delayMillis)
        }
      })
    }
    else {
      addRequest(request, delayMillis)
    }
  }

  private fun getModalityState(): ModalityState? {
    return if (threadToUse == ThreadToUse.SWING_THREAD) ApplicationManager.getApplication()?.defaultModalityState else null
  }

  fun addRequest(request: Runnable, delayMillis: Long) {
    doAddRequest(request = request, delayMillis = delayMillis, modalityState = getModalityState())
  }

  open fun addRequest(request: Runnable, delayMillis: Int) {
    doAddRequest(request = request, delayMillis = delayMillis.toLong(), modalityState = getModalityState())
  }

  fun addComponentRequest(request: Runnable, delayMillis: Int) {
    ThreadingAssertions.assertEventDispatchThread()
    doAddRequest(
      request = request,
      delayMillis = delayMillis.toLong(),
      modalityState = ModalityState.stateForComponent(activationComponent!!),
    )
  }

  fun addComponentRequest(request: Runnable, delayMillis: Long) {
    ThreadingAssertions.assertEventDispatchThread()
    checkNotNull(activationComponent)
    doAddRequest(
      request = request,
      delayMillis = delayMillis,
      modalityState = ModalityState.stateForComponent(activationComponent),
    )
  }

  fun addRequest(request: Runnable, delayMillis: Int, modalityState: ModalityState?) {
    LOG.assertTrue(threadToUse == ThreadToUse.SWING_THREAD)
    doAddRequest(request = request, delayMillis = delayMillis.toLong(), modalityState = modalityState)
  }

  fun addRequest(request: Runnable, delayMillis: Long, modalityState: ModalityState?) {
    LOG.assertTrue(threadToUse == ThreadToUse.SWING_THREAD)
    doAddRequest(request = request, delayMillis = delayMillis, modalityState = modalityState)
  }

  internal fun cancelAllAndAddRequest(request: Runnable, delayMillis: Int, modalityState: ModalityState?) {
    synchronized(LOCK) {
      cancelAllRequests(requests)
      cancelAllRequests(pendingRequests)
      doAddRequest(request = request, delayMillis = delayMillis.toLong(), modalityState = modalityState)
    }
  }

  internal fun doAddRequest(request: Runnable, delayMillis: Long, modalityState: ModalityState?) {
    val childContext = if (request !is ContextAwareRunnable && AppExecutorUtil.propagateContext()) createChildContext() else null
    val requestToSchedule = Request(
      task = request,
      modalityState = modalityState,
      delayMillis = delayMillis,
      childContext = childContext,
    )
    synchronized(LOCK) {
      LOG.assertTrue(!isDisposed, "Already disposed")

      if (activationComponent == null || isActivationComponentShowing) {
        add(requestToSchedule)
      }
      else if (!pendingRequests.contains(requestToSchedule)) {
        pendingRequests.add(requestToSchedule)
      }
    }
  }

  @get:RequiresEdt
  private val isActivationComponentShowing: Boolean
    get() = activationComponent!!.isShowing

  // must be called under LOCK
  private fun add(request: Request) {
    requests.add(request)
    request.schedule(owner = this)
  }

  private fun flushPending() {
    synchronized(LOCK) {
      for (request in pendingRequests) {
        add(request)
      }
      pendingRequests.clear()
    }
  }

  fun cancelRequest(request: Runnable): Boolean {
    synchronized(LOCK) {
      cancelAndRemoveRequestFrom(request, requests)
      cancelAndRemoveRequestFrom(request, pendingRequests)
    }
    return true
  }

  private fun cancelAndRemoveRequestFrom(request: Runnable, list: MutableList<Request>) {
    for ((i, r) in list.asReversed().withIndex()) {
      if (r.task === request) {
        r.cancel()
        list.removeAt(i)
        break
      }
    }
  }

  // returns number of requests canceled
  open fun cancelAllRequests(): Int {
    return synchronized(LOCK) {
      cancelAllRequests(requests) + cancelAllRequests(pendingRequests)
    }
  }

  private fun cancelAllRequests(list: MutableList<Request>): Int {
    val count = list.size
    for (request in list) {
      request.cancel()
    }
    list.clear()
    return count
  }

  @TestOnly
  fun drainRequestsInTest() {
    assert(ApplicationManager.getApplication().isUnitTestMode)
    val result = ArrayList<Runnable>()
    synchronized(LOCK) {
      for (request in requests) {
        request.cancel()?.let {
          result.add(it)
        }
      }
      requests.clear()
    }
    for (task in result) {
      task.run()
    }
  }

  /**
   * wait for all requests to start execution (i.e., their delay elapses and their run() method, well, runs)
   * and then wait for the execution to finish.
   */
  @TestOnly
  @Throws(TimeoutException::class)
  fun waitForAllExecuted(timeout: Long, timeUnit: TimeUnit) {
    assert(ApplicationManager.getApplication().isUnitTestMode)

    val jobs = taskCoroutineScope.coroutineContext.job.children.toList()
    if (jobs.isEmpty()) {
      return
    }

    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      try {
        withTimeout(timeUnit.toMillis(timeout)) {
          jobs.joinAll()
        }
      }
      catch (e: TimeoutCancellationException) {
        // compatibility - throw TimeoutException as before
        throw TimeoutException(e.message)
      }
    }
  }

  val activeRequestCount: Int
    get() = synchronized(LOCK) { requests.size }

  val isEmpty: Boolean
    get() = synchronized(LOCK) { requests.isEmpty() }

  val isDisposed: Boolean
    get() = !taskCoroutineScope.isActive

  private class Request @Async.Schedule constructor(
    @JvmField var task: Runnable?,
    private val modalityState: ModalityState?,
    private val delayMillis: Long,
    private val childContext: ChildContext?,
  ) {
    @JvmField
    var job: Job? = null // guarded by LOCK
    private val clientId = getCurrentValue()

    @Async.Execute
    private fun runSafely(task: Runnable) {
      withClientId(clientId).use { _ ->
        if (childContext == null) {
          doRunSafely(task)
        }
        else {
          childContext.runAsCoroutine(completeOnFinish = true) {
            installThreadContext(coroutineContext = childContext.context, replace = true).use { _ ->
              doRunSafely(task)
            }
          }
        }
      }
    }

    fun schedule(owner: Alarm) {
      assert(job == null)
      job = owner.taskCoroutineScope.launch(modalityState?.asContextElement() ?: EmptyCoroutineContext) {
        delay(delayMillis)
        val task = synchronized(owner.LOCK) {
          task?.also { task = null }
        } ?: return@launch

        blockingContext {
          runSafely(task)
        }
      }.also {
        it.invokeOnCompletion {
          synchronized(owner.LOCK) {
            owner.requests.remove(this@Request)
            task = null
            job = null
          }
        }
      }
    }

    /**
     * Must be called under `LOCK`.
     * Returns a task, if not yet executed.
     */
    fun cancel(): Runnable? {
      job?.let {
        it.cancel(null)
        job = null
      }

      childContext?.job?.cancel(null)
      return task?.let {
        task = null
        it
      }
    }

    override fun toString(): String = "${super.toString()} $task; delay=${delayMillis}ms"
  }
}

// todo next step - support passing coroutine scope
private fun createCoroutineScope(inEdt: Boolean): CoroutineScope {
  val app = ApplicationManager.getApplication()
  @Suppress("SSBasedInspection")
  if (inEdt) {
    // maybe not defined in tests
    val edtDispatcher = app?.serviceOrNull<CoroutineSupport>()?.edtDispatcher()
    if (edtDispatcher == null) {
      // cannot be as error - not clear what to do in case of `RangeTimeScrollBarTest`
      logger<Alarm>().warn("Do not use an alarm in an early executing code")
      return CoroutineScope(object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
          EventQueue.invokeLater(block)
        }

        override fun toString() = "Swing"
      } + SupervisorJob())
    }
    else {
      @Suppress("UsagesOfObsoleteApi")
      return (app as ComponentManagerEx).getCoroutineScope().childScope("Alarm", edtDispatcher)
    }
  }
  else {
    val dispatcher = Dispatchers.Default.limitedParallelism(1)
    if (app == null) {
      logger<Alarm>().error("Do not use an alarm in an early executing code")
      return CoroutineScope(SupervisorJob() + dispatcher)
    }
    else {
      @Suppress("UsagesOfObsoleteApi")
      return (app as ComponentManagerEx).getCoroutineScope().childScope("Alarm", dispatcher)
    }
  }
}

private fun doRunSafely(run: Runnable) {
  try {
    run.run()
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.error(e)
  }
}
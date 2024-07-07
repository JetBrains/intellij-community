// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util

import com.intellij.codeWithMe.ClientId.Companion.getCurrentValue
import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.concurrency.installThreadContext
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.util.concurrency.*
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector.Companion.installOn
import org.jetbrains.annotations.Async
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.*
import javax.swing.JComponent
import kotlin.concurrent.Volatile

private val LOG: Logger = logger<Alarm>()

/**
 * Allows scheduling `Runnable` instances (requests) to be executed after a specific time interval on a specific thread.
 * Use [.addRequest] methods to schedule the requests.
 * Two requests scheduled with the same delay are executed sequentially, one after the other.
 * [.cancelAllRequests] and [.cancelRequest] allow canceling already scheduled requests.
 */
open class Alarm @JvmOverloads constructor(
  private val threadToUse: ThreadToUse = ThreadToUse.SWING_THREAD,
  parentDisposable: Disposable? = null,
) : Disposable {
  @Volatile
  var isDisposed: Boolean = false
    private set

  // requests scheduled to myExecutorService
  private val requests = SmartList<Request>() // guarded by LOCK

  // requests not yet scheduled to myExecutorService (because, e.g., the corresponding component isn't active yet)
  // guarded by LOCK
  private val pendingRequests = SmartList<Request>()

  private val executorService: ScheduledExecutorService

  private val LOCK = Any()

  // accessed in EDT only
  private var activationComponent: JComponent? = null

  override fun dispose() {
    if (!isDisposed) {
      isDisposed = true
      cancelAllRequests()

      if (executorService !== EdtExecutorService.getScheduledExecutorInstance()) {
        executorService.shutdownNow()
      }
    }
  }

  private fun checkDisposed() {
    LOG.assertTrue(!isDisposed, "Already disposed")
  }

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

  /**
   * Creates alarm for EDT which executes its requests only when the {@param activationComponent} is shown on screen
   */
  constructor(activationComponent: JComponent, parent: Disposable) : this(ThreadToUse.SWING_THREAD, parent) {
    this.activationComponent = activationComponent
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

    executorService = if (threadToUse == ThreadToUse.SWING_THREAD) {
      // pass straight to EDT
      EdtExecutorService.getScheduledExecutorInstance()
    }
    else {
      // or pass to app pooled thread.
      // have to restrict the number of running tasks because otherwise the (implicit) contract
      // "addRequests with the same delay are executed in order" will be broken
      AppExecutorUtil.createBoundedScheduledExecutorService("Alarm Pool", 1)
    }

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

  private val modalityState: ModalityState?
    get() = if (threadToUse == ThreadToUse.SWING_THREAD) ApplicationManager.getApplication()?.defaultModalityState else null

  fun addRequest(request: Runnable, delayMillis: Long) {
    doAddRequest(request = request, delayMillis = delayMillis, modalityState = modalityState)
  }

  open fun addRequest(request: Runnable, delayMillis: Int) {
    doAddRequest(request = request, delayMillis = delayMillis.toLong(), modalityState = modalityState)
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
      modalityState = ModalityState.stateForComponent(activationComponent!!),
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
      cancelAllRequests()
      doAddRequest(request = request, delayMillis = delayMillis.toLong(), modalityState = modalityState)
    }
  }

  internal fun doAddRequest(request: Runnable, delayMillis: Long, modalityState: ModalityState?) {
    val childContext = if (request !is ContextAwareRunnable && AppExecutorUtil.propagateContext()) createChildContext() else null
    val requestToSchedule = Request(
      owner = this,
      task = request,
      modalityState = modalityState,
      delayMillis = delayMillis,
      childContext = childContext,
    )
    synchronized(LOCK) {
      checkDisposed()
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
  private fun add(requestToSchedule: Request) {
    requestToSchedule.schedule()
    requests.add(requestToSchedule)
  }

  private fun flushPending() {
    synchronized(LOCK) {
      for (each in pendingRequests) {
        add(each)
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

  private fun cancelAllRequests(list: MutableList<out Request>): Int {
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
  @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
  fun waitForAllExecuted(timeout: Long, unit: TimeUnit) {
    assert(ApplicationManager.getApplication().isUnitTestMode)

    var futures: List<Future<*>>
    synchronized(LOCK) {
      futures = requests.mapNotNull { it.future }
    }

    val deadline = System.nanoTime() + unit.toNanos(timeout)
    for (future in futures) {
      val toWait = deadline - System.nanoTime()
      if (toWait < 0) {
        throw TimeoutException()
      }

      try {
        future.get(toWait, TimeUnit.NANOSECONDS)
      }
      catch (ignored: CancellationException) {
      }
    }
  }

  val activeRequestCount: Int
    get() {
      return synchronized(LOCK) {
        requests.size
      }
    }

  val isEmpty: Boolean
    get() {
      return synchronized(LOCK) {
        requests.isEmpty()
      }
    }

  private class Request @Async.Schedule constructor(
    private val owner: Alarm,
    task: Runnable,
    modalityState: ModalityState?,
    delayMillis: Long,
    childContext: ChildContext?,
  ) : Runnable {
    @JvmField
    var task: Runnable? = null // guarded by LOCK

    private var modalityState: ModalityState? = null
    @JvmField
    var future: Future<*>? = null // guarded by LOCK
    private var delayMillis: Long = 0
    private var clientId: String? = null
    private var childContext: ChildContext? = null

    init {
      synchronized(owner.LOCK) {
        this.task = task
        this.childContext = childContext
        this.modalityState = modalityState
        this.delayMillis = delayMillis
        clientId = getCurrentValue()
      }
    }

    override fun run() {
      try {
        if (owner.isDisposed) {
          return
        }

        val task = synchronized(owner.LOCK) {
          task?.also { task = null }
        }
        if (task != null) {
          runSafely(task)
        }
      }
      catch (ignored: CancellationException) {
      }
    }

    @Async.Execute
    fun runSafely(task: Runnable?) {
      try {
        if (task == null || owner.isDisposed) {
          return
        }

        val childContext = childContext
        withClientId(clientId!!).use { _ ->
          if (childContext == null) {
            QueueProcessor.runSafely(task)
          }
          else {
            QueueProcessor.runSafely {
              childContext.runAsCoroutine(
                Runnable {
                  installThreadContext(childContext.context, true).use { _ ->
                    task.run()
                  }
                })
            }
          }
        }
      }
      finally {
        // remove from the list after execution to be able for {@link #waitForAllExecuted(long, TimeUnit)} to wait for completion
        synchronized(owner.LOCK) {
          owner.requests.remove(this)
          future = null
        }
      }
    }

    // must be called under LOCK
    fun schedule() {
      val modalityState = modalityState
      future = if (modalityState == null) {
        owner.executorService.schedule(contextAwareCallable(this), delayMillis, TimeUnit.MILLISECONDS)
      }
      else {
        EdtScheduledExecutorService.getInstance().schedule(
          ContextAwareRunnable { this.run() }, modalityState, delayMillis, TimeUnit.MILLISECONDS)
      }
    }

    /**
     * Must be called under `LOCK`.
     * Returns a task, if not yet executed.
     */
    fun cancel(): Runnable? {
      val future = future
      childContext?.job?.cancel(null)
      if (future != null) {
        future.cancel(false)
        this.future = null
      }
      val task = task
      this.task = null
      return task
    }

    override fun toString(): String {
      var task: Runnable?
      synchronized(owner.LOCK) {
        task = this.task
      }
      return super.toString() + (if (task == null) "" else ": $task") + "; delay=" + delayMillis + "ms"
    }
  }

  @Deprecated("use {@link #Alarm(JComponent, Disposable)} instead ")
  @RequiresEdt
  fun setActivationComponent(component: JComponent): Alarm {
    PluginException.reportDeprecatedUsage("Alarm#setActivationComponent", "Please use `#Alarm(JComponent, Disposable)` instead")
    activationComponent = component
    installOn(component, object : Activatable {
      override fun showNotify() {
        flushPending()
      }
    })
    return this
  }
}

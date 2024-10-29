// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.util.ui.update

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.ide.UiActivity
import com.intellij.ide.UiActivity.AsyncBgOperation
import com.intellij.ide.UiActivityMonitor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.Alarm.ThreadToUse
import com.intellij.util.SingleAlarm
import com.intellij.util.SystemProperties
import com.intellij.util.ui.EDT
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.update.UiNotifyConnector.Companion.installOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.JComponent
import kotlin.concurrent.Volatile
import kotlin.coroutines.coroutineContext

private val priorityComparator = Comparator.comparingInt<Update> { it.priority }

/**
 * Use this class to postpone task execution and optionally merge identical tasks. This is needed, e.g., to reflect in UI status of some
 * background activity: it doesn't make sense and would be inefficient to update UI 1000 times per second, so it's better to postpone 'update UI'
 * task execution for e.g., 500ms and if new updates are added during this period, they can be simply ignored.
 *
 * Create an instance of this class and use [.queue] method to add new tasks.
 *
 * Sometimes [MergingUpdateQueue] can be used for control flow operations. **This kind of usage is discouraged**, in favor of
 * [kotlinx.coroutines.flow.Flow] and [kotlinx.coroutines.flow.FlowKt.debounce].
 * If you are still using [MergingUpdateQueue], you can consider queuing via [MergingQueueUtil.queueTracked]
 * in order to notify the platform about scheduled updates.

 * @param name                   name of this queue, used only for debugging purposes
 * @param mergingTimeSpan        time (in milliseconds) for which execution of tasks will be postponed
 * @param isActive               if `true` the queue will execute tasks otherwise it'll just collect them and execute only after [.activate] is called
 * @param modalityStateComponent makes sense only if `thread` is [SWING_THREAD][Alarm.ThreadToUse.SWING_THREAD], in that
 * case the tasks will be processed in [ModalityState] corresponding the given component
 * @param parent                 if not `null` the queue will be disposed when the given parent is disposed
 * @param activationComponent    if not `null` the tasks will be processing only when the given component is showing
 * @param thread                 specifies on which thread the tasks are executed
 */
@Obsolete
open class MergingUpdateQueue @JvmOverloads constructor(
  private val name: @NonNls String,
  private var mergingTimeSpan: Int,
  isActive: Boolean,
  private var modalityStateComponent: JComponent?,
  parent: Disposable?,
  activationComponent: JComponent?,
  thread: ThreadToUse,
  coroutineScope: CoroutineScope? = null,
) : Disposable, Activatable {
  @Volatile
  var isSuspended: Boolean = false
    private set

  private val flushTask = object : Runnable {
    override fun run() {
      if (!isSuspended) {
        flush()
      }
    }

    override fun toString() = "MergingUpdateQueueFlushTask(name=$name)"
  }

  @Volatile
  var isActive: Boolean = false
    private set

  private val scheduledUpdates = ConcurrentCollectionFactory.createConcurrentIntObjectMap<MutableMap<Update, Update>>()
  private val waiterForMerge: SingleAlarm

  @Volatile
  var isFlushing: Boolean = false
    private set

  private val executeInDispatchThread: Boolean = thread == ThreadToUse.SWING_THREAD

  /**
   * if `true` the tasks won't be postponed but executed immediately instead
   */
  var isPassThrough: Boolean = false

  @Volatile
  private var isDisposed: Boolean = false
  private var restartOnAdd: Boolean = false

  private var trackUiActivity: Boolean = false
  private var computedUiActivity: UiActivity? = null

  @JvmOverloads
  constructor(
    name: @NonNls String,
    mergingTimeSpan: Int,
    isActive: Boolean,
    modalityStateComponent: JComponent?,
    parent: Disposable? = null,
    activationComponent: JComponent? = null,
    executeInDispatchThread: Boolean = true,
  ) : this(
    name = name,
    mergingTimeSpan = mergingTimeSpan,
    isActive = isActive,
    modalityStateComponent = modalityStateComponent,
    parent = parent,
    activationComponent = activationComponent,
    thread = if (executeInDispatchThread) ThreadToUse.SWING_THREAD else ThreadToUse.POOLED_THREAD,
    coroutineScope = null,
  )

  init {
    if (parent != null) {
      @Suppress("LeakingThis")
      Disposer.register(parent, this)
    }

    waiterForMerge = if (coroutineScope == null) {
      @Suppress("LeakingThis")
      SingleAlarm(
        task = getFlushTask(),
        delay = mergingTimeSpan,
        // due to historical reasons, we don't pass parent disposable if EDT
        parentDisposable = if (thread == ThreadToUse.SWING_THREAD) null else this,
        threadToUse = thread,
        modalityState = null,
        coroutineScope = null,
      )
    }
    else {
      @Suppress("LeakingThis")
      SingleAlarm(
        task = getFlushTask(),
        delay = mergingTimeSpan,
        parentDisposable = null,
        threadToUse = thread,
        coroutineScope = coroutineScope,
      )
    }

    if (isActive) {
      showNotify()
    }

    if (activationComponent != null) {
      val connector = installOn(activationComponent, this)
      @Suppress("LeakingThis")
      Disposer.register(this, connector)
    }

    @Suppress("LeakingThis")
    queues?.add(this)
  }

  companion object {
    @JvmField
    val ANY_COMPONENT: JComponent = object : JComponent() {}

    @Internal
    @JvmOverloads
    fun edtMergingUpdateQueue(
      name: String,
      mergingTimeSpan: Int,
      coroutineScope: CoroutineScope,
      modalityStateComponent: JComponent? = null,
    ): MergingUpdateQueue {
      return MergingUpdateQueue(
        name = name,
        mergingTimeSpan = mergingTimeSpan,
        isActive = true,
        modalityStateComponent = modalityStateComponent,
        parent = null,
        activationComponent = null,
        thread = ThreadToUse.SWING_THREAD,
        coroutineScope = coroutineScope,
      )
    }

    @Internal
    fun mergingUpdateQueue(
      name: String,
      mergingTimeSpan: Int,
      coroutineScope: CoroutineScope,
    ): MergingUpdateQueue {
      return MergingUpdateQueue(
        name = name,
        mergingTimeSpan = mergingTimeSpan,
        isActive = true,
        modalityStateComponent = null,
        parent = null,
        activationComponent = null,
        thread = ThreadToUse.POOLED_THREAD,
        coroutineScope = coroutineScope,
      )
    }

    private val queues: MutableSet<MergingUpdateQueue>? = if (SystemProperties.getBooleanProperty("intellij.MergingUpdateQueue.enable.global.flusher", false)) {
      ConcurrentCollectionFactory.createConcurrentSet()
    }
    else {
      null
    }

    @Internal
    fun flushAllQueues() {
      if (queues != null) {
        for (queue in queues) {
          queue.flush()
        }
      }
    }
  }

  fun setMergingTimeSpan(timeSpan: Int) {
    mergingTimeSpan = timeSpan
    if (isActive) {
      restartTimer()
    }
  }

  fun cancelAllUpdates() {
    synchronized(scheduledUpdates) {
      for (each in getAllScheduledUpdates()) {
        try {
          each.setRejected()
        }
        catch (ignored: CancellationException) {
        }
      }
      scheduledUpdates.clear()
      finishActivity()
    }
  }

  private fun getAllScheduledUpdates(): List<Update> = scheduledUpdates.values().flatMap { it.keys }

  /**
   * Switches on the PassThrough mode if this method is called in unit test (i.e., when [Application.isUnitTestMode] is true).
   * It is needed to support some old tests, which expect such behaviour.
   * @return this instance for the sequential creation (the Builder pattern)
   */
  @Internal
  @Deprecated("""use {@link #waitForAllExecuted(long, TimeUnit)} instead in tests  """)
  fun usePassThroughInUnitTestMode(): MergingUpdateQueue {
    val app = ApplicationManager.getApplication()
    if (app == null || app.isUnitTestMode) {
      isPassThrough = true
    }
    return this
  }

  fun activate() {
    showNotify()
  }

  fun deactivate() {
    hideNotify()
  }

  open fun suspend() {
    isSuspended = true
  }

  open fun resume() {
    isSuspended = false
    restartTimer()
  }

  override fun hideNotify() {
    if (!isActive) {
      return
    }

    isActive = false

    finishActivity()

    clearWaiter()
  }

  final override fun showNotify() {
    if (isActive) {
      return
    }

    isActive = true
    restartTimer()
    flush()
  }

  fun restartTimer() {
    restart(mergingTimeSpan)
  }

  @Internal
  protected open fun getFlushTask(): Runnable = flushTask

  private fun restart(mergingTimeSpan: Int) {
    if (!isActive) {
      return
    }

    waiterForMerge.scheduleTask(
      delay = mergingTimeSpan.toLong(),
      customModality = (if (!executeInDispatchThread) {
        null
      }
      else if (modalityStateComponent === ANY_COMPONENT) {
        ModalityState.any()
      }
      else {
        getModalityState()
      })?.asContextElement(),
    ) {
      if (isSuspended || isEmpty || isFlushing || !isModalityStateCorrect()) {
        return@scheduleTask
      }

      isFlushing = true
      try {
        val all = flushScheduledUpdates() ?: return@scheduleTask

        coroutineContext.ensureActive()

        for (update in all) {
          update.setProcessed()
        }
        all.sortWith(priorityComparator)
        executeUpdates(all)
      }
      finally {
        isFlushing = false
        if (isEmpty) {
          finishActivity()
        }
      }
    }
  }

  @Internal
  protected open suspend fun executeUpdates(updates: List<Update>) {
    for (update in updates) {
      coroutineContext.ensureActive()

      if (isExpired(update)) {
        update.setRejected()
      }
      else {
        update.execute()
      }
    }
  }

  /**
   * Executes all scheduled requests in the current thread.
   * Please note that requests that started execution before this method call are not waited for completion.
   */
  fun flush() {
    if (isEmpty || isFlushing || !isModalityStateCorrect()) {
      return
    }

    if (!executeInDispatchThread || EDT.isCurrentThreadEdt()) {
      doFlush()
    }
    else {
      try {
        EdtInvocationManager.getInstance().invokeAndWait { doFlush() }
      }
      catch (e: Exception) {
        thisLogger().error(e)
      }
    }
  }

  private fun doFlush() {
    isFlushing = true
    try {
      val all = flushScheduledUpdates() ?: return
      for (update in all) {
        update.setProcessed()
      }
      all.sortWith(priorityComparator)
      execute(all)
    }
    finally {
      isFlushing = false
      if (isEmpty) {
        finishActivity()
      }
    }
  }

  private fun flushScheduledUpdates(): MutableList<Update>? {
    synchronized(scheduledUpdates) {
      if (scheduledUpdates.isEmpty) {
        return null
      }

      val result = ArrayList<Update>()
      scheduledUpdates.values().flatMapTo(result) { it.keys }
      scheduledUpdates.clear()
      return result
    }
  }

  @Suppress("unused")
  fun setModalityStateComponent(modalityStateComponent: JComponent?) {
    this.modalityStateComponent = modalityStateComponent
  }

  @VisibleForTesting
  @Internal
  protected open fun isModalityStateCorrect(): Boolean {
    if (!executeInDispatchThread || modalityStateComponent === ANY_COMPONENT) {
      return true
    }

    val current = ModalityState.current()
    val modalityState = getModalityState()
    return !current.dominates(modalityState)
  }

  @Internal
  protected open fun execute(updates: List<Update>) {
    for (update in updates) {
      if (isExpired(update)) {
        update.setRejected()
        continue
      }

      if (update.executeInWriteAction) {
        @Suppress("ForbiddenInSuspectContextMethod", "RedundantSuppression")
        ApplicationManager.getApplication().runWriteAction { execute(update) }
      }
      else {
        execute(update)
      }
    }
  }

  private fun execute(update: Update) {
    if (isDisposed) {
      update.setRejected()
    }
    else {
      update.run()
    }
  }

  /**
   * Adds a task to be executed.
   */
  open fun queue(update: Update) {
    if (isDisposed) {
      return
    }

    if (trackUiActivity) {
      startActivity()
    }

    if (isPassThrough) {
      update.run()
      finishActivity()
      return
    }

    val active = isActive
    synchronized(scheduledUpdates) {
      try {
        if (eatThisOrOthers(update)) {
          return
        }

        if (active && scheduledUpdates.isEmpty) {
          restartTimer()
        }
        put(update)

        if (restartOnAdd) {
          restartTimer()
        }
      }
      finally {
        if (isEmpty) {
          finishActivity()
        }
      }
    }
  }

  private fun eatThisOrOthers(update: Update): Boolean {
    val updates = scheduledUpdates.get(update.priority)
    if (updates != null && updates.containsKey(update)) {
      return false
    }

    for (eachInQueue in getAllScheduledUpdates()) {
      if (eachInQueue.canEat(update)) {
        update.setRejected()
        return true
      }

      if (update.canEat(eachInQueue)) {
        scheduledUpdates.get(eachInQueue.priority).remove(eachInQueue)
        eachInQueue.setRejected()
      }
    }
    return false
  }

  fun run(update: Update) {
    execute(listOf(update))
  }

  private fun put(update: Update) {
    val updates: MutableMap<Update, Update> = scheduledUpdates.cacheOrGet(update.priority, LinkedHashMap())
    val existing = updates.remove(update)
    if (existing != null && existing !== update) {
      existing.setProcessed()
      existing.setRejected()
    }
    updates.put(update, update)
  }

  override fun dispose() {
    try {
      isDisposed = true
      isActive = false
      finishActivity()
      Disposer.dispose(waiterForMerge)
      cancelAllUpdates()
    }
    finally {
      queues?.remove(this)
    }
  }

  private fun clearWaiter() {
    waiterForMerge.cancel()
  }

  override fun toString(): String = "$name active=$isActive scheduled=${scheduledUpdates.values().asSequence().flatMap { it.keys }.count()}"

  fun getModalityState(): ModalityState {
    return when (val modalityStateComponent = modalityStateComponent) {
      null -> ModalityState.nonModal()
      else -> ModalityState.stateForComponent(modalityStateComponent)
    }
  }

  fun setRestartTimerOnAdd(restart: Boolean): MergingUpdateQueue {
    restartOnAdd = restart
    return this
  }

  val isEmpty: Boolean
    get() = scheduledUpdates.isEmpty

  fun sendFlush() {
    restart(0)
  }

  private fun startActivity() {
    if (trackUiActivity) {
      UiActivityMonitor.getInstance().addActivity(getActivityId(), getModalityState())
    }
  }

  private fun finishActivity() {
    if (trackUiActivity) {
      UiActivityMonitor.getInstance().removeActivity(getActivityId())
    }
  }

  private fun getActivityId(): UiActivity {
    return computedUiActivity ?: AsyncBgOperation("UpdateQueue:$name${hashCode()}").also {
      computedUiActivity = it
    }
  }

  @TestOnly
  @Throws(TimeoutException::class)
  fun waitForAllExecuted(timeout: Long, unit: TimeUnit) {
    val deadline = System.nanoTime() + unit.toNanos(timeout)
    if (!waiterForMerge.isEmpty) {
      // to not wait for mergingTimeSpan ms in tests
      restart(0)
    }

    waiterForMerge.waitForAllExecuted(timeout, unit)
    while (!scheduledUpdates.isEmpty) {
      val toWait = deadline - System.nanoTime()
      if (toWait < 0) {
        throw TimeoutException()
      }

      // to not wait for mergingTimeSpan ms in tests
      restart(0)
      waiterForMerge.waitForAllExecuted(toWait, TimeUnit.NANOSECONDS)
    }
  }
}

private fun isExpired(update: Update): Boolean = update.isDisposed || update.isExpired
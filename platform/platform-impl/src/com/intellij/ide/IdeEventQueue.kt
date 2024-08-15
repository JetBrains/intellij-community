// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.ide

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.ClientId.Companion.current
import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.concurrency.resetThreadContext
import com.intellij.diagnostic.EventWatcher
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.ide.MnemonicUsageCollector.logMnemonicUsed
import com.intellij.ide.actions.MaximizeActiveDialogAction
import com.intellij.ide.dnd.DnDManager
import com.intellij.ide.dnd.DnDManagerImpl
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.AnyThreadWriteThreadingSupport
import com.intellij.openapi.application.impl.InvocationUtil
import com.intellij.openapi.application.impl.RwLockHolder
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.keymap.impl.IdeMouseEventDispatcher
import com.intellij.openapi.keymap.impl.KeyState
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.FocusManagerImpl
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.platform.ide.bootstrap.StartupErrorReporter
import com.intellij.platform.ide.bootstrap.isImplicitReadOnEDTDisabled
import com.intellij.ui.ComponentUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.EDT
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import sun.awt.AppContext
import sun.awt.PeerEvent
import sun.awt.SunToolkit
import java.awt.*
import java.awt.EventQueue.invokeLater
import java.awt.EventQueue.isDispatchThread
import java.awt.event.*
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import javax.swing.*
import javax.swing.plaf.basic.ComboPopup

@Suppress("FunctionName")
class IdeEventQueue private constructor() : EventQueue() {
  /**
   * Adding/Removing of "idle" listeners should be thread safe.
   */
  private val lock = Any()
  private val activityListeners = ContainerUtil.createLockFreeCopyOnWriteList<Runnable>()

  @Internal
  val threadingSupport: ThreadingSupport = if (isNewLockEnabled) AnyThreadWriteThreadingSupport else RwLockHolder
  val keyEventDispatcher: IdeKeyEventDispatcher = IdeKeyEventDispatcher(this)
  val mouseEventDispatcher: IdeMouseEventDispatcher = IdeMouseEventDispatcher()
  val popupManager: IdePopupManager = IdePopupManager()

  @get:Internal
  var popupTriggerTime: Long = -1
    private set

  /**
   * Counter of processed events. It is used to assert that data context lives only inside a single Swing event.
   */
  var eventCount: Int = 0

  @VisibleForTesting
  @JvmField
  val keyboardEventPosted: AtomicInteger = AtomicInteger()

  @VisibleForTesting
  @JvmField
  val keyboardEventDispatched: AtomicInteger = AtomicInteger()

  private val eventsPosted = AtomicLong()
  private val eventsReturned = AtomicLong()

  private var isInInputEvent = false
  var trueCurrentEvent: AWTEvent = InvocationEvent(this, EmptyRunnable.getInstance())
    private set

  private var currentSequencedEvent: AWTEvent? = null

  @Volatile
  private var lastActiveTime = System.nanoTime()
  private var lastEventTime = System.currentTimeMillis()
  private val dispatchers = ContainerUtil.createLockFreeCopyOnWriteList<EventDispatcher>()
  private val postProcessors = ContainerUtil.createLockFreeCopyOnWriteList<EventDispatcher>()
  private val preProcessors = ContainerUtil.createLockFreeCopyOnWriteList<EventDispatcher>()
  private val ready = HashSet<Runnable>()
  private val hoverService = HoverService()
  private var keyboardBusy = false
  private var winMetaPressed = false
  private var inputMethodLock = 0
  private val postEventListeners = ContainerUtil.createLockFreeCopyOnWriteList<PostEventHook>()
  private val runnablesWaitingFocusChange = HashMap<AWTEvent, MutableList<Runnable>>()
  private val focusEventList = ConcurrentLinkedQueue<AWTEvent>()

  @Internal
  @JvmField
  val isDispatchingOnMainThread: Boolean = Thread.currentThread().name.contains("AppKit").also {
    if (it) System.setProperty("jb.dispatching.on.main.thread", "true")
  }

  private var idleTracker: () -> Unit = {}
  private var testMode: Boolean? = null

  init {
    assert(isDispatchThread()) { Thread.currentThread() }
    val systemEventQueue = Toolkit.getDefaultToolkit().systemEventQueue
    assert(systemEventQueue !is IdeEventQueue) { systemEventQueue }
    systemEventQueue.push(this)
    threadingSupport.postInit(Thread.currentThread())
    EDT.updateEdt()
    replaceDefaultKeyboardFocusManager()
    addDispatcher(WindowsAltSuppressor(), null)
    if (SystemInfoRt.isWindows && java.lang.Boolean.parseBoolean(System.getProperty("keymap.windows.up.to.maximize.dialogs", "true"))) {
      // 'Windows+Up' shortcut would maximize the active dialog under Win 7+
      addDispatcher(WindowsUpMaximizer(), null)
    }
    addDispatcher(EditingCanceller(), null)
    //addDispatcher(new UIMouseTracker(), null);
    abracadabraDaberBoreh(this)
    if (java.lang.Boolean.parseBoolean(System.getProperty("skip.move.resize.events", "true"))) {
      postEventListeners.add { skipMoveResizeEvents(it) } // hot path, do not use method reference
    }
  }

  companion object {
    private val _instance by lazy { IdeEventQueue() }

    @JvmStatic
    fun getInstance(): IdeEventQueue = _instance

    @Internal
    @JvmStatic
    fun applicationClose() {
      appIsLoaded = false
      transactionGuard = null
      restoreDefaultKeyboardFocusManager()
    }
  }

  @RequiresEdt
  internal fun setIdleTracker(value: () -> Unit) {
    EDT.assertIsEdt()
    idleTracker = value
  }

  /**
   * Executes given `runnable` after all focus activities are finished.
   *
   * @apiNote be careful with this method. It may run `runnable` synchronously in the context of the current thread, or may queue
   * runnable until the focus events queue is empty. In the latter case, runnable is going to be run while processing the last focus
   * event from the queue, without any context, e.g., outside the write-safe context. Consider using safer [IdeFocusManager.doWhenFocusSettlesDown]
   */
  fun executeWhenAllFocusEventsLeftTheQueue(runnable: Runnable) {
    ifFocusEventsInTheQueue(
      yes = { e ->
        var runnables = runnablesWaitingFocusChange.get(e)
        if (runnables == null) {
          runnables = mutableListOf()
          runnables.add(runnable)
          runnablesWaitingFocusChange.put(e, runnables)
        }
        else {
          Logs.FOCUS_AWARE_RUNNABLES_LOG.debug { "We have already had a runnable for the event: $e" }
          runnables.add(runnable)
        }
      },
      no = runnable,
    )
  }

  private fun runnablesWaitingForFocusChangeState(): String =
    focusEventList.joinToString(separator = ", ") { event -> "[${event.id}; ${event.source.javaClass.name}]" }

  private inline fun ifFocusEventsInTheQueue(yes: (AWTEvent) -> Unit, no: Runnable) {
    val lastFocusGainedEvent = focusEventList.lastOrNull { it.id == FocusEvent.FOCUS_GAINED }
    if (lastFocusGainedEvent == null) {
      Logs.FOCUS_AWARE_RUNNABLES_LOG.debug { "No focus gained event in the queue runnable is run on EDT if needed : ${no.javaClass.name}" }
      EdtInvocationManager.invokeLaterIfNeeded(no)
    }
    else {
      Logs.FOCUS_AWARE_RUNNABLES_LOG.debug {
        "Focus event list (trying to execute runnable): ${runnablesWaitingForFocusChangeState()}\n" +
        "runnable saved for : [${lastFocusGainedEvent.id}; ${lastFocusGainedEvent.source}] -> ${no.javaClass.name}"
      }
      yes(lastFocusGainedEvent)
    }
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use IdleTracker and coroutines")
  fun addIdleListener(runnable: Runnable, timeoutMillis: Int) {
    @Suppress("DEPRECATION")
    IdleTracker.getInstance().addIdleListener(runnable = runnable, timeoutMillis = timeoutMillis)
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Use IdleTracker and coroutines")
  fun removeIdleListener(runnable: Runnable) {
    @Suppress("DEPRECATION")
    IdleTracker.getInstance().removeIdleListener(runnable)
  }

  fun addActivityListener(runnable: Runnable, parentDisposable: Disposable) {
    activityListeners.add(runnable)
    Disposer.register(parentDisposable) { activityListeners.remove(runnable) }
  }

  fun addActivityListener(runnable: Runnable, coroutineScope: CoroutineScope) {
    activityListeners.add(runnable)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      activityListeners.remove(runnable)
    }
  }

  fun addDispatcher(dispatcher: EventDispatcher, parent: Disposable?) {
    addProcessor(dispatcher, parent, dispatchers)
  }

  fun addDispatcher(dispatcher: EventDispatcher, scope: CoroutineScope) {
    dispatchers.add(dispatcher)
    scope.coroutineContext.job.invokeOnCompletion {
      dispatchers.remove(dispatcher)
    }
  }

  fun removeDispatcher(dispatcher: EventDispatcher) {
    dispatchers.remove(dispatcher)
  }

  fun containsDispatcher(dispatcher: EventDispatcher): Boolean = dispatchers.contains(dispatcher)

  fun addPostprocessor(dispatcher: EventDispatcher, parent: Disposable?) {
    addProcessor(dispatcher, parent, postProcessors)
  }

  fun addPostprocessor(dispatcher: EventDispatcher, coroutineScope: CoroutineScope) {
    postProcessors.add(dispatcher)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      postProcessors.remove(dispatcher)
    }
  }

  fun removePostprocessor(dispatcher: EventDispatcher) {
    postProcessors.remove(dispatcher)
  }

  fun addPreprocessor(dispatcher: EventDispatcher, parent: Disposable?) {
    addProcessor(dispatcher, parent, preProcessors)
  }

  public override fun dispatchEvent(e: AWTEvent) {
    var event = e

    // DO NOT ADD ANYTHING BEFORE fixNestedSequenceEvent is called
    val startedAt = System.currentTimeMillis()
    val performanceWatcher = PerformanceWatcher.getInstanceIfCreated()
    val eventWatcher = if (LoadingState.COMPONENTS_LOADED.isOccurred) EventWatcher.getInstanceOrNull() else null
    try {
      performanceWatcher?.edtEventStarted()
      eventWatcher?.edtEventStarted(event, startedAt)
      fixNestedSequenceEvent(event)
      // Add code below if you need

      // Update EDT if it changes (might happen after Application disposal)
      EDT.updateEdt()
      if (event.id == WindowEvent.WINDOW_ACTIVATED || event.id == WindowEvent.WINDOW_DEICONIFIED || event.id == WindowEvent.WINDOW_OPENED) {
        ActiveWindowsWatcher.addActiveWindow(event.source as Window)
      }

      if (isMetaKeyPressedOnLinux(event)) {
        return
      }

      if (event.source is TrayIcon) {
        dispatchTrayIconEvent(event)
        return
      }

      checkForTimeJump(startedAt)

      if (!appIsLoaded()) {
        try {
          super.dispatchEvent(event)
        }
        catch (t: Throwable) {
          processException(t)
        }
        return
      }

      hoverService.process(event)

      event = mapEvent(event)
      val metaEvent = mapMetaState(event)
      if (metaEvent != null && Registry.`is`("keymap.windows.as.meta", false)) {
        event = metaEvent
      }
      if (SystemInfoRt.isMac && event is InputEvent) {
        disableAltGrUnsupportedOnMac(event)
      }

      val wasInputEvent = isInInputEvent
      isInInputEvent = isInputEvent(event)
      val oldEvent = trueCurrentEvent
      trueCurrentEvent = event
      val finalEvent = event
      val runnable = InvocationUtil.extractRunnable(event)
      val runnableClass = runnable?.javaClass ?: Runnable::class.java
      val processEventRunnable = Runnable {
        withAttachedClientId(event).use {
          val progressManager = ProgressManager.getInstanceOrNull()
          try {
            runCustomProcessors(finalEvent, preProcessors)
            performActivity(finalEvent, isCoroutineWILEnabled && !threadingSupport.isInsideUnlockedWriteIntentLock()) {
              if (progressManager == null) {
                _dispatchEvent(finalEvent)
              }
              else {
                progressManager.computePrioritized(ThrowableComputable {
                  _dispatchEvent(finalEvent)
                  null
                })
              }
            }
          }
          catch (t: Throwable) {
            processException(t)
          }
          finally {
            isInInputEvent = wasInputEvent
            trueCurrentEvent = oldEvent
            if (currentSequencedEvent === finalEvent) {
              currentSequencedEvent = null
            }
            runCustomProcessors(finalEvent, postProcessors)
            if (finalEvent is KeyEvent) {
              maybeReady()
            }
            if (eventWatcher != null && runnable != null && !InvocationUtil.isFlushNow(runnable)) {
              eventWatcher.logTimeMillis(if (runnableClass == Runnable::class.java) finalEvent.toString() else runnableClass.name,
                                         startedAt,
                                         runnableClass)
            }
          }
          if (isFocusEvent(finalEvent)) {
            onFocusEvent(finalEvent)
          }
        }
      }

      if (runnableClass == InvocationUtil.REPAINT_PROCESSING_CLASS) {
        processEventRunnable.run()
        return
      }

      if (defaultEventWithWrite) {
        ApplicationManagerEx.getApplicationEx().runIntendedWriteActionOnCurrentThread(processEventRunnable)
      }
      else {
        processEventRunnable.run()
      }
    }
    finally {
      Thread.interrupted()
      processIdleActivityListeners(e)
      performanceWatcher?.edtEventFinished()
      eventWatcher?.edtEventFinished(event, System.currentTimeMillis())
    }
  }

  private fun runCustomProcessors(event: AWTEvent, processors: List<EventDispatcher>) {
    for (each in processors) {
      try {
        each.dispatch(event)
      }
      catch (t: Throwable) {
        processException(t)
      }
    }
  }

  // Fixes IDEA-218430: nested sequence events cause deadlock
  private fun fixNestedSequenceEvent(e: AWTEvent) {
    if (e.javaClass == SequencedEventNestedFieldHolder.SEQUENCED_EVENT_CLASS) {
      if (currentSequencedEvent != null) {
        val sequenceEventToDispose = currentSequencedEvent!!
        currentSequencedEvent = null // Set to null BEFORE dispose b/c `dispose` can dispatch events internally
        SequencedEventNestedFieldHolder.invokeDispose(sequenceEventToDispose)
      }
      currentSequencedEvent = e
    }
  }

  private fun onFocusEvent(event: AWTEvent) {
    Logs.FOCUS_AWARE_RUNNABLES_LOG.debug { "Focus event list (execute on focus event): " + runnablesWaitingForFocusChangeState() }
    val events = mutableListOf<AWTEvent>()
    @Suppress("UsePropertyAccessSyntax")
    while (!focusEventList.isEmpty()) {
      val f = focusEventList.poll()
      events.add(f)
      if (f == event) {
        break
      }
    }

    for (entry in events) {
      val runnables = runnablesWaitingFocusChange.remove(entry) ?: continue
      for (r in runnables) {
        if (r !is ExpirableRunnable || !r.isExpired) {
          try {
            r.run()
          }
          catch (e: Throwable) {
            processException(e)
          }
        }
      }
    }
  }

  // as we rely on system time monotonicity in many places, let's log anomalies at least
  private fun checkForTimeJump(now: Long) {
    if (lastEventTime > now + 1000) {
      Logs.LOG.warn("System clock's jumped back by ~${(lastEventTime - now) / 1000} sec")
    }
    lastEventTime = now
  }

  @Suppress("UsePropertyAccessSyntax")
  override fun getNextEvent(): AWTEvent {
    val event = if (isImplicitReadOnEDTDisabled) {
      super.getNextEvent()
    }
    else {
      val applicationEx = ApplicationManagerEx.getApplicationEx()
      if (applicationEx != null && appIsLoaded()) {
        applicationEx.runUnlockingIntendedWrite<AWTEvent, InterruptedException> { super.getNextEvent() }
      }
      else {
        super.getNextEvent()
      }
    }
    eventsReturned.incrementAndGet()
    if (isKeyboardEvent(event) && keyboardEventDispatched.incrementAndGet() > keyboardEventPosted.get()) {
      throw RuntimeException("$event; posted: $keyboardEventPosted; dispatched: $keyboardEventDispatched")
    }
    return event
  }

  private fun processException(exception: Throwable) {
    var t = exception
    if (isTestMode()) {
      throw t
    }

    if (t is ControlFlowException && java.lang.Boolean.getBoolean("report.control.flow.exceptions.in.edt")) {
      // 'bare' ControlFlowException-s are not reported
      t = RuntimeException(t)
    }
    StartupErrorReporter.processException(t)
  }

  /**
   * Here we try to use the 'Windows' key like a modifier, so we patch events with modifier 'Meta'
   * when the 'Windows' key was pressed and is still not released.
   *
   * Note: As a side effect, this method tracks a special flag for the 'Windows' key state that is valuable in itself.
   */
  private fun mapMetaState(e: AWTEvent): AWTEvent? {
    if (winMetaPressed) {
      val app = ApplicationManager.getApplication()
      var weAreNotActive = app == null || !app.isActive
      weAreNotActive = weAreNotActive or (e is FocusEvent && e.oppositeComponent == null)
      if (weAreNotActive) {
        winMetaPressed = false
        return null
      }
    }

    if (e is KeyEvent) {
      if (e.keyCode == KeyEvent.VK_WINDOWS) {
        if (e.id == KeyEvent.KEY_PRESSED) winMetaPressed = true
        if (e.id == KeyEvent.KEY_RELEASED) winMetaPressed = false
        return null
      }
      if (winMetaPressed) {
        @Suppress("DEPRECATION")
        return KeyEvent(e.component, e.id, e.getWhen(), UIUtil.getAllModifiers(e) or Event.META_MASK, e.keyCode, e.keyChar, e.keyLocation)
      }
    }

    if (winMetaPressed && e is MouseEvent && e.button != 0) {
      @Suppress("DEPRECATION")
      return MouseEvent(e.component, e.id, e.getWhen(), UIUtil.getAllModifiers(e) or Event.META_MASK, e.x, e.y, e.clickCount, e.isPopupTrigger, e.button)
    }

    return null
  }

  private fun _dispatchEvent(e: AWTEvent) {
    if (e.id == MouseEvent.MOUSE_DRAGGED && appIsLoaded()) {
      (DnDManager.getInstance() as? DnDManagerImpl)?.lastDropHandler = null
    }

    eventCount++
    keyboardBusy = e is KeyEvent || keyboardEventPosted.get() > keyboardEventDispatched.get()
    if (e is KeyEvent) {
      if (e.getID() == KeyEvent.KEY_RELEASED && e.keyCode == KeyEvent.VK_SHIFT) {
        mouseEventDispatcher.resetHorScrollingTracker()
      }
    }
    if (e is MouseWheelEvent && processMouseWheelEvent(e)) {
      return
    }

    // increment the activity counter before performing the action
    // so that they are called with data providers with fresh data
    if (isUserActivityEvent(e)) {
      ActivityTracker.getInstance().inc()
    }
    if (popupManager.isPopupActive && popupManager.dispatch(e)) {
      if (keyEventDispatcher.isWaitingForSecondKeyStroke) {
        keyEventDispatcher.state = KeyState.STATE_INIT
      }
      return
    }

    if (e is WindowEvent) {
      // app activation can call methods that need write intent (like project saving)
      threadingSupport.runWriteIntentReadAction<Unit, Throwable> { processAppActivationEvent(e) }
    }
    if (dispatchByCustomDispatchers(e)) {
      return
    }
    if (e is InputMethodEvent && SystemInfoRt.isMac && keyEventDispatcher.isWaitingForSecondKeyStroke) {
      return
    }

    when {
      e is MouseEvent -> threadingSupport.runWriteIntentReadAction<Unit, Throwable> { dispatchMouseEvent(e) }
      e is KeyEvent -> threadingSupport.runWriteIntentReadAction<Unit, Throwable> { dispatchKeyEvent(e) }
      appIsLoaded() -> {
        val app = ApplicationManagerEx.getApplicationEx()
        if (e is ComponentEvent) {
          if (!app.isHeadlessEnvironment) {
            (app.serviceIfCreated<WindowManager>() as? WindowManagerEx)?.dispatchComponentEvent(e)
          }
        }
        threadingSupport.runWithoutImplicitRead { defaultDispatchEvent(e) }
      }
      else -> threadingSupport.runWithoutImplicitRead { defaultDispatchEvent(e) }
    }
  }

  private fun isUserActivityEvent(e: AWTEvent): Boolean =
    KeyEvent.KEY_PRESSED == e.id ||
    KeyEvent.KEY_TYPED == e.id ||
    MouseEvent.MOUSE_PRESSED == e.id ||
    MouseEvent.MOUSE_RELEASED == e.id ||
    MouseEvent.MOUSE_CLICKED == e.id ||
    e is FocusEvent ||
    e is WindowEvent && e.id != WindowEvent.WINDOW_CLOSED

  private fun processIdleActivityListeners(e: AWTEvent) {
    if (!isUserActivityEvent(e)) return
    // Increment the activity counter right before notifying listeners
    // so that the listeners would get data providers with fresh data
    ActivityTracker.getInstance().inc()
    idleTracker()
    synchronized(lock) {
      lastActiveTime = System.nanoTime()
      for (activityListener in activityListeners) {
        activityListener.run()
      }
    }
  }

  private fun dispatchKeyEvent(e: KeyEvent) {
    if (keyEventDispatcher.dispatchKeyEvent(e)) {
      e.consume()
    }
    defaultDispatchEvent(e)
  }

  private fun dispatchMouseEvent(e: MouseEvent) {
    @Suppress("DEPRECATION")
    if (e.id == MouseEvent.MOUSE_PRESSED && e.modifiers > 0 && e.modifiersEx == 0) {
      resetGlobalMouseEventTarget(e)
    }
    if (IdeMouseEventDispatcher.patchClickCount(e) && e.id == MouseEvent.MOUSE_CLICKED) {
      redispatchLater(e)
    }
    if (!mouseEventDispatcher.dispatchMouseEvent(e)) {
      defaultDispatchEvent(e)
    }
  }

  /**
   * [java.awt.LightweightDispatcher.processMouseEvent] uses a recent 'active' component from inner `WeakReference`
   * (see [LightweightDispatcher.mouseEventTarget]), even if the component has been already removed from component hierarchy.
   * So we have to reset this WeakReference with a synthetic event just before processing the actual one.
   */
  private fun resetGlobalMouseEventTarget(me: MouseEvent) {
    super.dispatchEvent(MouseEvent(me.component, MouseEvent.MOUSE_MOVED, me.getWhen(), 0, me.x, me.y, 0, false, 0))
  }

  private fun redispatchLater(me: MouseEvent) {
    @Suppress("DEPRECATION")
    val toDispatch = MouseEvent(me.component, me.id, System.currentTimeMillis(), me.modifiers, me.x, me.y, 1, me.isPopupTrigger, me.button)
    invokeLater { dispatchEvent(toDispatch) }
  }

  private fun dispatchByCustomDispatchers(e: AWTEvent): Boolean {
    for (eachDispatcher in dispatchers) {
      try {
        if (eachDispatcher.dispatch(e)) {
          return true
        }
      }
      catch (t: Throwable) {
        processException(t)
      }
    }

    for (eachDispatcher in DISPATCHER_EP.extensionsIfPointIsRegistered) {
      try {
        if (eachDispatcher.dispatch(e)) {
          return true
        }
      }
      catch (t: Throwable) {
        processException(t)
      }
    }
    return false
  }

  private fun defaultDispatchEvent(e: AWTEvent) {
    try {
      maybeReady()
      val me = e as? MouseEvent
      val ke = e as? KeyEvent
      val consumed = ke == null || ke.isConsumed
      if (me != null && (me.isPopupTrigger || e.id == MouseEvent.MOUSE_PRESSED) ||
          ke != null /*&& ke.keyCode == KeyEvent.VK_CONTEXT_MENU*/) {
        popupTriggerTime = System.nanoTime()
      }
      val source = e.source
      if (source is IdeFrameImpl) {
        when (e.id) {
          MouseEvent.MOUSE_MOVED -> {
            e as MouseEvent
            if (!source.isActive) {
              source.lastInactiveMouseXAbs = e.xOnScreen
              source.lastInactiveMouseYAbs = e.yOnScreen
            }
          }
          WindowEvent.WINDOW_ACTIVATED -> {
            source.isFirstMousePressed = true
          }
          MouseEvent.MOUSE_PRESSED -> {
            e as MouseEvent
            source.wasJustActivatedByClick =
              source.isFirstMousePressed &&
              e.xOnScreen == source.lastInactiveMouseXAbs &&
              e.yOnScreen == source.lastInactiveMouseYAbs
            source.isFirstMousePressed = false
          }
        }
      }
      super.dispatchEvent(e)
      // collect mnemonics statistics only if a key event was processed above
      if (!consumed && ke!!.isConsumed && KeyEvent.KEY_PRESSED == ke.id) {
        logMnemonicUsed(ke)
      }
    }
    catch (t: Throwable) {
      processException(t)
    }
  }

  @Internal
  fun flushQueue() {
    EDT.assertIsEdt()
    resetThreadContext().use {
      while (true) {
        peekEvent() ?: return
        try {
          dispatchEvent(nextEvent)
        }
        catch (e: Exception) {
          Logs.LOG.error(e)
        }
      }
    }
  }

  fun pumpEventsForHierarchy(modalComponent: Component, exitCondition: Future<*>, eventConsumer: Consumer<AWTEvent>) {
    EDT.assertIsEdt()
    Logs.LOG.debug { "pumpEventsForHierarchy($modalComponent, $exitCondition)" }

    while (!exitCondition.isDone) {
      try {
        val event = nextEvent
        val consumed = consumeUnrelatedEvent(modalComponent, event)
        if (!consumed) {
          dispatchEvent(event)
        }
        eventConsumer.accept(event)
      }
      catch (e: Throwable) {
        Logs.LOG.error(e)
      }
    }
    Logs.LOG.debug { "pumpEventsForHierarchy.exit($modalComponent, $exitCondition)" }
  }

  fun interface EventDispatcher {
    fun dispatch(e: AWTEvent): Boolean
  }

  val idleTime: Long
    get() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastActiveTime)

  /**
   * When `blockMode` is `COMPLETE`, blocks following related mouse events completely, when `blockMode` is
   * `ACTIONS` only blocks performing actions bound to corresponding mouse shortcuts.
   */
  /**
   * Same as [.blockNextEvents] with `blockMode` equal to `COMPLETE`.
   */
  @JvmOverloads
  fun blockNextEvents(e: MouseEvent, blockMode: BlockMode = BlockMode.COMPLETE) {
    mouseEventDispatcher.blockNextEvents(e, blockMode)
  }

  private val isReady: Boolean
    get() = !keyboardBusy && keyEventDispatcher.isReady

  internal fun maybeReady() {
    if (ready.isNotEmpty() && isReady) {
      invokeReadyHandlers()
    }
  }

  private fun invokeReadyHandlers() {
    val ready = ready.toTypedArray()
    this.ready.clear()
    for (each in ready) {
      each.run()
    }
  }

  fun doWhenReady(runnable: Runnable) {
    if (isDispatchThread()) {
      ready.add(runnable)
      maybeReady()
    }
    else {
      invokeLater {
        ready.add(runnable)
        maybeReady()
      }
    }
  }

  val isPopupActive: Boolean
    get() = popupManager.isPopupActive

  //Windows OS doesn't support a Windows+Up/Down shortcut for dialogs, so we provide a workaround
  private inner class WindowsUpMaximizer : EventDispatcher {
    override fun dispatch(e: AWTEvent): Boolean {
      if ((winMetaPressed
           && e is KeyEvent && e.getID() == KeyEvent.KEY_RELEASED) && (e.keyCode == KeyEvent.VK_UP || e.keyCode == KeyEvent.VK_DOWN)) {
        val parent: Component? = ComponentUtil.getWindow(e.component)
        if (parent is JDialog) {
          invokeLater {
            if (e.keyCode == KeyEvent.VK_UP) {
              MaximizeActiveDialogAction.maximize(parent)
            }
            else {
              MaximizeActiveDialogAction.normalize(parent)
            }
          }
          return true
        }
      }
      return false
    }
  }

  val isInputMethodEnabled: Boolean
    get() = !SystemInfoRt.isMac || inputMethodLock == 0

  fun disableInputMethods(parentDisposable: Disposable) {
    inputMethodLock++
    Disposer.register(parentDisposable) { inputMethodLock-- }
  }

  override fun postEvent(event: AWTEvent) {
    doPostEvent(event)
  }

  // return true if posted, false if consumed immediately
  fun doPostEvent(event: AWTEvent): Boolean {
    for (listener in postEventListeners) {
      if (listener(event)) {
        return false
      }
    }
    eventsPosted.incrementAndGet()

    attachClientIdIfNeeded(event)?.let {
      super.postEvent(it)
      return true
    }

    if (event is KeyEvent) {
      keyboardEventPosted.incrementAndGet()
    }
    if (isFocusEvent(event)) {
      focusEventList.add(event)
    }
    super.postEvent(event)
    return true
  }

  private fun attachClientIdIfNeeded(event: AWTEvent): AWTEvent? {
    if (!ClientId.propagateAcrossThreads) {
      return null
    }
    // We don't 'attach' a current client ID to `PeerEvent` instances for two reasons.
    // First, they are often posted to `EventQueue` indirectly (first to `sun.awt.PostEventQueue`, and then to the `EventQueue` by
    // `SunToolkit#flushPendingEvents`), so a current client ID might be unrelated to the code that created those events.
    // Second, just wrapping `PeerEvent` into a new `InvocationEvent` loses the information about priority kept in the former
    // and changes the overall events' processing order.
    if (event is InvocationEvent && event !is PeerEvent) {
      val runnable = InvocationUtil.extractRunnable(event)
      if (runnable is ContextAwareRunnable) {
        return null
      }
      val clientId = current
      return InvocationEvent(event.source) { withClientId(clientId).use { dispatchEvent(event) } }
    }
    if (event.id in ComponentEvent.COMPONENT_FIRST..ComponentEvent.COMPONENT_LAST) {
      return ComponentEventWithClientId((event as ComponentEvent).component, event.id, current)
    }
    return null
  }

  private fun withAttachedClientId(event: AWTEvent): AccessToken {
    return if (event is ComponentEventWithClientId) withClientId(event.clientId) else AccessToken.EMPTY_ACCESS_TOKEN
  }

  @Deprecated("Does nothing currently")
  fun flushDelayedKeyEvents() {
  }

  private fun isTestMode(): Boolean {
    var testMode = testMode
    if (testMode != null) {
      return testMode
    }
    val application = ApplicationManager.getApplication() ?: return false
    testMode = application.isUnitTestMode
    this.testMode = testMode
    return testMode
  }

  @TestOnly
  fun executeInProductionModeEvenThoughWeAreInTests(runnable: Runnable) {
    assert(ApplicationManager.getApplication().isUnitTestMode)
    testMode = false
    try {
      runnable.run()
    }
    finally {
      testMode = true
    }
  }

  /**
   * @see IdeEventQueue.blockNextEvents
   */
  enum class BlockMode {
    COMPLETE,
    ACTIONS
  }

  fun addPostEventListener(listener: PostEventHook, parentDisposable: Disposable) {
    postEventListeners.add(listener)
    Disposer.register(parentDisposable) {
      postEventListeners.remove(listener)
    }
  }

  fun getPostedEventCount() = eventsPosted.get()

  fun getReturnedEventCount() = eventsReturned.get()

  fun getPostedSystemEventCount() = (AppContext.getAppContext()?.get("jb.postedSystemEventCount") as? AtomicLong)?.get() ?: -1

  fun flushNativeEventQueue() {
    SunToolkit.flushPendingEvents()
  }
}

// IdeEventQueue is created before log configuration - cannot be initialized as a part of IdeEventQueue
private object Logs {
  @JvmField
  val LOG: Logger = logger<IdeEventQueue>()

  @JvmField
  val FOCUS_AWARE_RUNNABLES_LOG: Logger = Logger.getInstance(IdeEventQueue::class.java.name + ".runnables")
}

/**
 * An absolute guru API, please avoid using it at all costs.
 * @return true, if the event is handled by the listener and shouldn't be added to an event queue at all
 */
typealias PostEventHook = (event: AWTEvent) -> Boolean

private val DISPATCHER_EP = ExtensionPointName<IdeEventQueue.EventDispatcher>("com.intellij.ideEventQueueDispatcher")

private const val defaultEventWithWrite = false

private val isSkipMetaPressOnLinux = java.lang.Boolean.getBoolean("keymap.skip.meta.press.on.linux")

private var transactionGuard: TransactionGuardImpl? = null

private fun skipMoveResizeEvents(event: AWTEvent): Boolean {
  // JList, JTable and JTree paint every cell/row/column using the following method:
  //   CellRendererPane.paintComponent(Graphics, Component, Container, int, int, int, int, boolean)
  // This method sets bounds to a renderer component and invokes the following internal method:
  //   Component.notifyNewBounds
  // All default and simple renderers do not post specified events,
  // but panel-based renderers have to post events by contract.
  when (event.id) {
    ComponentEvent.COMPONENT_MOVED, ComponentEvent.COMPONENT_RESIZED, HierarchyEvent.ANCESTOR_MOVED, HierarchyEvent.ANCESTOR_RESIZED -> {
      val source = event.source
      if (source is Component && ComponentUtil.getParentOfType(CellRendererPane::class.java, source) != null) {
        return true
      }
    }
  }
  return false
}

private fun addProcessor(dispatcher: IdeEventQueue.EventDispatcher, parent: Disposable?, set: MutableCollection<IdeEventQueue.EventDispatcher>) {
  set.add(dispatcher)
  if (parent != null) {
    Disposer.register(parent) { set.remove(dispatcher) }
  }
}

private var appIsLoaded: Boolean? = null

private fun appIsLoaded(): Boolean = when {
  appIsLoaded == true -> true
  appIsLoaded == null && LoadingState.COMPONENTS_LOADED.isOccurred -> {
    appIsLoaded = true
    true
  }
  else -> false
}

private fun dispatchTrayIconEvent(e: AWTEvent) {
  if (e is ActionEvent) {
    for (listener in (e.getSource() as TrayIcon).actionListeners) {
      listener.actionPerformed(e)
    }
  }
}

private fun disableAltGrUnsupportedOnMac(e: AWTEvent) {
  if (e is KeyEvent && e.keyCode == KeyEvent.VK_ALT_GRAPH) {
    e.keyCode = KeyEvent.VK_ALT
  }
  IdeKeyEventDispatcher.removeAltGraph((e as InputEvent))
}

private fun isMetaKeyPressedOnLinux(e: AWTEvent): Boolean {
  if (!isSkipMetaPressOnLinux) {
    return false
  }

  val metaIsPressed = e is InputEvent && e.modifiersEx and InputEvent.META_DOWN_MASK != 0
  val typedKeyEvent = e.id == KeyEvent.KEY_TYPED
  return SystemInfoRt.isLinux && typedKeyEvent && metaIsPressed
}

private fun isInputEvent(e: AWTEvent): Boolean {
  return e is InputEvent || e is InputMethodEvent || e is WindowEvent || e is ActionEvent
}

internal fun performActivity(e: AWTEvent, needWIL: Boolean, runnable: () -> Unit) {
  var transactionGuard = transactionGuard
  if (transactionGuard == null && appIsLoaded()) {
    val app = ApplicationManager.getApplication()
    if (app != null && !app.isDisposed) {
      transactionGuard = TransactionGuard.getInstance() as TransactionGuardImpl
      com.intellij.ide.transactionGuard = transactionGuard
    }
  }

  if (transactionGuard == null) {
    runnable()
  }
  else {
    val runnableWithWIL = if (needWIL) { { WriteIntentReadAction.run(runnable) } } else { runnable }
    transactionGuard.performActivity(isInputEvent(e) || e is ItemEvent || e is FocusEvent, runnableWithWIL)
  }
}

private fun mapEvent(e: AWTEvent): AWTEvent {
  return if (StartupUiUtil.isXToolkit() && e is MouseEvent && e.button > 3) mapXWindowMouseEvent(e) else e
}

private fun mapXWindowMouseEvent(src: MouseEvent): AWTEvent {
  if (src.button < 6) {
    // buttons 4-5 come from touchpad, they must be converted to horizontal scrolling events
    @Suppress("DEPRECATION") val modifiers = src.modifiers or InputEvent.SHIFT_DOWN_MASK
    return MouseWheelEvent(src.component, MouseEvent.MOUSE_WHEEL, src.getWhen(), modifiers, src.x, src.y, 0, false,
                           MouseWheelEvent.WHEEL_UNIT_SCROLL, src.clickCount, if (src.button == 4) -1 else 1)
  }
  else {
    // Here we "shift" events with buttons `6` and `7` to similar events with buttons 4 and 5
    // See `java.awt.InputEvent#BUTTON_DOWN_MASK`, 1<<14 is the 4th physical button, 1<<15 is the 5th.
    @Suppress("DEPRECATION") val modifiers = src.modifiers or (1 shl 8 + src.button)
    return MouseEvent(src.component, src.id, src.getWhen(), modifiers, src.x, src.y, 1, src.isPopupTrigger, src.button - 2)
  }
}

private fun processMouseWheelEvent(e: MouseWheelEvent): Boolean {
  val selectedPath = MenuSelectionManager.defaultManager().selectedPath
  if (selectedPath.isEmpty() || selectedPath[0] is ComboPopup) {
    return false
  }

  e.consume()
  (selectedPath[0].component as? JBPopupMenu)?.processMouseWheelEvent(e)
  return true
}

private fun processAppActivationEvent(event: WindowEvent) {
  ApplicationActivationStateManager.updateState(event)

  if (!LoadingState.COMPONENTS_LOADED.isOccurred ||
      (event.id != WindowEvent.WINDOW_DEACTIVATED && event.id != WindowEvent.WINDOW_LOST_FOCUS)) {
    return
  }

  val eventWindow = event.window
  val focusOwnerInDeactivatedWindow = eventWindow.mostRecentFocusOwner ?: return

  val windowManager = ApplicationManager.getApplication().serviceIfCreated<WindowManager>() as WindowManagerEx? ?: return
  val frame = ComponentUtil.findUltimateParent(eventWindow)
  for (frameHelper in windowManager.projectFrameHelpers) {
    if (frame === frameHelper.frame) {
      val focusManager = IdeFocusManager.getGlobalInstance()
      if (focusManager is FocusManagerImpl) {
        focusManager.setLastFocusedAtDeactivation(frame as Window, focusOwnerInDeactivatedWindow)
      }
    }
  }
}

private fun isFocusEvent(e: AWTEvent): Boolean {
  return e.id == FocusEvent.FOCUS_GAINED ||
         e.id == FocusEvent.FOCUS_LOST ||
         e.id == WindowEvent.WINDOW_ACTIVATED ||
         e.id == WindowEvent.WINDOW_DEACTIVATED ||
         e.id == WindowEvent.WINDOW_LOST_FOCUS ||
         e.id == WindowEvent.WINDOW_GAINED_FOCUS
}

private fun isKeyboardEvent(event: AWTEvent): Boolean = event is KeyEvent

// return true if consumed
internal fun consumeUnrelatedEvent(modalComponent: Component?, event: AWTEvent): Boolean {
  if (modalComponent == null) {
    if (event is InputEvent && event.getSource() is Component) {
      Logs.LOG.debug { "pumpEventsForHierarchy.consumed: $event" }
      event.consume()
      return true
    }
    else {
      return false
    }
  }

  var consumed = false
  if (event is InputEvent) {
    val s = event.getSource()
    if (s is Component) {
      var c: Component? = s
      val modalWindow = SwingUtilities.getWindowAncestor(modalComponent)
      while (c != null && c !== modalWindow) {
        c = c.parent
      }
      if (c == null) {
        consumed = true
        Logs.LOG.debug { "pumpEventsForHierarchy.consumed: $event" }
        event.consume()
      }
    }
  }
  return consumed
}

private object SequencedEventNestedFieldHolder {
  private val DISPOSE_METHOD: MethodHandle

  @JvmField
  val SEQUENCED_EVENT_CLASS: Class<*> = SequencedEventNestedFieldHolder::class.java.classLoader.loadClass("java.awt.SequencedEvent")

  fun invokeDispose(event: AWTEvent) {
    DISPOSE_METHOD.invoke(event)
  }

  init {
    DISPOSE_METHOD = MethodHandles.privateLookupIn(SEQUENCED_EVENT_CLASS, MethodHandles.lookup())
      .findVirtual(SEQUENCED_EVENT_CLASS, "dispose", MethodType.methodType(Void.TYPE))
  }
}

/**
 * This should've been an item in [IdeEventQueue.dispatchers],
 * but [IdeEventQueue.dispatchByCustomDispatchers] is run after [IdePopupManager.dispatch]
 * and after [processAppActivationEvent], which defeats the very purpose of this flag.
 */
@Internal
internal var skipWindowDeactivationEvents: Boolean = false

// we have to stop editing with <ESC> (if any) and consume the event to prevent any further processing (dialog closing etc.)
private class EditingCanceller : IdeEventQueue.EventDispatcher {
  override fun dispatch(e: AWTEvent): Boolean =
    e is KeyEvent &&
    e.getID() == KeyEvent.KEY_PRESSED &&
    e.keyCode == KeyEvent.VK_ESCAPE &&
    !IdeEventQueue.getInstance().popupManager.isPopupActive &&
    cancelCellEditing()
}

private fun cancelCellEditing(): Boolean {
  val owner = ComponentUtil.findParentByCondition(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner) { component ->
    component is JTable || component is JTree
  }
  return when {
    owner is JTable && owner.isEditing -> {
      owner.editingCanceled(null)
      true
    }
    owner is JTree && owner.isEditing -> {
      owner.cancelEditing()
      true
    }
    else -> false
  }
}

private class WindowsAltSuppressor : IdeEventQueue.EventDispatcher {
  private var waitingForAltRelease = false
  private var robot: Robot? = null

  override fun dispatch(e: AWTEvent): Boolean = e is KeyEvent && dispatchKeyEvent(e)

  private fun dispatchKeyEvent(ke: KeyEvent): Boolean {
    @Suppress("DEPRECATION")
    val pureAlt = ke.keyCode == KeyEvent.VK_ALT && ke.modifiers or InputEvent.ALT_MASK == InputEvent.ALT_MASK
    if (!pureAlt) {
      waitingForAltRelease = false
      return false
    }

    val uiSettings = UISettings.instanceOrNull
    if (uiSettings == null ||
        !SystemInfoRt.isWindows ||
        !Registry.`is`("actionSystem.win.suppressAlt", true) ||
        !(uiSettings.hideToolStripes || uiSettings.presentationMode)) {
      return false
    }

    val component = ke.component
    var dispatch = true
    if (ke.id == KeyEvent.KEY_PRESSED) {
      dispatch = !waitingForAltRelease
    }
    else if (ke.id == KeyEvent.KEY_RELEASED) {
      if (waitingForAltRelease) {
        waitingForAltRelease = false
        dispatch = false
      }
      else if (component != null) {
        EventQueue.invokeLater {
          try {
            val window = ComponentUtil.getWindow(component)
            if (window == null || !window.isActive) {
              return@invokeLater
            }
            waitingForAltRelease = true
            if (robot == null) {
              robot = Robot()
            }
            robot!!.keyPress(KeyEvent.VK_ALT)
            robot!!.keyRelease(KeyEvent.VK_ALT)
          }
          catch (e1: AWTException) {
            Logs.LOG.debug(e1)
          }
        }
      }
    }
    return !dispatch
  }
}

private class ComponentEventWithClientId(source: Component, id: Int, val clientId: ClientId) : ComponentEvent(source, id)

@Suppress("SpellCheckingInspection")
private fun abracadabraDaberBoreh(eventQueue: IdeEventQueue) {
  // We need to track if there are KeyBoardEvents in `IdeEventQueue`,
  // so we want to intercept all events posted to `IdeEventQueue` and increment counters.
  // However, the regular control flow goes like this:
  //   PostEventQueue.flush() -> EventQueue.postEvent() -> IdeEventQueue.postEventPrivate() ->
  //     we missed the event because `postEventPrivate()` can't be overridden.
  // Instead, we do the following:
  // - create a new `PostEventQueue` holding our `IdeEventQueue` instead of old `EventQueue`
  // - replace `PostEventQueue` value in `AppContext` with this new `PostEventQueue`
  // After that, the control flow goes like this:
  //   PostEventQueue.flush() -> IdeEventQueue.postEvent() -> we intercepted the event and incremented counters.
  val aClass = Class.forName("sun.awt.PostEventQueue")
  val constructor = MethodHandles.privateLookupIn(aClass, MethodHandles.lookup())
    .findConstructor(aClass, MethodType.methodType(Void.TYPE, EventQueue::class.java))
  val postEventQueue = constructor.invoke(eventQueue)
  AppContext.getAppContext().put("PostEventQueue", postEventQueue)
}

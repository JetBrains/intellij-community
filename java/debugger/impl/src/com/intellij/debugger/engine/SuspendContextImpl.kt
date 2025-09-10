// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.Patches
import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebuggerDiagnosticsUtil.needAnonymizedReports
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.engine.requests.RequestManagerImpl
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.frame.XSuspendContext
import com.sun.jdi.Location
import com.sun.jdi.ObjectReference
import com.sun.jdi.ThreadReference
import com.sun.jdi.event.EventSet
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.request.EventRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Supplier
import kotlin.concurrent.Volatile

abstract class SuspendContextImpl @ApiStatus.Internal constructor(
  private val myDebugProcess: DebugProcessImpl,
  @param:MagicConstant(flagsFromClass = EventRequest::class)
  private val mySuspendPolicy: Int,
  @JvmField protected var myVotesToVote: Int,
  private var myEventSet: EventSet?,
  private val myDebugId: Long,
) : XSuspendContext(), SuspendContext, Disposable {

  // Save the VM related to this suspend context, as a VM may be changed due to reattach
  @Suppress("UsagesOfObsoleteApi")
  val virtualMachineProxy: VirtualMachineProxyImpl = myDebugProcess.getVirtualMachineProxy()

  @Suppress("UsagesOfObsoleteApi")
  val managerThread: DebuggerManagerThreadImpl = myDebugProcess.managerThread

  /** The thread that comes from the JVM event or was reset by switching to suspend-all procedure  */
  @get:ApiStatus.Internal
  var eventThread: ThreadReferenceProxyImpl? = null
    private set

  @JvmField
  internal var myIsVotedForResume: Boolean = true

  @JvmField
  protected var mySteppingThreadForResumeOneSteppingCurrentMode: ThreadReferenceProxyImpl? = null

  @get:ApiStatus.Internal
  @set:ApiStatus.Internal
  var lightThreadFilter: Any? = null

  @JvmField
  internal var myResumedThreads: MutableSet<ThreadReferenceProxyImpl>? = null

  @JvmField
  protected val myNotExecutableThreads: MutableSet<ThreadReferenceProxyImpl> = HashSet()

  // There may be several events for the same break-point. So let's use custom processing if any of them is wanted it.
  @JvmField
  protected var mySuspendAllSwitchedContext: Boolean = false

  @Volatile
  private var myIsResumed = false

  @JvmField
  @Volatile
  protected var myIsGoingToResume: Boolean = false

  private val myPostponedCommands = ConcurrentLinkedQueue<SuspendContextCommandImpl>()

  @JvmField
  @Volatile
  var myInProgress: Boolean = false
  private val myKeptReferences = hashSetOf<ObjectReference>()
  var evaluationContext: EvaluationContextImpl? = null
    private set
  private var myFrameCount = -1
  private val myCoroutineScope = managerThread.coroutineScope.childScope("SuspendContextImpl $myDebugId")

  private var myActiveExecutionStack: JavaExecutionStack? = null

  private val myListener = object : ThreadReferenceProxyImpl.ThreadListener {
    override fun threadSuspended() {
      myNotExecutableThreads.clear()
      myFrameCount = -1
    }

    override fun threadResumed() {
      myNotExecutableThreads.clear()
      myFrameCount = -1
    }
  }

  init {
    if (!Disposer.tryRegister(managerThread, this)) {
      // could be due to VM death
      Disposer.dispose(this)
    }
  }

  override fun getCoroutineScope(): CoroutineScope = myCoroutineScope

  fun setThread(thread: ThreadReference?) {
    assertCanBeUsed()
    val threadProxy = virtualMachineProxy.getThreadReferenceProxy(thread)
    assertInLog(eventThread == null || eventThread === threadProxy) {
      "Invalid thread setting in $this: myThread = ${eventThread}, thread = $thread"
    }
    setThread(threadProxy)
  }

  fun resetThread(threadProxy: ThreadReferenceProxyImpl) {
    val currentThread = eventThread
    if (currentThread === threadProxy) return
    assertInLog(evaluationContext == null) { "Resetting thread during evaluation is not supported: $this" }
    assertInLog(myActiveExecutionStack == null) { "Thread should be retested before the active execution stack initialization: $this" }
    assertCanBeUsed()
    currentThread?.removeListener(myListener)
    myFrameCount = -1
    setThread(threadProxy)
  }

  private fun setThread(threadProxy: ThreadReferenceProxyImpl?) {
    if (threadProxy != null && eventThread !== threadProxy && !myDebugProcess.disposable.isDisposed()) { // do not add more than once
      threadProxy.addListener(myListener, this)
    }
    eventThread = threadProxy
  }

  override fun dispose() {
    myCoroutineScope.cancel()
    cancelAllPostponed()
  }

  val cachedThreadFrameCount: Int
    get() {
      if (myFrameCount == -1) {
        try {
          myFrameCount = eventThread?.frameCount() ?: 0
        }
        catch (_: EvaluateException) {
          myFrameCount = 0
        }
      }
      return myFrameCount
    }

  val location: Location?
    get() {
      // getting location from the event set is much faster than obtaining the frame and getting it from there
      val executionStack = myActiveExecutionStack
      val currentThread = eventThread
      if ((executionStack == null || executionStack.threadProxy === currentThread) && myEventSet != null) {
        val event = myEventSet?.filterIsInstance<LocatableEvent>()?.firstOrNull()
        if (event != null) {
          // myThread can be reset to the different thread in resetThread() method
          if (currentThread == null || currentThread.getThreadReference() == event.thread()) {
            return event.location()
          }
        }
      }
      try {
        return getFrameProxy()?.location()
      }
      catch (e: Throwable) {
        LOG.debug(e)
      }
      return null
    }

  protected abstract fun resumeImpl()

  fun resume(callResume: Boolean) {
    assertNotResumed()
    if (isEvaluating) {
      logError("Resuming context $this while evaluating")
    }
    DebuggerManagerThreadImpl.assertIsManagerThread()
    try {
      if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
        // delay enable collection to speed up the resume
        for (r in myKeptReferences) {
          managerThread.schedule(PrioritizedTask.Priority.LOWEST) { DebuggerUtilsEx.enableCollection(r) }
        }
        myKeptReferences.clear()
      }

      cancelAllPostponed()
      if (callResume) {
        LOG.debug { "Resuming context $this" }
        resumeImpl()
      }
    }
    finally {
      myIsResumed = true
      Disposer.dispose(this)
    }
  }

  private fun assertCanBeUsed() {
    assertNotResumed()
    assertInLog(!myIsGoingToResume) { "Context $this is going to resume." }
  }

  private fun assertNotResumed() {
    if (myIsResumed && myDebugProcess.isAttached) {
      logError("Cannot access $this because it is resumed.")
    }
  }

  var eventSet: EventSet?
    get() = myEventSet
    set(eventSet) {
      assertCanBeUsed()
      assertInLog(myEventSet == null) { "Event set in ${this} should be empty" }
      myEventSet = eventSet
      LOG.debug { "Installed set into $this" }
    }

  override fun getDebugProcess(): DebugProcessImpl = myDebugProcess

  override fun getFrameProxy(): StackFrameProxyImpl? = try {
    myActiveExecutionStack?.threadProxy?.frame(0) ?: frameProxyFromTechnicalThread
  }
  catch (e: EvaluateException) {
    myDebugProcess.logError("Error in proxy extracting", e)
    frameProxyFromTechnicalThread
  }


  private val frameProxyFromTechnicalThread: StackFrameProxyImpl?
    get() {
      assertNotResumed()
      try {
        val currentThread = eventThread ?: return null
        val frameCount = currentThread.frameCount()
        if (myFrameCount != -1 && myFrameCount != frameCount) {
          logError("Incorrect frame count, cached $myFrameCount, now $frameCount, thread $currentThread suspend count ${currentThread.getSuspendCount()}")
        }
        myFrameCount = frameCount
        if (frameCount > 0) {
          return currentThread.frame(0)
        }
        return null
      }
      catch (_: EvaluateException) {
        return null
      }
    }

  override fun getThread(): ThreadReferenceProxyImpl? = myActiveExecutionStack?.threadProxy ?: eventThread

  @MagicConstant(flagsFromClass = EventRequest::class)
  override fun getSuspendPolicy(): Int = mySuspendPolicy

  val suspendPolicyFromRequestors: String?
    get() {
      if (mySuspendPolicy == EventRequest.SUSPEND_ALL) return DebuggerSettings.SUSPEND_ALL
      val eventSet = myEventSet
      if (eventSet == null) return asStrPolicy()
      return if (RequestManagerImpl.hasSuspendAllRequestor(eventSet)) DebuggerSettings.SUSPEND_ALL else asStrPolicy()
    }

  private fun asStrPolicy(): String {
    return when (mySuspendPolicy) {
      EventRequest.SUSPEND_ALL -> DebuggerSettings.SUSPEND_ALL
      EventRequest.SUSPEND_EVENT_THREAD -> DebuggerSettings.SUSPEND_THREAD
      EventRequest.SUSPEND_NONE -> DebuggerSettings.SUSPEND_NONE
      else -> throw IllegalStateException("Cannot convert number $mySuspendPolicy")
    }
  }

  @Suppress("unused")
  fun doNotResumeHack() {
    assertNotResumed()
    myVotesToVote = 1000000000
  }

  fun isExplicitlyResumed(thread: ThreadReferenceProxyImpl): Boolean = myResumedThreads?.contains(thread) == true

  fun suspends(thread: ThreadReferenceProxyImpl): Boolean {
    assertNotResumed()
    if (thread === evaluationContext?.threadForEvaluation) return false
    return when (getSuspendPolicy()) {
      EventRequest.SUSPEND_ALL -> !isExplicitlyResumed(thread)
      EventRequest.SUSPEND_EVENT_THREAD -> thread === eventThread
      else -> false
    }
  }

  val isEvaluating: Boolean
    get() {
      assertNotResumed()
      return evaluationContext != null
    }

  val isResumed: Boolean
    get() = myIsResumed || myIsGoingToResume

  @ApiStatus.Internal
  fun setIsEvaluating(context: EvaluationContextImpl?) {
    assertCanBeUsed()
    evaluationContext = context
  }

  override fun toString(): String = "{$myDebugId} SP=${suspendPolicyString} ${oldToString()}"

  private fun eventSetAsString(): String? {
    val eventSet = myEventSet ?: return "null"
    if (!needAnonymizedReports()) return eventSet.toString()
    return "EventSet" + DebuggerDiagnosticsUtil.getEventSetClasses(eventSet) + " in " + eventThread
  }

  private val stackStr: String?
    get() {
      val executionStack = myActiveExecutionStack ?: return "null"
      return if (needAnonymizedReports()) "Stack in $eventThread" else executionStack.toString()
    }

  private fun oldToString(): String? {
    if (myEventSet != null) return eventSetAsString()
    return eventThread?.toString() ?: JavaDebuggerBundle.message("string.null.context")
  }

  fun toAttachmentString(): String {
    val sb = StringBuilder()
    sb.append("------------------\ncontext ").append(this).append(":\n")
    sb.append("myDebugId = ").append(myDebugId).append("\n")
    sb.append("myThread = ").append(eventThread).append("\n")
    sb.append("Suspend policy = ").append(suspendPolicyString).append("\n")
    sb.append("myEventSet = ").append(eventSetAsString()).append("\n")
    sb.append("myInProgress = ").append(myInProgress).append("\n")
    sb.append("myEvaluationContext = ").append(evaluationContext).append("\n")
    sb.append("myFrameCount = ").append(myFrameCount).append("\n")
    sb.append("myActiveExecutionStack = ").append(stackStr).append("\n")

    val resumedThreads = myResumedThreads
    if (!resumedThreads.isNullOrEmpty()) {
      sb.append("myResumedThreads:\n")
      for (thread in resumedThreads) {
        sb.append("  ").append(thread).append("\n")
      }
    }

    if (!myNotExecutableThreads.isEmpty()) {
      sb.append("myNotExecutableThreads:\n")
      for (thread in myNotExecutableThreads) {
        sb.append("  ").append(thread).append("\n")
      }
    }

    sb.append("mySuspendAllSwitchedContext = ").append(mySuspendAllSwitchedContext).append("\n")
    sb.append("myPostponedCommands: ").append(myPostponedCommands.size).append("\n")
    sb.append("myKeptReferences: ").append(myKeptReferences.size).append("\n")
    sb.append("myIsVotedForResume = ").append(myIsVotedForResume).append("\n")
    sb.append("myVotesToVote = ").append(myVotesToVote).append("\n")
    sb.append("myIsResumed = ").append(myIsResumed).append("\n")
    sb.append("myIsGoingToResume = ").append(myIsGoingToResume).append("\n")
    return sb.toString()
  }

  private val suspendPolicyString: String
    get() = when (getSuspendPolicy()) {
      EventRequest.SUSPEND_EVENT_THREAD -> "thread"
      EventRequest.SUSPEND_ALL -> "all"
      EventRequest.SUSPEND_NONE -> "none"
      else -> "other"
    }

  fun keep(reference: ObjectReference) {
    if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
      val added = myKeptReferences.add(reference)
      if (added) {
        DebuggerUtilsEx.disableCollection(reference)
      }
    }
  }

  fun keepAsync(reference: ObjectReference) {
    if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
      val added = myKeptReferences.add(reference)
      if (added) {
        DebuggerUtilsAsync.disableCollection(reference)
      }
    }
  }

  fun postponeCommand(command: SuspendContextCommandImpl) {
    if (!isResumed) {
      // Important! when postponing increment the holds counter, so that the action is not released too early.
      // This will ensure that the counter becomes zero only when the command is actually executed or canceled
      command.hold()
      myPostponedCommands.add(command)
    }
    else {
      command.notifyCancelled()
    }
  }

  fun cancelAllPostponed() {
    var postponed = pollPostponedCommand()
    while (postponed != null) {
      postponed.notifyCancelled()
      postponed = pollPostponedCommand()
    }
  }

  fun pollPostponedCommand(): SuspendContextCommandImpl? = myPostponedCommands.poll()

  override fun getActiveExecutionStack(): JavaExecutionStack? = myActiveExecutionStack

  fun initExecutionStacks(activeThread: ThreadReferenceProxyImpl?) {
    assertCanBeUsed()
    DebuggerManagerThreadImpl.assertIsManagerThread()
    if (eventThread == null) {
      setThread(activeThread)
    }
    if (activeThread != null) {
      if (mySuspendPolicy == EventRequest.SUSPEND_EVENT_THREAD && activeThread !== eventThread) {
        logError("Thread $activeThread was set as active into $this")
      }
      myActiveExecutionStack = JavaExecutionStack(activeThread, myDebugProcess, eventThread === activeThread)
      myActiveExecutionStack!!.initTopFrame()
    }
  }

  override fun computeExecutionStacks(container: XExecutionStackContainer) {
    assertCanBeUsed()
    managerThread.schedule(object : SuspendContextCommandImpl(this) {
      val myAddedThreads = hashSetOf<ThreadReferenceProxyImpl>()

      override fun contextAction(suspendContext: SuspendContextImpl) {
        val pausedThreads = myDebugProcess.suspendManager.getPausedContexts().mapNotNull { it.eventThread }
        // add paused threads first
        CompletableFuture.completedFuture(pausedThreads)
          .thenCompose { tds -> addThreads(tds, THREAD_NAME_COMPARATOR, false) }
          .thenCompose { res ->
            if (res)
              suspendContext.virtualMachineProxy.allThreadsAsync()
            else
              CompletableFuture.completedFuture(emptyList())
          }
          .thenCompose { tds -> addThreads(tds, THREADS_SUSPEND_AND_NAME_COMPARATOR, true) }
          .exceptionally { DebuggerUtilsAsync.logError(it) }
      }

      fun addThreads(
        threads: Collection<ThreadReferenceProxyImpl?>,
        comparator: Comparator<in JavaExecutionStack>,
        last: Boolean,
      ): CompletableFuture<Boolean> {
        val futures = threads.filterNotNull().mapNotNull { thread ->
          if (container.isObsolete) return CompletableFuture.completedFuture(false)
          if (!myAddedThreads.add(thread)) return@mapNotNull null
          JavaExecutionStack.create(thread, myDebugProcess, thread === eventThread)
        }
        return DebuggerUtilsAsync.reschedule(CompletableFuture.allOf(*futures.toTypedArray())).thenApply {
          if (container.isObsolete) return@thenApply true
          val stacks = futures.map { it.join() }.sortedWith(comparator)
          container.addExecutionStack(stacks, last)
          true
        }
      }
    })
  }

  private fun logError(message: String) {
    myDebugProcess.logError(message)
  }

  private fun assertInLog(value: Boolean, supplier: Supplier<String>) {
    if (!value) {
      myDebugProcess.logError(supplier.get())
    }
  }

  companion object {
    private val LOG = Logger.getInstance(SuspendContextImpl::class.java)

    private val THREAD_NAME_COMPARATOR: Comparator<JavaExecutionStack> =
      Comparator.comparing(JavaExecutionStack::getDisplayName, java.lang.String.CASE_INSENSITIVE_ORDER)

    private val SUSPEND_FIRST_COMPARATOR: Comparator<ThreadReferenceProxyImpl> =
      Comparator.comparing<ThreadReferenceProxyImpl, Boolean>(ThreadReferenceProxyImpl::isSuspended).reversed()

    private val THREADS_SUSPEND_AND_NAME_COMPARATOR: Comparator<JavaExecutionStack> =
      Comparator.comparing(JavaExecutionStack::getThreadProxy, SUSPEND_FIRST_COMPARATOR).thenComparing(THREAD_NAME_COMPARATOR)
  }
}

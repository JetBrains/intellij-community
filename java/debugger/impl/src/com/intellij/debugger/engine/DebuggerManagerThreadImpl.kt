// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.engine.managerThread.DebuggerCommand
import com.intellij.debugger.engine.managerThread.DebuggerManagerThread
import com.intellij.debugger.engine.managerThread.SuspendContextCommand
import com.intellij.debugger.impl.*
import com.intellij.debugger.statistics.StatisticsStorage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorListener
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.attachAsChildTo
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.progress.withProgressText
import com.intellij.util.concurrency.AppExecutorUtil
import com.sun.jdi.VMDisconnectedException
import kotlinx.coroutines.*
import kotlinx.coroutines.future.future
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.system.measureNanoTime

class DebuggerManagerThreadImpl @ApiStatus.Internal @JvmOverloads constructor(
  parent: Disposable,
  private val parentScope: CoroutineScope,
  debugProcess: DebugProcess? = null,
) : InvokeAndWaitThread<DebuggerCommandImpl?>(), DebuggerManagerThread, Disposable {

  @Volatile
  private var myDisposed = false

  internal val debuggerThreadDispatcher = DebuggerThreadDispatcher(this)
  private val myDebugProcess = WeakReference(debugProcess)
  internal val dispatchedCommandsCounter = AtomicInteger(0)

  @ApiStatus.Internal
  var coroutineScope: CoroutineScope = createScope()
    private set

  init {
    Disposer.register(parent, this)
  }

  override fun dispose() {
    myDisposed = true
  }

  @ApiStatus.Internal
  fun makeCancelable(
    project: Project,
    progressTitle: @ProgressTitle String,
    progressText: @Nls String,
    deferred: CompletableDeferred<Unit>,
    howToCancel: () -> Unit,
  ) {
    coroutineScope.launch {
      withBackgroundProgress(project, progressTitle) {
        withProgressText(progressText) {
          try {
            deferred.await()
          }
          catch (e: CancellationException) {
            howToCancel()
            throw e
          }
        }
      }
    }
  }

  private fun createScope() = parentScope.childScope("DebuggerManagerThreadImpl")

  override fun invokeAndWait(managerCommand: DebuggerCommandImpl) {
    LOG.assertTrue(!isManagerThread(), "Should be invoked outside manager thread, use DebuggerManagerThreadImpl.schedule(...)")
    super.invokeAndWait(managerCommand)
  }

  fun invokeNow(managerCommand: DebuggerCommandImpl) {
    assertIsManagerThread()
    LOG.assertTrue(currentThread() === this) { "invokeNow from a different DebuggerManagerThread" }
    setCommandManagerThread(managerCommand)
    processEvent(managerCommand)
  }

  @Deprecated("Use invokeNow if in DebuggerManagerThread or schedule otherwise",
              ReplaceWith("invokeNow(managerCommand)"))
  fun invoke(managerCommand: DebuggerCommandImpl) {
    if (currentThread() === this) {
      invokeNow(managerCommand)
    }
    else {
      if (isManagerThread()) {
        LOG.error("Schedule from a different DebuggerManagerThread")
      }
      schedule(managerCommand)
    }
  }

  @Deprecated("Use invokeNow if in DebuggerManagerThread or schedule otherwise",
              ReplaceWith("schedule(priority, runnable)"))
  fun invoke(priority: PrioritizedTask.Priority, runnable: Runnable) {
    invoke(object : DebuggerCommandImpl(priority) {
      override fun action() {
        runnable.run()
      }
    })
  }

  override fun pushBack(managerCommand: DebuggerCommandImpl): Boolean {
    val pushed = super.pushBack(managerCommand)
    if (!pushed) {
      managerCommand.notifyCancelled()
    }
    return pushed
  }

  fun schedule(priority: PrioritizedTask.Priority, runnable: Runnable) {
    schedule(object : DebuggerCommandImpl(priority) {
      override fun action() {
        runnable.run()
      }
    })
  }

  override fun schedule(managerCommand: DebuggerCommandImpl): Boolean {
    val scheduled = coroutineScope.isActive && super.schedule(managerCommand)
    if (!scheduled) {
      managerCommand.notifyCancelled()
    }
    return scheduled
  }

  /**
   * waits COMMAND_TIMEOUT milliseconds
   * if worker thread is still processing the same command
   * calls terminateCommand
   */
  fun terminateAndInvoke(command: DebuggerCommandImpl, terminateTimeoutMillis: Int) {
    val currentCommand = myEvents.currentEvent

    schedule(command)

    if (currentCommand != null) {
      AppExecutorUtil.getAppScheduledExecutorService().schedule(
        {
          if (currentCommand === myEvents.currentEvent) {
            // if current command is still in progress, cancel it
            val request = currentRequest
            request.requestStop()
            try {
              request.join()
            }
            catch (_: InterruptedException) {
            }
            catch (e: Exception) {
              throw RuntimeException(e)
            }
            finally {
              if (!myDisposed) {
                startNewWorkerThread()
              }
            }
          }
        }, terminateTimeoutMillis.toLong(), TimeUnit.MILLISECONDS)
    }
  }

  override fun processEvent(managerCommand: DebuggerCommandImpl) {
    assertIsManagerThread()
    val threadCommands = myCurrentCommands.get()
    threadCommands.add(managerCommand)
    try {
      if (myEvents.isClosed) {
        managerCommand.notifyCancelled()
      }
      else {
        val commandTimeNs = measureNanoTime {
          managerCommand.invokeCommand()
        }
        myDebugProcess.get()?.let { debugProcess ->
          val commandTimeMs = TimeUnit.NANOSECONDS.toMillis(commandTimeNs)
          StatisticsStorage.addCommandTime(debugProcess, commandTimeMs)
        }
      }
    }
    catch (e: VMDisconnectedException) {
      LOG.debug(e)
    }
    catch (e: RuntimeException) {
      throw e
    }
    catch (e: InterruptedException) {
      throw RuntimeException(e)
    }
    catch (e: Exception) {
      val unwrap = DebuggerUtilsAsync.unwrap(e)
      if (unwrap is InterruptedException) {
        throw RuntimeException(unwrap)
      }
      LOG.error(e)
    }
    finally {
      threadCommands.removeLast()
    }
  }

  fun startProgress(command: DebuggerCommandImpl, progressWindow: ProgressWindow) {
    object : ProgressIndicatorListener {
      override fun cancelled() {
        command.release()
      }
    }.installToProgress(progressWindow)

    ApplicationManager.getApplication().executeOnPooledThread {
      ProgressManager.getInstance().runProcess(
        { invokeAndWait(command) }, progressWindow)
    }
  }


  fun startLongProcessAndFork(process: Runnable) {
    assertIsManagerThread()
    startNewWorkerThread()

    try {
      process.run()
    }
    finally {
      val request = getCurrentThreadRequest()

      LOG.debug { "Switching back to $request" }

      var cancelled = false
      super.invokeAndWait(object : DebuggerCommandImpl() {
        override fun action() {
          switchToRequest(request)
        }

        override fun commandCancelled() {
          cancelled = true
          LOG.debug { "Event queue was closed, killing request $request" }
          request.requestStop()
        }
      })

      // the queue is already closed - we need to stop asap
      if (cancelled) {
        throw VMDisconnectedException()
      }
    }
  }

  override fun invokeCommand(command: DebuggerCommand) {
    if (command is SuspendContextCommand) {
      schedule(object : SuspendContextCommandImpl(command.suspendContext as SuspendContextImpl) {
        override fun contextAction(suspendContext: SuspendContextImpl) {
          command.action()
        }

        override fun commandCancelled() {
          command.commandCancelled()
        }
      })
    }
    else {
      schedule(object : DebuggerCommandImpl() {
        override fun action() {
          command.action()
        }

        override fun commandCancelled() {
          command.commandCancelled()
        }
      })
    }
  }

  /**
   * Indicates whether the debugger manager thread is currently idle.
   * This is determined by checking if there are no pending events.
   */
  @ApiStatus.Internal
  fun isIdle(): Boolean = myEvents.isEmpty && dispatchedCommandsCounter.get() == 0

  fun hasAsyncCommands(): Boolean {
    return myEvents.hasAsyncCommands()
  }

  @ApiStatus.Internal
  fun restartIfNeeded() {
    if (myEvents.isClosed) {
      myEvents.reopen()
      LOG.assertTrue(!coroutineScope.isActive, "Coroutine scope should be cancelled")
      coroutineScope = createScope()
      startNewWorkerThread()
    }
  }

  @ApiStatus.Internal
  fun cancelScope() {
    coroutineScope.cancel()
  }

  companion object {
    private val LOG = Logger.getInstance(DebuggerManagerThreadImpl::class.java)
    private val myCurrentCommands = ThreadLocal.withInitial { ArrayDeque<DebuggerCommandImpl>() }

    const val COMMAND_TIMEOUT: Int = 3000

    @JvmStatic
    @TestOnly
    fun createTestInstance(parent: Disposable, project: Project?): DebuggerManagerThreadImpl {
      var thread: DebuggerManagerThreadImpl? = null
      val disposable = Disposable {
        try {
          thread?.close()
        }
        catch (_: Exception) {
        }
        thread?.currentRequest?.join()
      }
      Disposer.register(parent, disposable)
      return DebuggerManagerThreadImpl(disposable, (project as ComponentManagerEx).getCoroutineScope())
        .also { thread = it }
    }

    @JvmStatic
    fun isManagerThread(): Boolean = currentThread() is DebuggerManagerThreadImpl

    @JvmStatic
    fun assertIsManagerThread() {
      LOG.assertTrue(isManagerThread(), "Should be invoked in manager thread, use DebuggerManagerThreadImpl.schedule(...)")
    }

    @JvmStatic
    fun getCurrentCommand(): DebuggerCommandImpl? = myCurrentCommands.get().peekLast()

    /**
     * Debugger thread runs in a progress indicator itself, so we need to check whether we have any other progress indicator additionally.
     */
    @ApiStatus.Internal
    fun hasNonDefaultProgressIndicator(): Boolean {
      val hasProgressIndicator = ProgressManager.getInstance().hasProgressIndicator()
      if (!hasProgressIndicator) return false
      if (!isManagerThread()) return true
      val currentIndicator = ProgressManager.getInstance().progressIndicator
      val debuggerIndicator = currentThread().currentRequest.progressIndicator
      return currentIndicator !== debuggerIndicator
    }
  }
}

private fun findCurrentContext(): Triple<DebuggerManagerThreadImpl, PrioritizedTask.Priority, SuspendContextImpl?> {
  DebuggerManagerThreadImpl.assertIsManagerThread()
  val managerThread = InvokeThread.currentThread() as DebuggerManagerThreadImpl
  val command = DebuggerManagerThreadImpl.getCurrentCommand()
  val priority = command?.priority ?: PrioritizedTask.Priority.LOW
  val suspendContext = (command as? SuspendContextCommandImpl)?.suspendContext
  return Triple(managerThread, priority, suspendContext)
}

/**
 * Executes [action] in debugger manager thread and returns a future.
 * **This method can only be called in debugger manager thread.**
 * Use [invokeCommandAsCompletableFuture] with [SuspendContextImpl] param if you are not.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun <T> invokeCommandAsCompletableFuture(block: suspend CoroutineScope.() -> T): CompletableFuture<T> {
  val (managerThread, priority, suspendContext) = findCurrentContext()
  val scope = suspendContext?.coroutineScope ?: managerThread.coroutineScope
  return scope.future {
    if (suspendContext != null) {
      withDebugContext(suspendContext, priority, block)
    }
    else {
      withDebugContext(managerThread, priority, block)
    }
  }
}

/**
 * Executes [block] in debugger manager thread as a [SuspendContextCommandImpl] and returns a future.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun <T> invokeCommandAsCompletableFuture(
  suspendContext: SuspendContextImpl,
  priority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW,
  block: suspend CoroutineScope.() -> T,
): CompletableFuture<T> = suspendContext.coroutineScope.future {
  withDebugContext(suspendContext, priority, block)
}

/**
 * Runs [block] in debugger manager thread as a [SuspendContextCommandImpl].
 *
 * This is similar to [DebuggerManagerThreadImpl.schedule] call.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun executeOnDMT(
  suspendContext: SuspendContextImpl,
  priority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW,
  block: suspend CoroutineScope.() -> Unit,
): Job {
  val managerThread = suspendContext.managerThread
  return withJobCommandTracking(managerThread) {
    val newContext = Dispatchers.Debugger(managerThread) + SuspendContextCommandProvider(suspendContext, priority)
    suspendContext.coroutineScope.launch(newContext, block = block)
  }
}

/**
 * Runs [block] in debugger manager thread as a [DebuggerCommandImpl].
 *
 * This is similar to [DebuggerManagerThreadImpl.schedule] call.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun executeOnDMT(
  managerThread: DebuggerManagerThreadImpl,
  priority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW,
  block: suspend CoroutineScope.() -> Unit,
): Job = withJobCommandTracking(managerThread) {
  val newContext = Dispatchers.Debugger(managerThread) + DebuggerCommandProvider(priority)
  managerThread.coroutineScope.launch(newContext, block = block)
}


/**
 * Runs [block] in debugger manager thread as a [com.intellij.debugger.engine.events.DebuggerContextCommandImpl].
 *
 * This is similar to [DebuggerManagerThreadImpl.schedule] call.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun executeOnDMT(
  debuggerContext: DebuggerContextImpl,
  priority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW,
  block: suspend CoroutineScope.() -> Unit,
): Job {
  val managerThread = debuggerContext.managerThread!!
  return withJobCommandTracking(managerThread) {
    val newContext = Dispatchers.Debugger(managerThread) + DebuggerContextCommandProvider(debuggerContext, priority)
    managerThread.coroutineScope.launch(newContext, block = block)
  }
}

/**
 * Runs [block] in debugger manager thread as a [SuspendContextCommandImpl].
 *
 * The coroutine is canceled if the corresponding command is canceled.
 *
 * This is similar to [withContext] call to switch to the debugger thread inside a coroutine.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
suspend fun <T> withDebugContext(
  suspendContext: SuspendContextImpl,
  priority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW,
  block: suspend CoroutineScope.() -> T,
): T {
  val managerThread = suspendContext.managerThread
  val newContext = Dispatchers.Debugger(managerThread) + SuspendContextCommandProvider(suspendContext, priority)
  return runWithContext(managerThread, newContext, suspendContext.coroutineScope, block)
}

/**
 * Runs [block] in debugger manager thread as a [DebuggerCommandImpl].
 *
 * The coroutine is canceled if the corresponding command is canceled.
 *
 * This is similar to [withContext] call to switch to the debugger thread inside a coroutine.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
suspend fun <T> withDebugContext(
  managerThread: DebuggerManagerThreadImpl,
  priority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW,
  block: suspend CoroutineScope.() -> T,
): T {
  val newContext = Dispatchers.Debugger(managerThread) + DebuggerCommandProvider(priority)
  return runWithContext(managerThread, newContext, managerThread.coroutineScope, block)
}

/**
 * Runs [block] in debugger manager thread as a [com.intellij.debugger.engine.events.DebuggerContextCommandImpl].
 *
 * The coroutine is canceled if the corresponding command is canceled.
 *
 * This is similar to [withContext] call to switch to the debugger thread inside a coroutine.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
suspend fun <T> withDebugContext(
  debuggerContext: DebuggerContextImpl,
  priority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW,
  block: suspend CoroutineScope.() -> T,
): T {
  val managerThread = debuggerContext.managerThread!!
  val newContext = Dispatchers.Debugger(managerThread) + DebuggerContextCommandProvider(debuggerContext, priority)
  return runWithContext(managerThread, newContext, managerThread.coroutineScope, block)
}

/**
 * Instead of calling [withContext], we use [async] with a following [Deferred.await],
 * because we have to always pass through [DebuggerThreadDispatcher], while [withContext] may not call [CoroutineDispatcher.dispatch],
 * as it is unnecessary within the same dispatcher.
 *
 * Due to [DebuggerThreadDispatcher] having a more complex contract than only thread switching, we need to ensure dispatching here.
 */
private suspend fun <T> runWithContext(
  managerThread: DebuggerManagerThreadImpl,
  context: CoroutineContext,
  parentScope: CoroutineScope,
  block: suspend CoroutineScope.() -> T,
): T = coroutineScope {
  // Ensure the job is canceled when the corresponding CoroutineScope is closed
  attachAsChildTo(parentScope)
  withJobCommandTracking(managerThread) {
    async(context, block = block)
  }.await()
}

private inline fun <T : Job> withJobCommandTracking(managerThread: DebuggerManagerThreadImpl, block: () -> T): T {
  managerThread.dispatchedCommandsCounter.incrementAndGet()
  val job = block()
  job.invokeOnCompletion { managerThread.dispatchedCommandsCounter.decrementAndGet() }
  return job
}

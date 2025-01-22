// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.engine.managerThread.DebuggerCommand
import com.intellij.debugger.engine.managerThread.DebuggerManagerThread
import com.intellij.debugger.engine.managerThread.SuspendContextCommand
import com.intellij.debugger.impl.*
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
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.progress.withProgressText
import com.intellij.util.concurrency.AppExecutorUtil
import com.sun.jdi.VMDisconnectedException
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class DebuggerManagerThreadImpl(parent: Disposable, private val parentScope: CoroutineScope) :
  InvokeAndWaitThread<DebuggerCommandImpl?>(), DebuggerManagerThread, Disposable {

  @Volatile
  private var myDisposed = false

  private val myDebuggerThreadDispatcher = DebuggerThreadDispatcher(this)

  /**
   * This set is used for testing purposes as it is the only way to check that there are any (possibly async) debugger commands.
   */
  @ApiStatus.Internal
  val unfinishedCommands: MutableSet<DebuggerCommandImpl> = ConcurrentCollectionFactory.createConcurrentSet<DebuggerCommandImpl>()

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
            currentRequest.requestStop()
            try {
              currentRequest.join()
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
    threadCommands.push(managerCommand)
    try {
      if (myEvents.isClosed) {
        managerCommand.notifyCancelled()
      }
      else {
        managerCommand.invokeCommand(myDebuggerThreadDispatcher, coroutineScope)
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
      threadCommands.pop()
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
   * This is determined by checking if there are no pending events
   * and no unfinished commands (other than the current one).
   */
  @ApiStatus.Internal
  fun isIdle(): Boolean {
    if (!myEvents.isEmpty) {
      return false
    }
    val currentCommand = getCurrentCommand()
    if (currentCommand != null) {
      return unfinishedCommands.singleOrNull() == currentCommand
    }
    else {
      return unfinishedCommands.isEmpty()
    }
  }

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
    private val myCurrentCommands = ThreadLocal.withInitial { LinkedList<DebuggerCommandImpl>() }

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
    fun getCurrentCommand(): DebuggerCommandImpl? = myCurrentCommands.get().peek()

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
 * Use [invokeCommandAsCompletableFuture] with managerThread param if you are not.
 *
 * Starts a [SuspendContextCommandImpl] if was in a [SuspendContextCommandImpl], else starts a [DebuggerCommandImpl].
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun <T> invokeCommandAsCompletableFuture(action: suspend () -> T): CompletableFuture<T> {
  val (managerThread, priority, suspendContext) = findCurrentContext()
  return invokeCommandAsCompletableFuture(managerThread, priority, suspendContext, action)
}

/**
 * Executes [action] in debugger manager thread as a [SuspendContextCommandImpl] and returns a future.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun <T> invokeCommandAsCompletableFuture(
  suspendContext: SuspendContextImpl,
  priority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW,
  action: suspend () -> T,
): CompletableFuture<T> = invokeCommandAsCompletableFuture(suspendContext.managerThread, priority, suspendContext, action)

/**
 * Executes [action] in debugger manager thread as a [DebuggerCommandImpl] thread and returns a future.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun <T> invokeCommandAsCompletableFuture(
  managerThread: DebuggerManagerThreadImpl,
  priority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW,
  action: suspend () -> T,
): CompletableFuture<T> = invokeCommandAsCompletableFuture(managerThread, priority, null, action)

private fun <T> invokeCommandAsCompletableFuture(
  managerThread: DebuggerManagerThreadImpl,
  priority: PrioritizedTask.Priority,
  suspendContext: SuspendContextImpl?,
  action: suspend () -> T,
): CompletableFuture<T> {
  val res = DebuggerCompletableFuture<T>()
  executeOnDMT(managerThread, priority, suspendContext, { res.cancel(false) }) {
    try {
      res.complete(action())
    }
    catch (e: Exception) {
      res.completeExceptionally(e)
    }
  }
  return res
}

/**
 * This call launches the coroutine [action].
 *
 * **This method can only be called in debugger manager thread.** Use [executeOnDMT] if you are not.
 *
 * Starts a [SuspendContextCommandImpl] if was in a [SuspendContextCommandImpl], else starts a [DebuggerCommandImpl].
 *
 * Pass [onCommandCancelled] to be notified if the command is canceled.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun launchInDebuggerCommand(
  onCommandCancelled: (() -> Unit)? = null,
  action: suspend () -> Unit,
) {
  val (managerThread, priority, suspendContext) = findCurrentContext()
  executeOnDMT(managerThread, priority, suspendContext, onCommandCancelled, action)
}

/**
 * Runs [action] in debugger manager thread as a [SuspendContextCommandImpl].
 * Pass [onCommandCancelled] to be notified if the command is canceled.
 *
 * This is similar to [DebuggerManagerThreadImpl.schedule] call.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun executeOnDMT(
  suspendContext: SuspendContextImpl,
  priority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW,
  onCommandCancelled: (() -> Unit)? = null,
  action: suspend () -> Unit,
): Unit = executeOnDMT(suspendContext.managerThread, priority, suspendContext, onCommandCancelled, action)

/**
 * Runs [action] in debugger manager thread as a [DebuggerCommandImpl].
 * Pass [onCommandCancelled] to be notified if the command is canceled.
 *
 * This is similar to [DebuggerManagerThreadImpl.schedule] call.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
fun executeOnDMT(
  managerThread: DebuggerManagerThreadImpl,
  priority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW,
  onCommandCancelled: (() -> Unit)? = null,
  action: suspend () -> Unit,
): Unit = executeOnDMT(managerThread, priority, null, onCommandCancelled, action)

private fun executeOnDMT(
  managerThread: DebuggerManagerThreadImpl,
  priority: PrioritizedTask.Priority,
  suspendContext: SuspendContextImpl? = null,
  onCommandCancelled: (() -> Unit)? = null,
  action: suspend () -> Unit,
) {
  val managerCommand = if (suspendContext != null) {
    object : SuspendContextCommandImpl(suspendContext) {
      override suspend fun contextActionSuspend(suspendContext: SuspendContextImpl) = action()
      override fun getPriority() = priority
      override fun commandCancelled() {
        onCommandCancelled?.invoke()
      }
    }
  }
  else {
    object : DebuggerCommandImpl(priority) {
      override suspend fun actionSuspend() = action()
      override fun commandCancelled() {
        onCommandCancelled?.invoke()
      }
    }
  }
  managerThread.schedule(managerCommand)
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
  block: suspend () -> T,
): T = withDebugContext(suspendContext.managerThread, priority, suspendContext, block)

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
  block: suspend () -> T,
): T = withDebugContext(managerThread, priority, null, block)

private suspend fun <T> withDebugContext(
  managerThread: DebuggerManagerThreadImpl,
  priority: PrioritizedTask.Priority,
  suspendContext: SuspendContextImpl?,
  block: suspend () -> T,
): T = if (managerThread === InvokeThread.currentThread()) {
  block()
}
else suspendCancellableCoroutine { continuation ->
  executeOnDMT(managerThread, priority, suspendContext,
               onCommandCancelled = { continuation.cancel() }
  ) {
    val result = try {
      Result.success(block())
    }
    catch (e: Throwable) {
      Result.failure(e)
    }
    continuation.resumeWith(result)
  }
}


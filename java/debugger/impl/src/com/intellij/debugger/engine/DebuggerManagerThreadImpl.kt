// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  val unfinishedCommands = ConcurrentCollectionFactory.createConcurrentSet<DebuggerCommandImpl>()

  @ApiStatus.Internal
  var coroutineScope = createScope()
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
    howToCancel: () -> Unit
  ) {
    coroutineScope.launch {
      withBackgroundProgress(project, progressTitle) {
        withProgressText(progressText) {
          try {
            deferred.await()
          } catch (e: CancellationException) {
            howToCancel()
            throw e
          }
        }
      }
    }
  }

  private fun createScope() = parentScope.childScope("DebuggerManagerThreadImpl")

  override fun invokeAndWait(managerCommand: DebuggerCommandImpl) {
    LOG.assertTrue(!isManagerThread(), "Should be invoked outside manager thread, use DebuggerManagerThreadImpl.getInstance(..).invoke...")
    super.invokeAndWait(managerCommand)
  }

  fun invoke(managerCommand: DebuggerCommandImpl) {
    if (currentThread() === this) {
      setCommandManagerThread(managerCommand);
      processEvent(managerCommand)
    }
    else {
      if (isManagerThread()) {
        LOG.error("Schedule from a different DebuggerManagerThread")
      }
      schedule(managerCommand)
    }
  }

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

    invoke(command)

    if (currentCommand != null) {
      AppExecutorUtil.getAppScheduledExecutorService().schedule(
        {
          if (currentCommand === myEvents.currentEvent) {
            // if current command is still in progress, cancel it
            currentRequest.requestStop()
            try {
              currentRequest.join()
            }
            catch (ignored: InterruptedException) {
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

      if (LOG.isDebugEnabled) {
        LOG.debug("Switching back to $request")
      }

      super.invokeAndWait(object : DebuggerCommandImpl() {
        override fun action() {
          switchToRequest(request)
        }

        override fun commandCancelled() {
          LOG.debug("Event queue was closed, killing request")
          request.requestStop()
        }
      })
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

  val isIdle: Boolean
    get() = myEvents.isEmpty

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
      LOG.assertTrue(isManagerThread(), "Should be invoked in manager thread, use DebuggerManagerThreadImpl.getInstance(..).invoke...")
    }

    @JvmStatic
    fun getCurrentCommand(): DebuggerCommandImpl? = myCurrentCommands.get().peek()
  }
}

@ApiStatus.Experimental
fun <T> invokeCommandAsCompletableFuture(action: suspend () -> T): CompletableFuture<T> {
  DebuggerManagerThreadImpl.assertIsManagerThread()
  val managerThread = InvokeThread.currentThread() as DebuggerManagerThreadImpl
  val command = DebuggerManagerThreadImpl.getCurrentCommand()
  val priority = command?.priority ?: PrioritizedTask.Priority.LOW
  val suspendContext = (command as? SuspendContextCommandImpl)?.suspendContext
  return invokeCommandAsCompletableFuture(managerThread, priority, suspendContext, action)
}

@ApiStatus.Experimental
fun <T> invokeCommandAsCompletableFuture(managerThread: DebuggerManagerThreadImpl,
                                         priority: PrioritizedTask.Priority = PrioritizedTask.Priority.LOW,
                                         suspendContext: SuspendContextImpl? = null,
                                         action: suspend () -> T): CompletableFuture<T> {
  val res = DebuggerCompletableFuture<T>()

  suspend fun doRun() {
    try {
      res.complete(action())
    }
    catch (e: Exception) {
      res.completeExceptionally(e)
    }
  }

  if (suspendContext != null) {
    managerThread.invoke(object : SuspendContextCommandImpl(suspendContext) {
      override suspend fun contextActionSuspend(suspendContext: SuspendContextImpl) = doRun()
      override fun getPriority() = priority
      override fun commandCancelled() {
        res.cancel(false)
      }
    })
  }
  else {
    managerThread.invoke(object : DebuggerCommandImpl(priority) {
      override suspend fun actionSuspend() = doRun()
      override fun commandCancelled() {
        res.cancel(false)
      }
    })
  }

  return res
}


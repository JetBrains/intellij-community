// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.PrioritizedTask
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * The dispatcher that allows working with the debugger manager thread with coroutines.
 *
 * Every task dispatched to this dispatcher is scheduled to the debugger manager thread.
 * The type of the task is selected based on the [DebuggerDispatchedCommandProvider] that should be present in the [CoroutineContext]
 * when a task is dispatched.
 *
 * N.B. To ensure the correct switching between different commands type, the tasks must pass the [dispatch] method,
 * see [runWithContext] method for details.
 *
 * @see withDebugContext
 * @see executeOnDMT
 */
internal class DebuggerThreadDispatcher(private val managerThread: DebuggerManagerThreadImpl) : CoroutineDispatcher() {
  /**
   * The counter that helps to understand whether any task is going to be scheduled to DMT.
   *
   * Every [Job] started with this dispatcher should be taken into account by this counter.
   */
  internal val dispatchedCommandsCounter = AtomicInteger()

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    val debuggerCommand = createCommand(context, block)
    val job = context[Job]
    if (job != null) {
      dispatchedCommandsCounter.incrementAndGet()
      job.invokeOnCompletion {
        dispatchedCommandsCounter.decrementAndGet()
      }
    }
    managerThread.schedule(debuggerCommand)
  }

  private fun createCommand(context: CoroutineContext, block: Runnable): DebuggerCommandImpl {
    val provider = context[DebuggerDispatchedCommandProvider.Key] ?: error("No ${DebuggerDispatchedCommandProvider.Key} in context")
    return provider.createDebuggerCommand(block, cancellationAction = {
      context.cancel()
      // By the contract of the CoroutineDispatcher, the passed block must be eventually executed.
      // However, the debugger commands might be canceled before execution due to context changes (e.g., suspend context might be resumed).
      // To ensure the contract, we have to call `run()`, but we guarantee that the scheduled task is not executed
      // by cancelling the corresponding Job first.
      block.run()
    })
  }
}

/**
 * Access method for the debugger dispatcher.
 *
 * It is intentionally kept internal due to several causes:
 * 1. Limited number of use-cases for switching to DMT.
 * Considering the existing needs, the currently provided API should be enough.
 * 2. Tiding execution to the debugger's [CoroutineScope]s. The provided API ensures job cancellation by attaching to
 * [DebuggerManagerThreadImpl.coroutineScope] or [SuspendContextImpl.getCoroutineScope].
 * With a publicly available dispatcher, this should be ensured by the API users.
 * 3. Switching between different command types. The provided API ensures correct switching between the debugger commands.
 * With a publicly available dispatcher, the current solution should be reconsidered.
 */
@Suppress("FunctionName", "UnusedReceiverParameter")
internal fun Dispatchers.Debugger(managerThread: DebuggerManagerThreadImpl): CoroutineDispatcher = managerThread.debuggerThreadDispatcher

/**
 * Debugger command factory passed to the [DebuggerThreadDispatcher] for correct command scheduling.
 */
internal sealed class DebuggerDispatchedCommandProvider(val priority: PrioritizedTask.Priority) : CoroutineContext.Element {
  abstract fun createDebuggerCommand(block: Runnable, cancellationAction: (() -> Unit)): DebuggerCommandImpl
  override val key: CoroutineContext.Key<*> get() = Key

  companion object Key : CoroutineContext.Key<DebuggerDispatchedCommandProvider>
}

internal class DebuggerCommandProvider(priority: PrioritizedTask.Priority) : DebuggerDispatchedCommandProvider(priority) {
  override fun createDebuggerCommand(block: Runnable, cancellationAction: (() -> Unit)): DebuggerCommandImpl =
    object : DebuggerCommandImpl(priority) {
      override fun action() = block.run()
      override fun commandCancelled() = cancellationAction()
    }
}

internal class SuspendContextCommandProvider(
  val suspendContext: SuspendContextImpl,
  priority: PrioritizedTask.Priority,
) : DebuggerDispatchedCommandProvider(priority) {
  override fun createDebuggerCommand(block: Runnable, cancellationAction: () -> Unit): DebuggerCommandImpl =
    object : SuspendContextCommandImpl(suspendContext) {
      override val priority: PrioritizedTask.Priority get() = this@SuspendContextCommandProvider.priority
      override fun contextAction(suspendContext: SuspendContextImpl) = block.run()
      override fun commandCancelled() = cancellationAction()
    }
}

internal class DebuggerContextCommandProvider(
  val debuggerContext: DebuggerContextImpl,
  priority: PrioritizedTask.Priority,
) : DebuggerDispatchedCommandProvider(priority) {

  private val command = object : DebuggerContextCommandImpl(debuggerContext) {
    lateinit var block: Runnable
    lateinit var cancellationAction: () -> Unit

    override val priority: PrioritizedTask.Priority get() = this@DebuggerContextCommandProvider.priority
    override fun threadAction(suspendContext: SuspendContextImpl) = block.run()
    override fun commandCancelled() = cancellationAction()
  }

  /**
   * Finds [CoroutineScope] of the [SuspendContextImpl] that will be used for this command
   */
  suspend fun findScope(): CoroutineScope? {
    // Possible commands reorder due to additional switch to DMT
    return withDebugContext(debuggerContext.managerThread!!) {
      command.suspendContext?.coroutineScope
    }
  }

  override fun createDebuggerCommand(block: Runnable, cancellationAction: () -> Unit): DebuggerCommandImpl {
    command.block = block
    command.cancellationAction = cancellationAction
    return command
  }
}

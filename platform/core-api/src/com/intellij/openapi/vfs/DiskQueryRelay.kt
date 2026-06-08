// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.concurrency.installThreadContext
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.isInCancellableContext
import com.intellij.openapi.progress.util.awaitWithCheckCanceled
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ExceptionUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function

/**
 * A utility to run a potentially long function on a pooled thread, wait for it in an interruptible way,
 * and reuse that computation if it's necessary again if it's still running.
 * Function results should be ready for concurrent access, preferably thread-safe.
 *
 *
 * To avoid deadlocks, please pay attention to locks held at the call time and try to abstain from taking locks
 * inside the `function` block.
 *
 *
 * An instance of the class should be used for performing multiple similar operations;
 * for one-shot tasks, [.compute] is simpler to use.
 */
class DiskQueryRelay<Param : Any, Result>(function: Function<in Param, out Result>, private val executor: ExecutorService) {
  constructor(function: Function<in Param, out Result>) : this(function, ProcessIOExecutorService.INSTANCE)

  private val function: Function<in Param, out Result> = Function { arg: Param ->
    val startedAtNs = System.nanoTime()
    try {
      return@Function function.apply(arg)
    }
    finally {
      val elapsedNs = System.nanoTime() - startedAtNs
      taskExecutionTotalTimeNs.addAndGet(elapsedNs)
      tasksExecutedCount.incrementAndGet()
    }
  }

  /**
   * We remember the submitted tasks in "myTasks" until they're finished, to avoid creating many-many similar threads
   * in case the callee is interrupted by "checkCanceled", restarted, comes again with the same query, is interrupted again, and so on.
   */
  private val myTasks: MutableMap<Param, Future<Result>> = ConcurrentHashMap<Param, Future<Result>>()

  fun accessDiskWithCheckCanceled(arg: Param): Result {
    val startedAtNs = System.nanoTime()
    try {
      if (!isInCancellableContext()) {
        return function.apply(arg)
      }
      val future = myTasks.computeIfAbsent(arg) { eachArg: Param ->
        executor.submit(Callable {
          installThreadContext(coroutineScope.coroutineContext, true) {
            try {
              function.apply(eachArg)
            }
            finally {
              myTasks.remove(eachArg)
            }
          }
        })
      }
      if (future.isDone) {
        // maybe it was very fast and completed before being put into a map
        myTasks.remove(arg, future)
      }
      return future.awaitWithCheckCanceled()
    }
    finally {
      val elapsedNs = System.nanoTime() - startedAtNs
      taskWaitingTotalTimeNs.addAndGet(elapsedNs)
      tasksRequestedCount.incrementAndGet()
    }
  }

  @ApiStatus.Internal
  companion object {
    /**
     * Use the method for one-shot tasks; for performing multiple similar operations, prefer an instance of the class.
     *
     * To avoid deadlocks, please pay attention to locks held at the call time and try to abstain from taking locks
     * inside the `task` block.
     */
    @JvmStatic
    @Throws(ProcessCanceledException::class)
    fun <Result, E : Exception> compute(task: ThrowableComputable<Result, E>): Result = compute(task, ProcessIOExecutorService.INSTANCE)

    /**
     * Use the method for one-shot tasks; for performing multiple similar operations, prefer an instance of the class.
     *
     * To avoid deadlocks, please pay attention to locks held at the call time and try to abstain from taking locks
     * inside the `task` block.
     */
    @JvmStatic
    @Throws(ProcessCanceledException::class)
    fun <Result, E : Exception> compute(task: ThrowableComputable<Result, E>, executor: ExecutorService): Result {
      if (!isInCancellableContext()) {
        return task.compute()
      }

      val future: Future<Result> = executor.submit(Callable { task.compute() })
      try {
        return future.awaitWithCheckCanceled()
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (wrapper: RuntimeException) {
        val outerCause = wrapper.cause
        if (outerCause is ExecutionException) {
          val t = outerCause.cause
          if (t != null) {
            ExceptionUtil.rethrowUnchecked(t)
            throw t
          }
        }
        throw wrapper
      }
      finally {
        // Better .cancel(true) here, but thread interruption is too intrusive, so it is cheaper
        // to allow the task to uselessly finish than to safely deal with thread interruption
        // everywhere (see IDEA-319309)
        future.cancel(false)
      }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val coroutineScope: CoroutineScope
      get() = try {
        service<DiskQueryRelayCoroutineScope>().scope
      }
      catch (_: Exception) {
        // In some rare cases, there is no coroutine scope available within the Application.
        // Fallback to GlobalScope
        GlobalScope
      }

    // ==================================== monitoring: ====================================================== //
    /** total time (since app start) of actual task executions, ns  */
    private val taskExecutionTotalTimeNs = AtomicLong()

    /** total time (since app start) spent waiting for the task result, ns  */
    private val taskWaitingTotalTimeNs = AtomicLong()

    /** total (since app start) number of tasks actually executed  */
    private val tasksExecutedCount = AtomicInteger()

    /** Total (since app start) number of tasks requested. Could be <= tasksExecuted because of task coalescing  */
    private val tasksRequestedCount = AtomicInteger()

    @ApiStatus.Internal
    fun taskExecutionTotalTime(unit: TimeUnit): Long = unit.convert(taskExecutionTotalTimeNs.get(), TimeUnit.NANOSECONDS)

    @ApiStatus.Internal
    fun taskWaitingTotalTime(unit: TimeUnit): Long = unit.convert(taskWaitingTotalTimeNs.get(), TimeUnit.NANOSECONDS)

    @ApiStatus.Internal
    fun tasksExecuted(): Int = tasksExecutedCount.get()

    @ApiStatus.Internal
    fun tasksRequested(): Int = tasksRequestedCount.get()
  }

  @Service(Service.Level.APP)
  private class DiskQueryRelayCoroutineScope(val scope: CoroutineScope)
}

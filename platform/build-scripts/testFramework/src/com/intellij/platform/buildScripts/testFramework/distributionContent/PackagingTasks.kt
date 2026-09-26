package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.buildScripts.concurrency.Awaitable
import com.intellij.platform.buildScripts.concurrency.TaskContext
import com.intellij.platform.buildScripts.concurrency.TaskFailedException
import com.intellij.platform.buildScripts.concurrency.TaskScope
import com.intellij.platform.buildScripts.concurrency.TaskSignal
import com.sun.management.HotSpotDiagnosticMXBean
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Registers every worker on the supervisor thread before it joins the scope. */
internal class PackagingTasks(private val scope: TaskScope, val diagnostics: PackagingSuiteHangDiagnostics) {
  fun <T> task(name: String = "packaging task", startImmediately: Boolean = false, block: () -> T): PackagingTaskHandle<T> {
    val start = TaskSignal<Unit>("start $name")
    val result = TaskSignal<T>(name)
    val context = AtomicReference<TaskContext?>()
    val state = AtomicReference("waiting for start")
    val handle = PackagingTaskHandle(start, result, name, state, context)
    diagnostics.register(handle)
    val subtask = scope.fork(name) {
      context.set(this)
      try {
        start.await()
        checkCancelled()
        state.set("running")
        result.complete(block())
        state.set("succeeded")
      }
      catch (failure: Throwable) {
        state.set(if (isCancelled || failure is InterruptedException) "cancelled" else "failed")
        result.fail(failure)
        throw failure
      }
      finally {
        context.set(null)
      }
    }
    subtask.onCompletion { failure ->
      if (failure != null && !result.isDone) {
        state.set("cancelled")
        result.fail(if (failure is TaskFailedException) failure.cause ?: failure else failure)
      }
    }
    if (startImmediately) handle.start()
    return handle
  }
}

/** Starting a handle opens its demand gate. A separate scheduling gate can still delay the work. */
internal class PackagingTaskHandle<T>(
  private val startSignal: TaskSignal<Unit>,
  private val result: TaskSignal<T>,
  private val name: String,
  private val state: AtomicReference<String>,
  private val context: AtomicReference<TaskContext?>,
) : Awaitable<T> {
  val isDone: Boolean get() = result.isDone

  fun start(): Boolean = startSignal.complete(Unit)

  override fun await(timeout: Duration?): T {
    start()
    return result.await(timeout)
  }

  fun onCompletion(action: () -> Unit) {
    result.onCompletion(action)
  }

  fun describe(): String = "$name: ${state.get()}${context.get()?.waitingFor?.let { "; awaiting $it" }.orEmpty()}"
}

internal class PackagingSuiteHangDiagnostics {
  private val tasks = CopyOnWriteArrayList<PackagingTaskHandle<*>>()

  fun register(task: PackagingTaskHandle<*>) {
    tasks.add(task)
  }

  fun describe(): String = tasks.joinToString("\n") { it.describe() }
}

/** Reports slow waits without treating idle workers as proof of a deadlock. */
internal fun <T> awaitOnTestThread(
  what: String,
  diagnostics: PackagingSuiteHangDiagnostics = PackagingSuiteHangDiagnostics(),
  dumpDelay: Duration = 10.minutes,
  probeInterval: Duration = 1.minutes,
  report: (String) -> Unit = System.err::println,
  block: () -> T,
): T {
  val watchdog = Thread.ofPlatform().daemon().name("packaging suite watchdog").unstarted {
    try {
      Thread.sleep(dumpDelay.inWholeMilliseconds.coerceAtLeast(1))
      while (!Thread.currentThread().isInterrupted) {
        report("Packaging suite: waiting for $what\n${diagnostics.describe()}\n${dumpPackagingThreads()}")
        Thread.sleep(probeInterval.inWholeMilliseconds.coerceAtLeast(1))
      }
    }
    catch (_: InterruptedException) {
    }
  }
  watchdog.start()
  try {
    return block()
  }
  finally {
    watchdog.interrupt()
    awaitThreadTermination(watchdog)
  }
}

internal fun awaitThreadTermination(thread: Thread) {
  check(thread !== Thread.currentThread()) { "A thread cannot join itself" }
  var interrupted = Thread.interrupted()
  try {
    while (thread.isAlive) {
      try {
        thread.join()
      }
      catch (_: InterruptedException) {
        interrupted = true
      }
    }
  }
  finally {
    if (interrupted) Thread.currentThread().interrupt()
  }
}

private fun dumpPackagingThreads(): String {
  val path = Files.createTempFile("packaging-threads", ".txt")
  return try {
    Files.delete(path)
    ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean::class.java)
      .dumpThreads(path.toString(), HotSpotDiagnosticMXBean.ThreadDumpFormat.TEXT_PLAIN)
    Files.readString(path)
  }
  catch (failure: Exception) {
    "Cannot collect the thread dump: $failure"
  }
  finally {
    Files.deleteIfExists(path)
  }
}

internal fun <T : Any> startRemainingTasksWithRollingReplenishment(
  startedTasks: Collection<T>,
  remainingTasks: Collection<T>,
  getCompletion: (T) -> PackagingTaskHandle<*>,
  startTask: (T) -> Unit,
) {
  if (startedTasks.isEmpty()) {
    remainingTasks.forEach(startTask)
    return
  }
  val limit = maxOf(startedTasks.size, remainingTasks.size)
  if (limit == 0) return
  val completed = LinkedBlockingQueue<T>()
  val active = LinkedHashSet<T>()
  val remaining = ArrayDeque(remainingTasks)
  fun observe(task: T) {
    active.add(task)
    getCompletion(task).onCompletion { completed.add(task) }
  }
  for (task in startedTasks) observe(task)
  fun fill() {
    while (active.size < limit && remaining.isNotEmpty()) {
      val task = remaining.removeFirst()
      observe(task)
      startTask(task)
    }
  }
  if (active.isEmpty()) fill()
  while (remaining.isNotEmpty()) {
    active.remove(completed.take())
    fill()
  }
}

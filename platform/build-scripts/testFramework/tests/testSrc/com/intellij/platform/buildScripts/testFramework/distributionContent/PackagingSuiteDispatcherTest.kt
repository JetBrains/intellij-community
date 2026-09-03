// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * `Dispatchers.Default` can drop a task. A worker puts a CPU task in its local queue, takes a blocking task and gives
 * up its CPU permit, cannot get the permit back while the other workers are busy, parks, and terminates after the
 * keep-alive. `CoroutineScheduler.tryTerminateWorker` moves the local queue nowhere, so the queued task is gone.
 * [createPackagingSuiteDispatcher] works around it with a virtual thread per resume.
 *
 * The first test reproduces the defect. It proves the loss by state, and not by a clock. The worker that owns the
 * queue is dead, a task dispatched after that death runs at once, and the queued task never started. A test that only
 * waits proves nothing, because a task that is late looks the same as a task that is gone.
 *
 * The trap needs a two-worker scheduler with a short keep-alive, so the first test needs these VM options:
 * `-Dkotlinx.coroutines.scheduler.core.pool.size=2 -Dkotlinx.coroutines.scheduler.keep.alive.sec=1`.
 * The `jps_test` rule of this module sets them. The test skips itself without them, and on TeamCity.
 */
class PackagingSuiteDispatcherTest {
  @Test
  fun `the kotlinx scheduler drops a task of a worker that terminates while idle`() {
    assumeTrue(System.getenv("TEAMCITY_VERSION") == null, "the test holds both CPU permits of the JVM, so it runs locally only")
    val corePoolSize = System.getProperty(CORE_POOL_SIZE_PROPERTY)?.toIntOrNull()
    val keepAliveSec = System.getProperty(KEEP_ALIVE_PROPERTY)?.toLongOrNull()
    assumeTrue(
      corePoolSize == 2 && keepAliveSec != null && keepAliveSec <= 2,
      "the test needs -D$CORE_POOL_SIZE_PROPERTY=2 and -D$KEEP_ALIVE_PROPERTY=1",
    )

    val reports = reproduceLostTask(cpu = Dispatchers.Default)
    println(reports.joinToString(separator = "\n") { "${it.outcome}: ${it.detail}" })
    assertThat(reports.map { it.outcome })
      .describedAs("no iteration lost a task, and the report above tells why")
      .contains(Outcome.LOST)
  }

  /**
   * The fixture dispatcher runs the same pattern. It has no CPU permit and no worker queue, so the child always runs.
   *
   * This test needs no VM option, because the pattern does not depend on the pool size here.
   */
  @Test
  fun `the fixture dispatcher runs a task that a worker queued before it blocked`() {
    createPackagingSuiteDispatcher().use { cpu ->
      val reports = reproduceLostTask(cpu = cpu, iterations = FIXTURE_ITERATIONS)
      assertThat(reports.map { it.outcome })
        .describedAs("the report is %s", reports.joinToString(separator = "\n") { "${it.outcome}: ${it.detail}" })
        .containsOnly(Outcome.KEPT)
    }
  }
}

/**
 * How many times to set the trap on the kotlinx scheduler. The run stops at the first loss.
 *
 * A single iteration springs the trap about half of the time. Another worker can steal the child, or the worker of
 * the victim can stay alive. A measurement over 20 runs gave 18 losses in 40 iterations, so 5 retries left one run in
 * ten with no loss at all. This budget makes that outcome rare, and it costs nothing when the first iteration wins.
 */
private const val ITERATIONS = 20

/** The fixture dispatcher cannot lose the task, so a couple of iterations are enough. */
private const val FIXTURE_ITERATIONS = 2

private const val CORE_POOL_SIZE_PROPERTY = "kotlinx.coroutines.scheduler.core.pool.size"
private const val KEEP_ALIVE_PROPERTY = "kotlinx.coroutines.scheduler.keep.alive.sec"

/** How long the two spinners hold both CPU permits. It must exceed the one second keep-alive of an idle worker. */
private val SPIN = 2500.milliseconds

/** How long the victim stays in the blocking task. It must outlast the start of both spinners. */
private val BLOCKING_TASK = 300.milliseconds

/**
 * How long to wait for the worker of the victim to terminate. A worker that is still alive did not fall in the trap.
 *
 * The wait is a blocking `Thread.join`, so [RUN_LIMIT] cannot cancel it. This bound keeps it finite.
 */
private val WORKER_DEATH_LIMIT = 10.seconds

/** How long to wait for a task that is dispatched after the death of the worker. An idle scheduler runs it at once. */
private val PROBE_LIMIT = 10.seconds

/**
 * How long to wait after the probe, before the run calls the child lost.
 *
 * This is a safety margin and not the verdict. It can only turn a loss into a pass, never the reverse.
 */
private val SETTLE = 200.milliseconds

/** The bound of one whole run. It only guards against a shape that the outcome checks do not expect. */
private val RUN_LIMIT = 180.seconds

private enum class Outcome {
  /** The worker died, the scheduler answered a later task, and the child never started. */
  LOST,

  /** The child ran. */
  KEPT,

  /** The iteration did not set the trap, so it says nothing about the defect. */
  NO_TRAP,
}

private class IterationReport(@JvmField val outcome: Outcome, @JvmField val detail: String)

/**
 * The worker that owns the local queue with the child.
 *
 * The name is captured while the worker still runs, because kotlinx renames a worker after its state. A dead worker
 * is called `DefaultDispatcher-worker-TERMINATED`, which no longer tells which worker it was.
 */
private class VictimWorker(@JvmField val thread: Thread, @JvmField val name: String)

/**
 * Runs the pattern that loses a task, and stops at the first loss.
 *
 * The child is not a child of the iteration scope, because a lost task would otherwise keep the scope open forever.
 */
private fun reproduceLostTask(cpu: CoroutineDispatcher, iterations: Int = ITERATIONS): List<IterationReport> {
  @Suppress("RAW_SCOPE_CREATION")
  val detached = CoroutineScope(SupervisorJob() + cpu)
  try {
    val reports = ArrayList<IterationReport>()
    runBlocking {
      withTimeout(RUN_LIMIT) {
        repeat(iterations) {
          val report = runIteration(cpu = cpu, detached = detached)
          reports.add(report)
          if (report.outcome == Outcome.LOST) {
            return@withTimeout
          }
        }
      }
    }
    return reports
  }
  finally {
    detached.cancel()
  }
}

/**
 * Sets the trap once and reports what happened.
 *
 * The victim captures its worker, puts the child in the local queue of that worker, and then takes a blocking task.
 * The two spinners start only after the child is in the queue, so the setup does not race. They hold both CPU permits
 * past the keep-alive, so the worker of the victim parks and terminates with the child still in its local queue.
 */
private suspend fun runIteration(cpu: CoroutineDispatcher, detached: CoroutineScope): IterationReport {
  val childStarted = AtomicBoolean()
  val queued = CompletableDeferred<VictimWorker>()

  val child: Deferred<Unit> = coroutineScope {
    val victim = async(cpu) {
      val child = detached.async { childStarted.set(true) }
      queued.complete(VictimWorker(thread = Thread.currentThread(), name = Thread.currentThread().name))
      withContext(Dispatchers.IO) { Thread.sleep(BLOCKING_TASK.inWholeMilliseconds) }
      child
    }
    queued.await()
    repeat(2) {
      launch(cpu) { spinFor(SPIN) }
    }
    victim.await()
  }

  val worker = queued.await()
  worker.thread.join(WORKER_DEATH_LIMIT.inWholeMilliseconds)
  if (worker.thread.isAlive) {
    if (child.isCompleted) {
      return IterationReport(Outcome.KEPT, "the child ran on a worker that stays alive")
    }
    return IterationReport(Outcome.NO_TRAP, "the worker ${worker.name} is alive, so it did not terminate while idle")
  }

  val probeThread = withTimeoutOrNull(PROBE_LIMIT) { withContext(cpu) { Thread.currentThread().name } }
  if (probeThread == null) {
    return IterationReport(Outcome.NO_TRAP, "the scheduler did not answer a probe task, so it is busy and the child is only late")
  }
  delay(SETTLE)

  if (childStarted.get()) {
    // Another worker can steal the child before the owner terminates, so one kept child does not mean the defect is gone.
    return IterationReport(Outcome.KEPT, "the child ran, so nothing dropped it")
  }
  return IterationReport(
    Outcome.LOST,
    "the worker ${worker.name} is dead, the probe ran on $probeThread, and the child never started",
  )
}

private fun spinFor(duration: Duration) {
  val end = System.nanoTime() + duration.inWholeNanoseconds
  while (System.nanoTime() < end) {
    Thread.onSpinWait()
  }
}

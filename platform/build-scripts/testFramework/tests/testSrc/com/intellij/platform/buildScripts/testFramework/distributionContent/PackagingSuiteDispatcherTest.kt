// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.Closeable
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The kotlinx scheduler can drop a task. A worker queues a CPU task in its local queue, takes a blocking task and gives up
 * its CPU permit, cannot get the permit back while the other workers are busy, parks, and terminates after the keep-alive
 * without moving its local queue anywhere. The fixture dispatcher works around it with a virtual thread per resume.
 *
 * The first test reproduces the defect on a private scheduler with a short keep-alive. When it fails, the scheduler keeps
 * the queue of a terminating worker, and the fixture can return to `Dispatchers.Default`.
 */
class PackagingSuiteDispatcherTest {
  @Test
  fun `the kotlinx scheduler drops a task of a worker that terminates while idle`() {
    val lost = KotlinxScheduler(corePoolSize = 2, keepAlive = 5.milliseconds).use {
      countLostTasks(cpu = it.cpuDispatcher, blocking = it.blockingDispatcher)
    }
    println("The kotlinx scheduler lost a task in $lost of $ITERATIONS iterations")
    assertThat(lost)
      .describedAs("every task ran, so the scheduler keeps the local queue of a terminating worker now")
      .isPositive()
  }

  @Test
  fun `the fixture dispatcher runs every task under the same load`() {
    val lost = createPackagingSuiteDispatcher().use {
      countLostTasks(cpu = it, blocking = Dispatchers.IO)
    }
    assertThat(lost).isZero()
  }
}

private const val ITERATIONS = 10

/**
 * Runs the pattern that loses a task on the kotlinx scheduler, and counts the iterations whose child task never ran.
 *
 * The victim queues a child on its own worker, then hands that worker a blocking task. Two spinning coroutines hold both
 * CPU permits past the keep-alive of the victim's worker. The child is not a child of the iteration scope, because a lost
 * task would otherwise keep the scope open forever.
 */
private fun countLostTasks(cpu: CoroutineDispatcher, blocking: CoroutineDispatcher): Int {
  @Suppress("RAW_SCOPE_CREATION")
  val detached = CoroutineScope(SupervisorJob() + cpu)
  try {
    var lost = 0
    runBlocking {
      repeat(ITERATIONS) {
        val finished = withTimeoutOrNull(1.seconds) {
          coroutineScope {
            val victim = async(cpu) {
              val child = detached.async { 1 }
              withContext(blocking) { Thread.sleep(5) }
              child.await()
            }
            repeat(2) {
              launch(cpu) { spinFor(50.milliseconds) }
            }
            victim.await()
          }
        }
        if (finished == null) {
          lost++
        }
      }
    }
    return lost
  }
  finally {
    detached.cancel()
  }
}

private fun spinFor(duration: Duration) {
  val end = System.nanoTime() + duration.inWholeNanoseconds
  while (System.nanoTime() < end) {
    Thread.onSpinWait()
  }
}

/**
 * A private `kotlinx.coroutines.scheduling.CoroutineScheduler`, so the test controls the pool size and the keep-alive.
 *
 * The class is internal to the library, so reflection creates it. Its `dispatch(Runnable, Boolean, Boolean)` takes the
 * task kind as the second argument: `true` is a blocking task.
 */
private class KotlinxScheduler(corePoolSize: Int, keepAlive: Duration) : Closeable {
  private val scheduler: Closeable
  val cpuDispatcher: CoroutineDispatcher
  val blockingDispatcher: CoroutineDispatcher

  init {
    val schedulerClass = Class.forName("kotlinx.coroutines.scheduling.CoroutineScheduler")
    scheduler = schedulerClass
      .getConstructor(Int::class.java, Int::class.java, Long::class.java, String::class.java)
      .newInstance(corePoolSize, corePoolSize * 8, keepAlive.inWholeNanoseconds, "test-scheduler") as Closeable
    cpuDispatcher = (scheduler as Executor).asCoroutineDispatcher()
    val dispatch = schedulerClass.getMethod("dispatch", Runnable::class.java, Boolean::class.java, Boolean::class.java)
    blockingDispatcher = object : CoroutineDispatcher() {
      override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatch.invoke(scheduler, block, true, false)
      }
    }
  }

  override fun close() {
    scheduler.close()
  }
}

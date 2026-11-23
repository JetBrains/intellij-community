// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.SystemProperties
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

/**
 * The wait task of [FileBasedIndexTumbler] ends on a cancel of the dumb queue and on a dispose of the children of the project.
 * The dumb queue runs the task on a background thread only with [DumbServiceImpl.IDEA_FORCE_DUMB_QUEUE_TASKS].
 * Without the property the task returns at once, and the cases test nothing.
 */
@TestApplication
@Timeout(30)
class FileBasedIndexTumblerTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val project by projectFixture

  companion object {
    private var previousForceDumbQueueTasks: String? = null
    private var previousIgnoreHeadless: String? = null

    @BeforeAll
    @JvmStatic
    fun forceDumbQueueTasks() {
      previousForceDumbQueueTasks = System.setProperty(DumbServiceImpl.IDEA_FORCE_DUMB_QUEUE_TASKS, "true")
      previousIgnoreHeadless = System.setProperty("intellij.progress.task.ignoreHeadless", "true")
    }

    @AfterAll
    @JvmStatic
    fun restoreDumbQueueTasks() {
      SystemProperties.setProperty(DumbServiceImpl.IDEA_FORCE_DUMB_QUEUE_TASKS, previousForceDumbQueueTasks)
      SystemProperties.setProperty("intellij.progress.task.ignoreHeadless", previousIgnoreHeadless)
    }
  }

  @Test
  fun `cancelAllTasksAndWait ends the wait task`() {
    withIndexOff {
      runInEdtAndWait {
        WriteIntentReadAction.run { DumbService.getInstance(project).cancelAllTasksAndWait() }
      }
      waitUntil("The wait task of the tumbler still runs after cancelAllTasksAndWait", seconds = 5) { !isWaitTaskRunning() }
    }
  }

  @Test
  fun `disposing the wait task of the project ends dumb mode without turnOn`() {
    withIndexOff {
      runInEdtAndWait {
        // The unload disposes the children of the project by their class loader. One loader holds every class here, so the name stands for it.
        Disposer.disposeChildren(project) { it.javaClass.name.startsWith(FileBasedIndexTumbler::class.java.name) }
      }
      waitUntil("The project stays dumb after the dispose of the wait task", seconds = 10) { !isDumb() }
      waitUntil("The wait task of the tumbler still runs after its dispose", seconds = 5) { !isWaitTaskRunning() }
    }
  }

  @Test
  @RegistryKey(key = "run.index.rescanning.on.plugin.load.unload", value = "false")
  fun `index invalidation while index is off starts rescan`() {
    var rescanStarted = false
    val previousForceDumbQueueTasks = System.getProperty(DumbServiceImpl.IDEA_FORCE_DUMB_QUEUE_TASKS)
    try {
      withIndexOff(beforeIndexTasksStarted = { rescanStarted = true }) {
        FileBasedIndex.getInstance().invalidateCaches()
        System.setProperty(DumbServiceImpl.IDEA_FORCE_DUMB_QUEUE_TASKS, "false")
      }
    }
    finally {
      SystemProperties.setProperty(DumbServiceImpl.IDEA_FORCE_DUMB_QUEUE_TASKS, previousForceDumbQueueTasks)
    }
    assertTrue(rescanStarted, "Index invalidation must start a rescan even when general restart rescanning is disabled")
  }

  private fun withIndexOff(beforeIndexTasksStarted: Runnable? = null, block: () -> Unit) {
    runBlocking { IndexingTestUtil.suspendUntilIndexesAreReady(project) }
    val tumbler = FileBasedIndexTumbler("FileBasedIndexTumblerTest")
    runInEdtAndWait { tumbler.turnOff() }
    try {
      waitUntil("The project is not dumb after turnOff", seconds = 10) { isDumb() }
      waitUntil("The wait task of the tumbler did not start", seconds = 10) { isWaitTaskRunning() }
      block()
    }
    finally {
      runInEdtAndWait {
        WriteIntentReadAction.run { tumbler.turnOn(beforeIndexTasksStarted) }
      }
      runBlocking { IndexingTestUtil.suspendUntilIndexesAreReady(project) }
    }
  }

  private fun isDumb(): Boolean = runInEdtAndGet { DumbService.isDumb(project) }

  private fun isWaitTaskRunning(): Boolean {
    return Thread.getAllStackTraces().values.any { frames ->
      frames.any { it.className.contains("WaitForSemaphoreInDumbModeTask") }
    }
  }

  private fun waitUntil(message: String, seconds: Long, condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
    while (!condition()) {
      if (System.nanoTime() > deadline) {
        fail<Unit>(message)
      }
      Thread.sleep(50)
    }
  }
}

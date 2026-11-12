// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.TextOccurenceProcessor
import com.intellij.psi.search.UsageSearchContext
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.minutes

/**
 * Checks that a write action does not wait for the pool tasks of a search whose launcher thread left with an exception.
 *
 * A thread interrupt makes the launcher thread leave `JobLauncher` with an exception that is not a cancellation.
 * The search then removes its `beforeWriteActionStart` listener, so no write action can cancel the pool tasks.
 * The tasks hold read locks. `JobLauncher` must cancel them before it leaves.
 */
@TestApplication
class PsiSearchInterruptedLauncherTest {
  companion object {
    private const val WORD = "stallneedle"
    private const val FILE_COUNT = 200
    private const val STALL_MS = 1_000L
    private const val RELEASE_MS = 8_000L
    private const val LAUNCHER_NAME = "search-launcher"

    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture("stall")
    private val sourceRootFixture = moduleFixture.sourceRootFixture()
  }

  /** The pool threads that are in the processor now, with the indicator that each one runs under. */
  private val poolThreads = ConcurrentHashMap<Thread, ProgressIndicator>()
  private val writeRequestedNanos = AtomicLong()

  @Volatile
  private var stallReport: String? = null

  @Volatile
  private var stop = false

  @Test
  fun `write action does not wait for the tasks of an interrupted search`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    val project = projectFixture.get()
    val root = sourceRootFixture.get()
    edtWriteAction {
      repeat(FILE_COUNT) { i ->
        root.virtualFile.createChildData(this, "f$i.txt").setBinaryContent("$WORD $WORD\n".toByteArray())
      }
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)

    val launcher = thread(name = LAUNCHER_NAME) { search(project) }
    val watchdog = thread(name = "stall-watchdog", isDaemon = true) { watch() }
    val waitMs: Long
    try {
      // the launcher thread first processes all free files itself, and only then waits for the pool threads
      val deadline = System.nanoTime() + 60_000L * 1_000_000
      while (System.nanoTime() < deadline && !(launcherWaitsForTasks(launcher) && poolThreads.isNotEmpty())) delay(5)
      assertTrue(launcherWaitsForTasks(launcher), "the launcher thread must wait for its tasks before the interrupt")
      assertTrue(poolThreads.isNotEmpty(), "a pool thread must be in the processor before the interrupt")

      launcher.interrupt()
      launcher.join(5_000)
      assertFalse(launcher.isAlive, "the interrupt must make the launcher thread leave the search")

      waitMs = requestWriteAction()
    }
    finally {
      stop = true
      launcher.join(30_000)
      watchdog.join(1_000)
    }
    stallReport?.let { fail<Unit>("the write action waited $waitMs ms for the tasks of the interrupted search\n\n$it") }
  }

  private fun search(project: Project) {
    val processor = TextOccurenceProcessor { _, _ -> work(); true }
    try {
      ProgressManager.getInstance().runProcess(
        {
          PsiSearchHelper.getInstance(project)
            .processElementsWithWord(processor, GlobalSearchScope.projectScope(project), WORD, UsageSearchContext.ANY, true)
        },
        ProgressIndicatorBase(),
      )
    }
    catch (_: Throwable) {
      // shortcut: the interrupt ends the search with an exception; the test checks only the tasks that the search leaves behind
    }
  }

  /** On a pool thread, CPU work that calls `checkCanceled` all the time. On the launcher thread, no work. */
  private fun work() {
    val thread = Thread.currentThread()
    if (thread.name == LAUNCHER_NAME) return
    poolThreads[thread] = ProgressManager.getGlobalProgressIndicator() ?: return
    try {
      while (!stop) {
        ProgressManager.checkCanceled()
        Thread.onSpinWait()
      }
    }
    finally {
      poolThreads.remove(thread)
    }
  }

  private fun launcherWaitsForTasks(launcher: Thread): Boolean = launcher.stackTrace.any { it.methodName == "quietlyJoin" }

  /** Requests a write action on the EDT with the lock sequence of the freeze reports, and returns the wait in milliseconds. */
  private suspend fun requestWriteAction(): Long = withContext(Dispatchers.EDT) {
    val requested = System.nanoTime()
    writeRequestedNanos.set(requested)
    var acquired = 0L
    try {
      // write-intent first, then the upgrade to write
      WriteIntentReadAction.run {
        ApplicationManager.getApplication().runWriteAction { acquired = System.nanoTime() }
      }
    }
    finally {
      writeRequestedNanos.set(0)
    }
    (acquired - requested) / 1_000_000
  }

  /** Records the state of the pool threads when the write action waits too long, and then stops them to release the EDT. */
  private fun watch() {
    while (!stop) {
      Thread.sleep(20)
      val requested = writeRequestedNanos.get()
      if (requested == 0L) continue
      val pendingMs = (System.nanoTime() - requested) / 1_000_000
      if (pendingMs >= STALL_MS && stallReport == null) stallReport = report(pendingMs)
      if (pendingMs >= RELEASE_MS) stop = true
    }
  }

  private fun report(pendingMs: Long): String = buildString {
    append("the write action is pending for $pendingMs ms\n")
    append("checkCanceled behavior: ${CoreProgressManager.getCheckCanceledBehaviorName()}\n")
    for ((thread, indicator) in poolThreads) {
      append("--- ${thread.name}\n")
      append("    indicator chain: ${CoreProgressManager.indicatorChain(indicator)}\n")
      append("    chain canceled: ${indicator.isCanceled}\n")
      append("    checkCanceled can throw (thread is marked): ${CoreProgressManager.hasThreadUnderCanceledIndicator(thread)}\n")
      thread.stackTrace.forEach { append("\tat $it\n") }
    }
  }
}

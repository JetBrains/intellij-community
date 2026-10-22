// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.awaitWithCheckCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes

@TestApplication
internal class ChangedFilesCollectorWriteActionTest {
  private val project = projectFixture(openAfterCreation = true)
  private val module = project.moduleFixture("src")
  private val sourceRoot = module.sourceRootFixture()
  private val file = sourceRoot.virtualFileFixture("Changed.txt", "text")

  /**
   * Reproduces IJPL-259436: the rebuild of excluded roots started by indexing of a changed file blocks a write action.
   */
  @Test
  fun `write action interrupts a slow exclude policy`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(1.minutes) {
    val project = project.get()
    IndexingTestUtil.suspendUntilIndexesAreReady(project)

    val policy = BlockingExcludePolicy()
    edtWriteAction {
      DirectoryIndexExcludePolicy.EP_NAME.getPoint(project).registerExtension(policy, disposable)
    }
    val readerRebuiltRoots = (1..MAX_ATTEMPTS).any { interruptRebuild(project, policy) }
    assertThat(readerRebuiltRoots)
      .describedAs("Another thread rebuilt the excluded roots before the reader in all $MAX_ATTEMPTS attempts")
      .isTrue()
  }

  /**
   * Resets the excluded roots, lets a reader process a change of [file], and interrupts the rebuild of the roots with a
   * write action. Returns false when another thread rebuilt the roots first, so that the attempt checked nothing.
   */
  private suspend fun interruptRebuild(project: Project, policy: BlockingExcludePolicy): Boolean {
    val collector = (FileBasedIndex.getInstance() as FileBasedIndexImpl).changedFilesCollector
    val failure = AtomicReference<Throwable>()
    val reader = Thread {
      try {
        collector.ensureUpToDate()
      }
      catch (t: Throwable) {
        failure.set(t)
      }
    }
    val rebuild = Rebuild(reader)
    policy.rebuild = rebuild
    edtWriteAction {
      WorkspaceFileIndexEx.getInstance(project).indexData.resetCustomContributors()
    }
    collector.eventMerger.recordTransientStateChangeEvent(file.get())

    try {
      reader.start()
      assertThat(rebuild.entered.await(BLOCK_SECONDS, TimeUnit.SECONDS))
        .describedAs("No thread rebuilt the excluded roots")
        .isTrue()
      if (rebuild.caller.get() === reader) {
        edtWriteAction {}
        assertThat(rebuild.canceled.get())
          .describedAs("The write action did not interrupt the rebuild of the excluded roots")
          .isTrue()
      }
    }
    finally {
      rebuild.released.countDown()
    }
    reader.join(TimeUnit.SECONDS.toMillis(BLOCK_SECONDS))
    assertThat(reader.isAlive).describedAs("The reader did not finish processing the change").isFalse()
    val error = failure.get()
    if (error != null) throw error
    return rebuild.caller.get() === reader
  }

  /**
   * State of the first rebuild of the excluded roots after one reset, as seen by [BlockingExcludePolicy].
   */
  private class Rebuild(val reader: Thread) {
    val caller = AtomicReference<Thread>()
    val entered = CountDownLatch(1)
    val released = CountDownLatch(1)
    val canceled = AtomicBoolean()
  }

  /**
   * Blocks the first call of the current [rebuild] for at most [BLOCK_SECONDS] when [Rebuild.reader] makes it, so that
   * the test fails instead of hanging when a write action cannot interrupt the rebuild.
   */
  private class BlockingExcludePolicy : DirectoryIndexExcludePolicy {
    @Volatile
    var rebuild: Rebuild? = null

    override fun getExcludeUrlsForProject(): Array<String> {
      val rebuild = rebuild ?: return emptyArray()
      val thread = Thread.currentThread()
      if (!rebuild.caller.compareAndSet(null, thread)) return emptyArray()
      rebuild.entered.countDown()
      if (thread !== rebuild.reader) return emptyArray()

      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BLOCK_SECONDS)
      try {
        awaitWithCheckCanceled(ThrowableComputable {
          rebuild.released.await(10, TimeUnit.MILLISECONDS) || System.nanoTime() >= deadline
        })
      }
      catch (e: ProcessCanceledException) {
        rebuild.canceled.set(true)
        throw e
      }
      return emptyArray()
    }
  }
}

private const val BLOCK_SECONDS = 10L
private const val MAX_ATTEMPTS = 5

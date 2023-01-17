package com.intellij.util.indexing

import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import junit.framework.TestCase
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PerProviderSinkTest : LightPlatformTestCase() {

  private lateinit var queue: PerProjectIndexingQueue
  private lateinit var provider: FakeIterator

  override fun setUp() {
    super.setUp()
    queue = PerProjectIndexingQueue(project)
    provider = FakeIterator()
  }

  fun testNoAddCommit() {
    queue.getSink(provider).use { sink ->
      sink.commit()
    }
    val (filesInQueue, totalFiles, currentLatch) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(0, filesInQueue.size)
  }

  fun testAddCommit() {
    queue.getSink(provider).use { sink ->
      sink.addFile(LightVirtualFile("f1"))
      sink.commit()
    }
    val (filesInQueue, totalFiles, currentLatch) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(1, filesInQueue.size)
    TestCase.assertEquals(1, filesInQueue[provider]?.size)
  }

  fun testAddNoCommit() {
    queue.getSink(provider).use { sink ->
      sink.addFile(LightVirtualFile("f1"))
    }
    val (filesInQueue, totalFiles, currentLatch) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(0, filesInQueue.size)
  }

  fun testAddClearCommit() {
    queue.getSink(provider).use { sink ->
      sink.addFile(LightVirtualFile("f1"))
      sink.clear()
      sink.commit()
    }
    val (filesInQueue, totalFiles, currentLatch) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(0, filesInQueue.size)
  }

  fun testAddCommitCommit() {
    queue.getSink(provider).use { sink ->
      sink.addFile(LightVirtualFile("f1"))
      sink.commit()
      sink.commit()
    }
    val (filesInQueue, totalFiles, currentLatch) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(1, filesInQueue.size)
    TestCase.assertEquals(1, filesInQueue[provider]?.size)
  }

  fun testAddCommitTwoSinks() {
    val provider2 = FakeIterator()

    queue.getSink(provider).use { sink ->
      sink.addFile(LightVirtualFile("f1"))
      sink.commit()
    }

    queue.getSink(provider2).use { sink ->
      sink.addFile(LightVirtualFile("f2"))
      sink.commit()
    }
    val (filesInQueue, totalFiles, currentLatch) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(2, filesInQueue.size)
    TestCase.assertEquals(1, filesInQueue[provider]?.size)
    TestCase.assertEquals(1, filesInQueue[provider2]?.size)
  }

  fun testCancelAllTasksAndWait() {
    val sinkRunning = AtomicBoolean(false)
    val phaser = Phaser(2)
    val task = object : Task.Backgroundable(project, "Test task", true) {
      override fun run(indicator: ProgressIndicator) {
        TestCase.assertFalse(indicator.isCanceled)
        val sink = queue.getSink(provider)
        try {
          phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) // p1
          sinkRunning.set(true)
          phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) // p2
          Thread.sleep(500) // give a chance for cancelAllTasksAndWait to take effect
          sink.addFile(LightVirtualFile("f1"))
        }
        finally {
          sinkRunning.set(false)
          sink.close()
          TestCase.assertTrue(indicator.isCanceled) // sink.addFile should cancel the progress
        }
      }
    }

    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, EmptyProgressIndicator())

    TestCase.assertFalse(sinkRunning.get())
    phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) // p1
    phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) // p2
    TestCase.assertTrue(sinkRunning.get())
    queue.cancelAllTasksAndWait()
    TestCase.assertFalse(sinkRunning.get())
  }

  fun testNonCancelableSection() {
    val nonCancelableSectionCompeteNormally = AtomicBoolean(false)
    val phaser = Phaser(2)
    val task = object : Task.Backgroundable(project, "Test task", true) {
      override fun run(indicator: ProgressIndicator) {
        try {
          TestCase.assertNotNull(ProgressManager.getGlobalProgressIndicator())

          // There should be no PCE in non-cancelable sections
          ProgressManager.getInstance().executeNonCancelableSection {
            try {
              TestCase.assertFalse(indicator.isCanceled)
              queue.getSink(provider).use { sink ->
                sink.addFile(LightVirtualFile("f1"))
              }
            }
            catch (pce: ProcessCanceledException) {
              TestCase.fail("Should not throw PCE in non-cancellable section")
            }
          }
          nonCancelableSectionCompeteNormally.set(true)
        }
        finally {
          phaser.arriveAndDeregister() // p1
        }
      }
    }

    // cancel and wait. This will return immediately because no sinks are connected yet. But new sinks should be rejected.
    queue.cancelAllTasksAndWait()

    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, EmptyProgressIndicator())

    phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) // p1
    TestCase.assertTrue(nonCancelableSectionCompeteNormally.get())
  }

  private fun getAndResetQueuedFiles(queue: PerProjectIndexingQueue):
    Triple<ConcurrentMap<IndexableFilesIterator, Collection<VirtualFile>>, Int, CountDownLatch?> =
    PerProjectIndexingQueue.TestCompanion(queue).getAndResetQueuedFiles()

  private class FakeIterator : IndexableFilesIterator {
    override fun getDebugName(): String = "FakeIterator"
    override fun getIndexingProgressText(): String = "FakeIterator"
    override fun getRootsScanningProgressText(): String = "FakeIterator"

    override fun getOrigin(): IndexableSetOrigin {
      throw IllegalStateException("Not yet implemented")
    }

    override fun iterateFiles(project: Project, fileIterator: ContentIterator, fileFilter: VirtualFileFilter): Boolean {
      throw IllegalStateException("Not yet implemented")
    }

    override fun getRootUrls(project: Project): MutableSet<String> {
      throw IllegalStateException("Not yet implemented")
    }
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class PerProviderSinkTest : LightPlatformTestCase() {
  private val DEFAULT_SCANNING_ID = 0L

  private lateinit var queue: PerProjectIndexingQueue
  private lateinit var provider: FakeIterator

  override fun setUp() {
    super.setUp()
    queue = PerProjectIndexingQueue(project)
    provider = FakeIterator()
  }

  fun testNoAddCommit() {
    queue.getSink(provider, DEFAULT_SCANNING_ID).use { sink ->
      sink.commit()
    }
    val (filesInQueue, _) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(0, filesInQueue.size)
  }

  fun testAddCommit() {
    queue.getSink(provider, DEFAULT_SCANNING_ID).use { sink ->
      sink.addFile(LightVirtualFile("f1"))
      sink.commit()
    }
    val (filesInQueue) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(1, filesInQueue.size)
    TestCase.assertEquals(1, filesInQueue[provider]?.size)
  }

  fun testAddNoCommit() {
    queue.getSink(provider, DEFAULT_SCANNING_ID).use { sink ->
      sink.addFile(LightVirtualFile("f1"))
    }
    val (filesInQueue, _) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(0, filesInQueue.size)
  }

  fun testAddClearCommit() {
    queue.getSink(provider, DEFAULT_SCANNING_ID).use { sink ->
      sink.addFile(LightVirtualFile("f1"))
      sink.clear()
      sink.commit()
    }
    val (filesInQueue, _) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(0, filesInQueue.size)
  }

  fun testAddCommitCommit() {
    queue.getSink(provider, DEFAULT_SCANNING_ID).use { sink ->
      sink.addFile(LightVirtualFile("f1"))
      sink.commit()
      sink.commit()
    }
    val (filesInQueue, _) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(1, filesInQueue.size)
    TestCase.assertEquals(1, filesInQueue[provider]?.size)
  }

  fun testAddCommitTwoSinks() {
    val provider2 = FakeIterator()

    queue.getSink(provider, DEFAULT_SCANNING_ID).use { sink ->
      sink.addFile(LightVirtualFile("f1"))
      sink.commit()
    }

    queue.getSink(provider2, DEFAULT_SCANNING_ID).use { sink ->
      sink.addFile(LightVirtualFile("f2"))
      sink.commit()
    }
    val (filesInQueue, _) = getAndResetQueuedFiles(queue)
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
        val sink = queue.getSink(provider, DEFAULT_SCANNING_ID)
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
              queue.getSink(provider, DEFAULT_SCANNING_ID).use { sink ->
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

  fun testManySinksManyProvidersStress() {
    val maxBatchSize = 100
    val threadsCount = 30
    val providersCount = 5
    val queueFlushCount = 50

    val threadsCompleted = AtomicInteger()
    val filesSubmitted = AtomicInteger()
    val providers = List(providersCount) { FakeIterator() }

    class SimpleRandomizedProducer(private val producerName: String) : Runnable {
      override fun run() {
        var batch = 0
        while (true) {
          batch++
          val batchSize = Random.nextInt(maxBatchSize)
          val provider = providers.random()
          queue.getSink(provider, DEFAULT_SCANNING_ID).use { sink ->
            for (f in 1..batchSize) {
              sink.addFile(LightVirtualFile("$producerName batch $batch file $f"))
            }
            sink.commit()
            filesSubmitted.addAndGet(batchSize)
          }
        }
      }
    }

    for (i in 1..threadsCount) {
      Thread {
        try {
          ProgressManager.getInstance().runProcess(SimpleRandomizedProducer("Producer $i"), EmptyProgressIndicator())
        }
        catch (_: ProcessCanceledException) {
          // do nothing. This is not a production code, ignore the PCE
        }
        finally {
          threadsCompleted.incrementAndGet()
        }
      }.start()
    }

    var totalFilesSum = 0
    for (i in 1..queueFlushCount) {
      if (i == queueFlushCount) {
        queue.cancelAllTasksAndWait()
      }

      val (filesInQueue, totalFiles) = getAndResetQueuedFiles(queue)
      // Don't test latch: it is always `null` because in unit tests [UnindexedFilesScanner.shouldScanInSmartMode()] reports `false`
      //TestCase.assertTrue("Total files: $totalFiles, latch: $currentLatch", (totalFiles < DUMB_MODE_THRESHOLD) == (currentLatch == null))
      val totalFilesInThisQueue = filesInQueue.values.sumOf(Collection<*>::size)
      TestCase.assertEquals(totalFiles, totalFilesInThisQueue)
      totalFilesSum += totalFilesInThisQueue
      Thread.sleep(50)
    }

    val (filesInQueue, totalFiles) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(0, filesInQueue.size)
    TestCase.assertEquals(0, totalFiles)
    TestCase.assertEquals(0, queue.estimatedFilesCount().value)

    TestCase.assertEquals(threadsCompleted.get(), threadsCount)
    TestCase.assertEquals(filesSubmitted.get(), totalFilesSum)
  }

  private fun getAndResetQueuedFiles(queue: PerProjectIndexingQueue):
    Pair<ConcurrentMap<IndexableFilesIterator, Collection<VirtualFile>>, Int> =
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
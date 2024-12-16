// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.progress.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.indexing.events.FileIndexingRequest
import junit.framework.TestCase
import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class PerProviderSinkTest : LightPlatformTestCase() {
  private val DEFAULT_SCANNING_ID = 0L

  private lateinit var queue: PerProjectIndexingQueue
  private val idCounter = AtomicInteger(0)

  override fun setUp() {
    super.setUp()
    queue = PerProjectIndexingQueue(project)
  }

  private class LightVirtualFileWithId(name: String, private val id: Int) : LightVirtualFile(name), VirtualFileWithId {
    override fun getId(): Int = id
  }

  private fun createFile(name: String): VirtualFile {
    return LightVirtualFileWithId(name, idCounter.incrementAndGet())
  }

  fun testNoAddClose() {
    queue.getSink(DEFAULT_SCANNING_ID).close()
    val (filesInQueue, _) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(0, filesInQueue.size)
  }

  fun testAddClose() {
    queue.getSink(DEFAULT_SCANNING_ID).use { sink ->
      sink.addFile(createFile("f1"))
    }
    val (filesInQueue) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(1, filesInQueue.size)
  }

  fun testAddCloseClose() {
    queue.getSink(DEFAULT_SCANNING_ID).use { sink ->
      sink.addFile(createFile("f1"))
      sink.close()
      sink.close()
    }
    val (filesInQueue, _) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(1, filesInQueue.size)
  }

  fun testAddCloseTwoSinks() {
    queue.getSink(DEFAULT_SCANNING_ID).use { sink ->
      sink.addFile(createFile("f1"))
    }

    queue.getSink(DEFAULT_SCANNING_ID).use { sink ->
      sink.addFile(createFile("f2"))
    }
    val (filesInQueue, _) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(2, filesInQueue.size)
  }

  fun testCancelAllTasksAndWait() {
    val sinkRunning = AtomicBoolean(false)
    val phaser = Phaser(2)
    val task = object : Task.Backgroundable(project, "Test task", true) {
      override fun run(indicator: ProgressIndicator) {
        assertFalse(indicator.isCanceled)
        val sink = queue.getSink(DEFAULT_SCANNING_ID)
        try {
          phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) // p1
          sinkRunning.set(true)
          phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) // p2
          Thread.sleep(500) // give a chance for cancelAllTasksAndWait to take effect
          sink.addFile(createFile("f1"))
        }
        finally {
          sinkRunning.set(false)
          sink.close()
          assertTrue(indicator.isCanceled) // sink.addFile should cancel the progress
        }
      }
    }

    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, EmptyProgressIndicator())

    assertFalse(sinkRunning.get())
    phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) // p1
    phaser.awaitAdvanceInterruptibly(phaser.arrive(), 5, TimeUnit.SECONDS) // p2
    assertTrue(sinkRunning.get())
    queue.cancelAllTasksAndWait()
    assertFalse(sinkRunning.get())
  }

  fun testNonCancelableSection() {
    val nonCancelableSectionCompeteNormally = AtomicBoolean(false)
    val phaser = Phaser(2)
    val task = object : Task.Backgroundable(project, "Test task", true) {
      override fun run(indicator: ProgressIndicator) {
        try {
          assertNotNull(ProgressManager.getGlobalProgressIndicator())

          // There should be no PCE in non-cancelable sections
          ProgressManager.getInstance().executeNonCancelableSection {
            try {
              assertFalse(indicator.isCanceled)
              queue.getSink(DEFAULT_SCANNING_ID).use { sink ->
                sink.addFile(createFile("f1"))
              }
            }
            catch (_: ProcessCanceledException) {
              fail("Should not throw PCE in non-cancellable section")
            }
            catch (t: Throwable) {
              assertEquals("Could not cancel sink creation", t.message)
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
    assertTrue(nonCancelableSectionCompeteNormally.get())
  }

  fun testManySinksManyProvidersStress() {
    val maxBatchSize = 100
    val threadsCount = 30
    val queueFlushCount = 50

    val threadsCompleted = AtomicInteger()
    val filesSubmitted = AtomicInteger()

    class SimpleRandomizedProducer(private val producerName: String) : Runnable {
      override fun run() {
        var batch = 0
        while (true) {
          batch++
          val batchSize = Random.nextInt(maxBatchSize)
          queue.getSink(DEFAULT_SCANNING_ID).use { sink ->
            for (f in 1..batchSize) {
              sink.addFile(createFile("$producerName batch $batch file $f"))
              filesSubmitted.incrementAndGet()
            }
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
      val totalFilesInThisQueue = filesInQueue.size
      TestCase.assertEquals("iteration $i", totalFiles, totalFilesInThisQueue)
      totalFilesSum += totalFilesInThisQueue
      Thread.sleep(50)
    }

    val (filesInQueue, totalFiles) = getAndResetQueuedFiles(queue)
    TestCase.assertEquals(0, filesInQueue.size)
    TestCase.assertEquals(0, totalFiles)

    TestCase.assertEquals(threadsCompleted.get(), threadsCount)
    TestCase.assertEquals(filesSubmitted.get(), totalFilesSum)
  }

  private fun getAndResetQueuedFiles(queue: PerProjectIndexingQueue): Pair<Set<FileIndexingRequest>, Int> {
    val queuedFiles = PerProjectIndexingQueue.TestCompanion(queue).getAndResetQueuedFiles()
    return Pair(queuedFiles.requests, queuedFiles.size)
  }
}
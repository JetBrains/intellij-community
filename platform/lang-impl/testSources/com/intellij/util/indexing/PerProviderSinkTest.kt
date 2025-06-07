// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.indexing.events.FileIndexingRequest
import junit.framework.TestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

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

  fun testAddFile() {
    queue.addFile(createFile("f1"), DEFAULT_SCANNING_ID)
    var filesInQueue = getAndResetQueuedFiles(queue).first
    TestCase.assertEquals(1, filesInQueue.size)

    queue.addFile(createFile("f2"), DEFAULT_SCANNING_ID)
    queue.addFile(createFile("f3"), DEFAULT_SCANNING_ID)
    filesInQueue = getAndResetQueuedFiles(queue).first
    TestCase.assertEquals(2, filesInQueue.size)
  }

  fun testStress() = runBlocking {
    val threadsCount = 30
    val queueFlushCount = 50

    val threadsCompleted = AtomicInteger()
    val filesSubmitted = AtomicInteger()
    val producersRunning = AtomicBoolean(true)
    val semaphore = Semaphore(threadsCount, threadsCount)

    // producers
    repeat(threadsCount) { producerNr ->
      launch(Dispatchers.IO) {
        try {
          var fileNr = 0
          while (producersRunning.get()) {
            fileNr++
            queue.addFile(createFile("$producerNr file $fileNr"), DEFAULT_SCANNING_ID)
            filesSubmitted.incrementAndGet()
          }
        }
        finally {
          threadsCompleted.incrementAndGet()
          semaphore.release()
        }
      }
    }

    var totalFilesSum = 0
    // check queue state at random times while producers are running concurrently
    for (i in 1..queueFlushCount) {
      if (i == queueFlushCount) {
        // terminate all the producers and wait
        producersRunning.set(false)
        repeat(threadsCount) { semaphore.acquire() }
      }

      val (filesInQueue, totalFiles) = getAndResetQueuedFiles(queue)
      val totalFilesInThisQueue = filesInQueue.size
      TestCase.assertEquals("iteration $i", totalFiles, totalFilesInThisQueue)
      totalFilesSum += totalFilesInThisQueue
      Thread.sleep(50)
    }

    // check final state of the queue
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
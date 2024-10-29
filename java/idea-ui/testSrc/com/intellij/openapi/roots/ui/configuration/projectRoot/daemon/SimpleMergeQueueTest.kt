// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.projectRoot.daemon

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.UsefulTestCase.assertOrderedEquals
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.Alarm.ThreadToUse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.concurrent.Semaphore


@TestApplication
class SimpleMergeQueueTest {
  private lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    disposable = Disposer.newDisposable()
  }

  @AfterEach
  fun tearDown() {
    Disposer.dispose(disposable)
  }

  @ParameterizedTest
  @EnumSource(ThreadToUse::class, names = ["SWING_THREAD", "POOLED_THREAD"])
  fun duplicateIsRemoved(threadToUse: ThreadToUse) {
    val list = mutableListOf<String>()

    executeInQueue(threadToUse) { queue ->
      queue.queue(Task("0", "0", list))
      queue.queue(Task("1", "1", list))
      queue.queue(Task("2", "2", list))
      queue.queue(Task("0", "3", list)) // equals to the first task
    }

    synchronized(list) {
      assertOrderedEquals(list, "0", "1", "2")
    }
  }

  @ParameterizedTest
  @EnumSource(ThreadToUse::class, names = ["SWING_THREAD", "POOLED_THREAD"])
  fun duplicateIsNotRemovedAfterTimeout(threadToUse: ThreadToUse) {
    val list = mutableListOf<String>()

    val queue = SimpleMergingQueue<Runnable>("test", 100, false, threadToUse, disposable)
    queue.queue(Task("0", "0", list))
    queue.queue(Task("1", "1", list))
    waitForQueue(queue)
    queue.queue(Task("0", "0", list))
    queue.queue(Task("1", "1", list))
    waitForQueue(queue)

    synchronized(list) {
      assertOrderedEquals(list, "0", "1", "0", "1")
    }
  }

  @ParameterizedTest
  @EnumSource(ThreadToUse::class, names = ["SWING_THREAD", "POOLED_THREAD"])
  fun errorInTaskDoesNotPreventConsequentTasksFromExecuting(threadToUse: ThreadToUse) {
    val list = mutableListOf<String>()

    val error = RuntimeException()
    // testLogger usually throws. Instead, let's intercept the error. In this case, the test works the same way as in production
    val loggerError = LoggedErrorProcessor.executeAndReturnLoggedError {
      executeInQueue(threadToUse) { queue ->
        queue.queue(Task("0", "0", list))
        queue.queue {
          throw error // this error should not prevent the next task from execution
        }
        queue.queue(Task("1", "1", list))
      }
    }
    Assertions.assertEquals(loggerError, error)

    synchronized(list) {
      assertOrderedEquals(list, "0", "1")
    }
  }

  private fun waitForQueue(queue: SimpleMergingQueue<Runnable>) {
    val semaphore = Semaphore(0)
    queue.queue(Runnable {
      semaphore.release()
    })
    queue.start()
    semaphore.acquire() // wait for tasks to be processed
  }

  private fun executeInQueue(threadToUse: ThreadToUse, block: (SimpleMergingQueue<Runnable>) -> Unit) {
    val queue = SimpleMergingQueue<Runnable>("test", 100, false, threadToUse, disposable)
    block(queue)
    waitForQueue(queue)
  }

  private class Task(val equality: String, val text: String, val list: MutableList<String>) : Runnable {
    override fun run() {
      synchronized(list) {
        list += text
      }
    }

    override fun hashCode(): Int {
      return equality.hashCode()
    }

    override fun equals(other: Any?): Boolean {
      return other is Task && equality == other.equality
    }
  }
}
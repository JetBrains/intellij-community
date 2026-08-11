// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class PackagingSuiteTaskSchedulingTest {
  @Test
  fun `replenishes packaging capacity without waiting for the whole first wave`() {
    runBlocking {
      withTimeout(5.seconds) {
        val startedTasks = ArrayList<String>()
        val firstWave = listOf("priority-1", "priority-2", "priority-3")
        val remaining = listOf("remaining-1", "remaining-2")
        val completions = (firstWave + remaining).associateWith { CompletableDeferred<Unit>() }
        val startSignals = remaining.associateWith { CompletableDeferred<Unit>() }

        val scheduler = async(start = CoroutineStart.UNDISPATCHED) {
          startRemainingTasksWithRollingReplenishment(
            startedTasks = firstWave,
            remainingTasks = remaining,
            getCompletion = completions::getValue,
            startTask = { task ->
              startedTasks.add(task)
              startSignals.getValue(task).complete(Unit)
            },
          )
        }

        assertThat(startedTasks).isEmpty()
        completions.getValue("priority-2").complete(Unit)
        startSignals.getValue("remaining-1").await()
        assertThat(startedTasks).containsExactly("remaining-1")
        assertThat(startSignals.getValue("remaining-2").isCompleted).isFalse()

        completions.getValue("priority-1").complete(Unit)
        startSignals.getValue("remaining-2").await()
        scheduler.await()
        assertThat(startedTasks).containsExactly("remaining-1", "remaining-2")
      }
    }
  }

  @Test
  fun `preserves the larger previous wave as the concurrency ceiling`() {
    runBlocking {
      withTimeout(5.seconds) {
        val startedTasks = ArrayList<String>()
        val firstWave = listOf("priority-1", "priority-2")
        val remaining = listOf("remaining-1", "remaining-2", "remaining-3", "remaining-4")
        val completions = (firstWave + remaining).associateWith { CompletableDeferred<Unit>() }
        val startSignals = remaining.associateWith { CompletableDeferred<Unit>() }

        val scheduler = async(start = CoroutineStart.UNDISPATCHED) {
          startRemainingTasksWithRollingReplenishment(
            startedTasks = firstWave,
            remainingTasks = remaining,
            getCompletion = completions::getValue,
            startTask = { task ->
              startedTasks.add(task)
              startSignals.getValue(task).complete(Unit)
            },
          )
        }

        completions.getValue("priority-1").complete(Unit)
        startSignals.getValue("remaining-3").await()
        assertThat(startedTasks).containsExactly("remaining-1", "remaining-2", "remaining-3")
        assertThat(startSignals.getValue("remaining-4").isCompleted).isFalse()

        completions.getValue("priority-2").complete(Unit)
        startSignals.getValue("remaining-4").await()
        scheduler.await()
        assertThat(startedTasks).containsExactlyElementsOf(remaining)
      }
    }
  }

  @Test
  fun `starts all remaining tasks when the first wave is empty`() {
    runBlocking {
      val remaining = listOf("remaining-1", "remaining-2")
      val startedTasks = ArrayList<String>()

      startRemainingTasksWithRollingReplenishment(
        startedTasks = emptyList(),
        remainingTasks = remaining,
        getCompletion = { error("Completion must not be queried when all tasks can start immediately") },
        startTask = startedTasks::add,
      )

      assertThat(startedTasks).containsExactlyElementsOf(remaining)
    }
  }
}

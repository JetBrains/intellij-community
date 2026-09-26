// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.buildScripts.concurrency.TaskSignal
import com.intellij.platform.buildScripts.concurrency.taskScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

@Timeout(20)
class PackagingSuiteTaskSchedulingTest {
  @Test
  fun `replenishes capacity after one task completes`() {
    checkSchedule(firstWaveSize = 3, remainingSize = 2, firstReplenishment = 1)
  }

  @Test
  fun `preserves the larger wave as the concurrency ceiling`() {
    checkSchedule(firstWaveSize = 2, remainingSize = 4, firstReplenishment = 3)
  }

  private fun checkSchedule(firstWaveSize: Int, remainingSize: Int, firstReplenishment: Int) {
    val firstWave = (1..firstWaveSize).map { "priority-$it" }
    val remaining = (1..remainingSize).map { "remaining-$it" }
    val completed = (firstWave + remaining).associateWith { TaskSignal<Unit>() }
    val started = remaining.associateWith { TaskSignal<Unit>() }
    val order = CopyOnWriteArrayList<String>()
    taskScope {
      val tasks = PackagingTasks(this, PackagingSuiteHangDiagnostics())
      val handles = completed.mapValues { (name, completion) -> tasks.task(name, startImmediately = true) { completion.await() } }
      val scheduler = fork("scheduler") {
        startRemainingTasksWithRollingReplenishment(firstWave, remaining, handles::getValue) { name ->
          order.add(name)
          started.getValue(name).complete(Unit)
        }
      }
      try {
        assertThat(order).isEmpty()
        completed.getValue(firstWave.first()).complete(Unit)
        started.getValue(remaining[firstReplenishment - 1]).await(5.seconds)
        assertThat(order).containsExactlyElementsOf(remaining.take(firstReplenishment))
        assertThat(started.getValue(remaining.last()).isDone).isFalse()
        completed.getValue(firstWave[1]).complete(Unit)
        scheduler.await(5.seconds)
        assertThat(order).containsExactlyElementsOf(remaining)
      }
      finally {
        completed.values.forEach { it.complete(Unit) }
      }
      join()
    }
  }

  @Test
  fun `starts all remaining tasks when the first wave is empty`() {
    val started = ArrayList<String>()
    startRemainingTasksWithRollingReplenishment(
      emptyList(), listOf("first", "second"),
                                                getCompletion = { error("No observation is needed") }, startTask = { started.add(it) })
    assertThat(started).containsExactly("first", "second")
  }
}

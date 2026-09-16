// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import com.intellij.platform.buildScripts.concurrency.TaskSignal
import com.intellij.platform.buildScripts.concurrency.taskScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Timeout(20)
class PackagingSuiteHangDumpTest {
  @Test
  fun `watchdog reports task dependencies without aborting a slow wait`() {
    val reports = CopyOnWriteArrayList<String>()
    val reportReady = CountDownLatch(1)
    val release = TaskSignal<Unit>("release build")
    val waiting = CountDownLatch(1)
    taskScope {
      val diagnostics = PackagingSuiteHangDiagnostics()
      val task = PackagingTasks(this, diagnostics).task("slow build", startImmediately = true) {
        waiting.countDown()
        release.await()
        42
      }
      fork("release after report") {
        check(reportReady.await(10, TimeUnit.SECONDS))
        release.complete(Unit)
      }
      assertThat(waiting.await(5, TimeUnit.SECONDS)).isTrue()
      val result = awaitOnTestThread(
        "slow build", diagnostics, dumpDelay = 50.milliseconds, probeInterval = 5.seconds,
        report = { reports.add(it); reportReady.countDown() }) { task.await(10.seconds) }
      assertThat(result).isEqualTo(42)
      assertThat(reports).isNotEmpty()
      assertThat(reports.first()).contains("slow build", "release build")
      join()
    }
  }

  @Test
  fun `watchdog stays silent when the wait ends before the delay`() {
    val reports = CopyOnWriteArrayList<String>()
    assertThat(awaitOnTestThread("fast build", dumpDelay = 10.seconds, report = reports::add) { 42 }).isEqualTo(42)
    assertThat(reports).isEmpty()
  }
}

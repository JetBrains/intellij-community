// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PackagingSuiteHangDumpTest {
  @Test
  fun `dumps the coroutines once when the block outlives the delay`() {
    installCoroutineDebugProbes()
    val reports = ArrayList<String>()
    val result = awaitOnTestThread("slow block", dumpDelay = 100.milliseconds, report = reports::add) {
      delay(500.milliseconds)
      42
    }

    assertThat(result).isEqualTo(42)
    assertThat(reports).hasSize(1)
    val report = reports.single()
    assertThat(report).contains("slow block")
    assertThat(report).doesNotContain("not installed")
    // the stdlib `DebugProbesKt` is a no-op. When it shadows the forwarding one, the probes capture nothing and the dump is empty.
    val probesSource = Class.forName("kotlin.coroutines.jvm.internal.DebugProbesKt").protectionDomain?.codeSource?.location
    assertThat(report)
      .describedAs("the dump must name the suspended block; `DebugProbesKt` was loaded from $probesSource")
      .contains(PackagingSuiteHangDumpTest::class.simpleName!!)
  }

  @Test
  fun `does not report when the block ends before the delay`() {
    val reports = ArrayList<String>()
    val result = awaitOnTestThread("fast block", dumpDelay = 10.seconds, report = reports::add) { "done" }

    assertThat(result).isEqualTo("done")
    assertThat(reports).isEmpty()
  }
}

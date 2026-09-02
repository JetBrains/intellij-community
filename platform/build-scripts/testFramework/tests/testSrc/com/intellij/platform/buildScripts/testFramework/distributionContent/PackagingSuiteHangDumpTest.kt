// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.distributionContent

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PackagingSuiteHangDumpTest {
  @Test
  fun `aborts a frozen wait and names the cause`() {
    installCoroutineDebugProbes()
    val reports = ArrayList<String>()
    assertThatThrownBy {
      awaitOnTestThread("frozen block", dumpDelay = 500.milliseconds, probeInterval = 500.milliseconds, report = reports::add) {
        awaitCancellation()
      }
    }
      .isInstanceOf(PackagingSuiteHangException::class.java)
      .hasMessageContaining("frozen block")
      // the fixed text of `CoroutineScheduler.toString()`
      .hasMessageContaining("Pool Size {")
      .hasMessageContaining("Exceptions that escaped the coroutine machinery: none")
      .hasMessageContaining("Thread dump:")

    assertThat(reports).hasSize(1)
    // the stdlib `DebugProbesKt` is a no-op. When it shadows the forwarding one, the probes capture nothing and the dump is empty.
    val probesSource = Class.forName("kotlin.coroutines.jvm.internal.DebugProbesKt").protectionDomain?.codeSource?.location
    assertThat(reports.single())
      .describedAs("the dump must name the suspended block; `DebugProbesKt` was loaded from $probesSource")
      .contains(PackagingSuiteHangDumpTest::class.simpleName!!)
  }

  @Test
  fun `keeps waiting while the scope makes progress`() {
    installCoroutineDebugProbes()
    val reports = ArrayList<String>()
    val result = awaitOnTestThread("slow block", dumpDelay = 150.milliseconds, probeInterval = 150.milliseconds, report = reports::add) {
      var done = 0
      // every step starts a coroutine, so the coroutine dump differs between two probes of the watchdog
      repeat(10) {
        coroutineScope {
          done += async(Dispatchers.Default) {
            delay(100.milliseconds)
            1
          }.await()
        }
      }
      done
    }

    assertThat(result).isEqualTo(10)
    assertThat(reports).isEmpty()
  }

  @Test
  fun `does not report when the block ends before the delay`() {
    val reports = ArrayList<String>()
    val result = awaitOnTestThread("fast block", dumpDelay = 10.seconds, report = reports::add) { "done" }

    assertThat(result).isEqualTo("done")
    assertThat(reports).isEmpty()
  }

  @Test
  fun `records an exception that escapes the coroutine machinery`() {
    val diagnostics = PackagingSuiteHangDiagnostics()
    val scopeJob = SupervisorJob()
    val scope = createPackagingSuiteScope(job = scopeJob, diagnostics = diagnostics)
    try {
      runBlocking {
        scope.launch(CoroutineName("failing task")) { throw IllegalStateException("the resume failed") }.join()
      }
    }
    finally {
      scopeJob.cancel()
    }

    assertThat(diagnostics.describeEscapedExceptions())
      .contains("failing task")
      .contains("the resume failed")
  }
}

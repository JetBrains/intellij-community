// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.diagnostic.ThreadDumper
import com.intellij.execution.process.NopProcessHandler
import com.intellij.openapi.application.UI
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.management.ThreadInfo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TerminalExecutionConsoleTest : BasePlatformTestCase() {

  override fun runInDispatchThread(): Boolean = false

  fun `test disposing console should stop emulator thread`(): Unit = timeoutRunBlocking(20.seconds) {
    val processHandler = NopProcessHandler()
    val console = withContext(Dispatchers.UI) {
      TerminalExecutionConsole(project, null)
    }
    console.attachToProcess(processHandler)
    processHandler.startNotify()
    awaitCondition(5.seconds) { findEmulatorThreadInfo() != null }
    assertNotNull(findEmulatorThreadInfo())
    withContext(Dispatchers.UI) {
      Disposer.dispose(console)
    }
    awaitCondition(5.seconds) { findEmulatorThreadInfo() == null }
    assertNull(findEmulatorThreadInfo())
  }

  private suspend fun awaitCondition(timeout: Duration, condition: () -> Boolean) {
    withTimeoutOrNull(timeout) {
      while (!condition()) {
        delay(100.milliseconds)
      }
    }
  }

  private fun findEmulatorThreadInfo(): ThreadInfo? {
    val threadInfos = ThreadDumper.getThreadInfos()
    return threadInfos.find { it.threadName.startsWith("TerminalEmulator-") }
  }

}

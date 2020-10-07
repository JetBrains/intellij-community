// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator

import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemon
import com.intellij.execution.process.mediator.rt.MediatedProcessTestMain
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext

internal class ProcessMediatorTest {
  private val coroutineScope = CoroutineScope(EmptyCoroutineContext)
  private val client = ProcessMediatorClient(coroutineScope, createInProcessChannelForTesting())
  private val server = ProcessMediatorDaemon(createInProcessServerForTesting())

  private val TIMEOUT_MS = 3000.toLong()

  @BeforeEach
  fun start() {
    server.start()
  }

  @AfterEach
  fun tearDown() {
    try {
      client.close()
    }
    finally {
      server.stop()
      server.blockUntilShutdown()
    }
  }

  @Test
  internal fun `create simple process returning zero exit code`() {
    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.True::class)
      .startMediatedProcess()
    val exitValue = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    assertEquals(0, exitValue)
    assertEquals(0, process.exitValue())
  }

  @Test
  internal fun `create simple process returning non-zero exit code`() {
    val process = createProcessBuilderForJavaClass(MediatedProcessTestMain.False::class)
      .startMediatedProcess()
    val exitValue = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    assertEquals(42, exitValue)
    assertEquals(42, process.exitValue())
  }

  private fun ProcessBuilder.startMediatedProcess(): Process {
    return MediatedProcess.create(client, this)
  }
}
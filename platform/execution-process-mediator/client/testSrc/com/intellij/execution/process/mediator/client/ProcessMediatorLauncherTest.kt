// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.mediator.client

import com.intellij.execution.process.mediator.client.launcher.DaemonProcessLauncher
import com.intellij.execution.process.mediator.client.launcher.ProcessMediatorConnection
import com.intellij.execution.process.mediator.client.util.blockingGet
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.TestInfo

internal class ProcessMediatorLauncherTest : ProcessMediatorTest() {
  override fun createProcessMediatorConnection(coroutineScope: CoroutineScope, testInfo: TestInfo): ProcessMediatorConnection {
    val clientBuilder = ProcessMediatorClient.Builder(coroutineScope)
    return DaemonProcessLauncher(clientBuilder).launchAsync().blockingGet()
  }
}

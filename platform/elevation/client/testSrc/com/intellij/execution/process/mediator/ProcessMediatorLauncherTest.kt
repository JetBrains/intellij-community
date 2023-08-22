// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator

import com.intellij.execution.process.mediator.client.ProcessMediatorClient
import com.intellij.execution.process.mediator.launcher.DaemonProcessLauncher
import com.intellij.execution.process.mediator.launcher.ProcessMediatorConnection
import com.intellij.execution.process.mediator.util.blockingGet
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.TestInfo

internal class ProcessMediatorLauncherTest : ProcessMediatorTest() {
  override fun createProcessMediatorConnection(coroutineScope: CoroutineScope, testInfo: TestInfo): ProcessMediatorConnection {
    val clientBuilder = ProcessMediatorClient.Builder(coroutineScope)
    return DaemonProcessLauncher(clientBuilder).launchAsync().blockingGet()
  }
}

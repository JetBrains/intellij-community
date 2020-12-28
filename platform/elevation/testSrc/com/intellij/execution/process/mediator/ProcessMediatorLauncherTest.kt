// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator

import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemon
import com.intellij.execution.process.mediator.launcher.ProcessMediatorDaemonLauncher
import com.intellij.execution.process.mediator.util.blockingGet
import org.junit.jupiter.api.TestInfo

internal class ProcessMediatorLauncherTest : ProcessMediatorTest() {
  override fun createProcessMediatorDaemon(testInfo: TestInfo): ProcessMediatorDaemon {
    return ProcessMediatorDaemonLauncher().launchAsync().blockingGet()
  }
}

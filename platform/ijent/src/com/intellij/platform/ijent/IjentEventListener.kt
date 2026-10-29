// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.util.annotations.VisibleToClasses

@VisibleToClasses(
  "com.intellij.platform.frontend.split.baseline.light.LightIjentRedeploymentTest",
  "com.intellij.platform.ijent.IjentSessionExitReasonTest",
  "com.intellij.platform.ijent.community.ui.actions.dashboard.IjentDashboardTabStat",
  "com.intellij.platform.ijent.community.ui.actions.unavailable.IjentUnavailableDialogHandler",
  "com.intellij.platform.ijent.impl.base.GrpcIjentHealthCheckInterceptor",
  "com.intellij.platform.ijent.impl.base.IjentSessionImpl",
  "com.intellij.platform.ijent.spi.TestShellStrategy",
  "com.intellij.platform.ijent.ssh.AbstractIjentSshManagerTest",
)
interface IjentEventBus {
  val counter: IjentStatCounter
}
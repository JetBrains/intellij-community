// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.actions.dashboard

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.util.annotations.VisibleToClasses
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
@VisibleToClasses(
  "com.intellij.clouds.docker.gateway.ui.dashboard.DevcontainerDashboardActionKt",
  "com.intellij.clouds.docker.gateway.ui.dashboard.IjentDashboardTabPorts",
)
interface IjentDashboardTab {
  @get:NlsContexts.TabTitle
  val name: String
  fun createComponent(projects: List<Project>, ijentApi: IjentApi, ijentSession: IjentSession, parentComponent: JComponent?): JComponent?
  companion object {
    val EP_NAME: ExtensionPointName<IjentDashboardTab> = ExtensionPointName("com.intellij.ijent.dashboard.tab")
  }
}
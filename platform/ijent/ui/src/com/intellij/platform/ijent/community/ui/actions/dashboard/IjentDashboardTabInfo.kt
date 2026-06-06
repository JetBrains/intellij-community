// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.ui.actions.dashboard

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPathBoundDescriptor
import com.intellij.platform.eel.EelPosixApi
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.community.ui.actions.IjentImplBundle
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.asSafely
import javax.swing.JComponent

internal class IjentDashboardTabInfo : IjentDashboardTab {
  override val name: String
    get() = IjentImplBundle.message("tab.title.ijent.dashboard.info")

  private fun Row.textData(@NlsSafe text: String) = text(text)

  override fun createComponent(projects: List<Project>, ijentApi: IjentApi, ijentSession: IjentSession, parentComponent: JComponent?): JComponent {
    return panel {
      row(IjentImplBundle.message("label.ijent.dashboard.info.name")) {
        textData(ijentApi.descriptor.name)
      }
      row(IjentImplBundle.message("label.ijent.dashboard.info.nio.root")) {
        textData(ijentApi.descriptor.asSafely<EelPathBoundDescriptor>()?.rootPath?.toString() ?: "<i>no root path</i>")
      }
      row(IjentImplBundle.message("label.ijent.dashboard.info.platform")) {
        textData(ijentApi.platform.toString())
      }
      row(IjentImplBundle.message("label.ijent.dashboard.info.home")) {
        textData(ijentApi.userInfo.home.toString())
      }
      if (ijentApi is EelPosixApi) {
        row(IjentImplBundle.message("label.ijent.dashboard.info.uid")) {
          textData(ijentApi.userInfo.uid.toString())
        }
        row(IjentImplBundle.message("label.ijent.dashboard.info.gid")) {
          textData(ijentApi.userInfo.gid.toString())
        }
      }
      row(IjentImplBundle.message("label.ijent.dashboard.info.running")) {
        textData(ijentApi.isRunning.toString())
      }
      row(IjentImplBundle.message("label.ijent.dashboard.info.ijent.binary")) {
        textData(ijentApi.ijentProcessInfo.remoteExecutablePath)
      }
      row(IjentImplBundle.message("label.ijent.dashboard.info.ijent.version")) {
        textData(ijentApi.ijentProcessInfo.version)
      }
    }
  }
}

internal val EelDescriptor.rootPath: String?
  get() = this.asSafely<EelPathBoundDescriptor>()?.rootPath?.toString()
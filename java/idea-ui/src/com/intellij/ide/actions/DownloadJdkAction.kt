// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.projectWizard.generators.JdkDownloadService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ConfigureJdkService
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask

private val log = logger<DownloadJdkAction>()

class DownloadJdkAction: AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val sdkType = JavaSdk.getInstance()
    val downloadExtension = SdkDownload.EP_NAME.findFirstSafe { it: SdkDownload -> it.supportsDownload(sdkType) }
    val project = e.project ?: DefaultProjectFactory.getInstance().defaultProject

    if (downloadExtension != null) {
      downloadExtension.showDownloadUI(sdkType, ProjectSdksModel(), null, project, null, { true }) { task: SdkDownloadTask ->
        val sdk: Sdk
        project.service<JdkDownloadService>().apply {
          sdk = setupInstallableSdk(task)
          downloadSdk(sdk)
        }
        project.service<ConfigureJdkService>().setProjectJdkIfNull(sdk, true)
      }
    } else {
      log.warn("No download extension found to download a JDK")
    }
  }
}
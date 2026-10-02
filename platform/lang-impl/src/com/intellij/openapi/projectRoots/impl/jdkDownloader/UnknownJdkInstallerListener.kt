// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.UnknownSdkCheckerService

internal class UnknownJdkInstallerListener : JdkInstallerListener {
  override fun onJdkDownloadStarted(request: JdkInstallRequest, project: Project?) {
    checkUnknownSdks(project)
  }

  override fun onJdkDownloadFinished(request: JdkInstallRequest, project: Project?) {
    checkUnknownSdks(project)
  }

  private fun checkUnknownSdks(project: Project?) {
    if (project == null || project.isDisposed) return
    project.service<UnknownSdkCheckerService>().checkUnknownSdks()
  }
}
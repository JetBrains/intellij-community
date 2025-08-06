// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.AboutPopupDescriptionProvider
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsContexts

internal class ReporterIdForEAAutoReporters : AboutPopupDescriptionProvider {

  override fun getDescription(): @NlsContexts.DetailedDescription String? = null

  override fun getExtendedDescription(): @NlsContexts.DetailedDescription String {
    return DiagnosticBundle.message("about.dialog.text.ea.reporting.id", ITNProxy.DEVICE_ID)
  }
}

internal class ReporterIdLoggerActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    thisLogger().info(DiagnosticBundle.message("about.dialog.text.ea.reporting.id", ITNProxy.DEVICE_ID))
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.ApplicationActivity
import com.intellij.internal.DebugAttachDetector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException

/** Starts [JVMResponsivenessMonitor] on app start */
private class JVMResponsivenessMonitorStarter : ApplicationActivity {
  init {
    val app = ApplicationManager.getApplication()
    // We're interested in responsiveness for a regular user-facing IDE app.
    // Responsiveness statistics under unit-tests/headless or with debugger are unlikely representative for it.
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute() {
    if (!DebugAttachDetector.isDebugEnabled()) {
      serviceAsync<JVMResponsivenessMonitor>()
    }
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EnvironmentVariablesAwaitReporter
import kotlin.time.Duration

internal class EnvironmentVariablesAwaitReporterSpiForwarder : EnvironmentVariablesAwaitReporter {
  override fun started(descriptor: EelDescriptor, mode: EelExecApi.EnvironmentVariablesOptions.Mode) {
    delegate()?.started(descriptor, mode)
  }

  override fun finished(
    descriptor: EelDescriptor,
    mode: EelExecApi.EnvironmentVariablesOptions.Mode,
    duration: Duration,
    result: Result<Map<String, String>>,
  ) {
    delegate()?.finished(descriptor, mode, duration, result)
  }

  private fun delegate(): EnvironmentVariablesAwaitReporter? {
    val app = ApplicationManager.getApplication() ?: return null
    val service = app.getService(EnvironmentVariablesAwaitReporter::class.java) ?: return null
    return service.takeUnless { it === this }
  }
}

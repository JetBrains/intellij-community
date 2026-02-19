// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class StartupManagerEx : StartupManager() {
  companion object {
    @JvmStatic
    fun getInstanceEx(project: Project): StartupManagerEx = getInstance(project) as StartupManagerEx
  }

  abstract fun startupActivityPassed(): Boolean

  abstract suspend fun waitForInitProjectActivities(@NlsContexts.ProgressTitle progressTitle: String?)
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

private class RunToolbarInitializeService : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    withContext(Dispatchers.EDT) {
      delay(5.seconds)
      RunToolbarSlotManager.getInstance(project).initialized = true
    }
  }
}

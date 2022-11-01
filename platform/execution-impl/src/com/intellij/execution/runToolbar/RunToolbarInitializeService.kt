// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.coroutines.EmptyCoroutineContext

class RunToolbarInitializeService : StartupActivity.DumbAware {
  override fun runActivity(project: Project) {
    val cs = CoroutineScope(EmptyCoroutineContext)
    Disposer.register(project) {
      cs.cancel()
    }

    cs.launch(Dispatchers.EDT) {
      delay(Duration.of(5, ChronoUnit.SECONDS))
        RunToolbarSlotManager.getInstance(project).initialized = true
      }
  }
}

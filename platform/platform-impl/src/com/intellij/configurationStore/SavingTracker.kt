// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.IdeBundle
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.ActivityTracker
import org.jetbrains.annotations.Nls

private class SavingTracker : ActivityTracker {
  override val presentableName: @Nls String = IdeBundle.message("saving.activity.tracker.name")

  override suspend fun isInProgress(project: Project): Boolean {
    return false
  }

  override suspend fun awaitConfiguration(project: Project) {
  }
}
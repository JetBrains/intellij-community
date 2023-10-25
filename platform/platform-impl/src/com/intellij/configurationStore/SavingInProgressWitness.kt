// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.api.ActivityInProgressWitness
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@Internal
class SavingInProgressWitness : ActivityInProgressWitness {
  override val presentableName: @Nls String = "saving"

  override suspend fun isInProgress(project: Project): Boolean {
    saveSettings(ApplicationManager.getApplication(), true)
    saveProjectsAndApp(true, project)
    return false
  }

  override suspend fun awaitConfiguration(project: Project) {
    return
  }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.platform.backend.observation.trackActivity
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus

private class UnknownSdkHeadlessActivity : ProjectActivity {
  private object Key : ActivityKey {
    override val presentableName: String = "fix-unknown-sdks"
  }

  init {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) throw ExtensionNotApplicableException.create()
  }

  override suspend fun execute(project: Project) {
    try {
      project.trackActivity(Key) {
        coroutineToIndicator { configureUnknownSdks(project, ProgressManager.getGlobalProgressIndicator()) }
      }
    }
    finally {
      UnknownSdkActivityFinishedService.getInstance(project).activityFinished()
    }
  }
}

/**
 * Doesn't look good, we need to have state of activity available in observation API
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class UnknownSdkActivityFinishedService {
  companion object {
    fun getInstance(project: Project): UnknownSdkActivityFinishedService = project.service()
  }

  private val isFinished = Job()

  internal fun activityFinished() {
    isFinished.complete()
  }

  suspend fun await() {
    isFinished.join()
  }
}
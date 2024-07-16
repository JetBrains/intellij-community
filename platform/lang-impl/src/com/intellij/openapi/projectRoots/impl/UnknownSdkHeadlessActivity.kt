// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.platform.backend.observation.trackActivity

private class UnknownSdkHeadlessActivity : ProjectActivity {
  private object Key : ActivityKey {
    override val presentableName: String = "fix-unknown-sdks"
  }

  init {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) throw ExtensionNotApplicableException.create()
  }

  override suspend fun execute(project: Project) {
    project.trackActivity(Key) {
      coroutineToIndicator { configureUnknownSdks(project, ProgressManager.getGlobalProgressIndicator()) }
    }
  }
}

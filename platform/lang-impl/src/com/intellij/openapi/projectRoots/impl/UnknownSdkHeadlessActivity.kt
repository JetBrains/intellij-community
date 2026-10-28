// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.platform.backend.observation.trackActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus

internal class UnknownSdkHeadlessActivity : ProjectActivity {
  private object Key : ActivityKey {
    override val presentableName: String = "fix-unknown-sdks"
  }

  init {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) throw ExtensionNotApplicableException.create()
  }

  override suspend fun execute(project: Project) {
    project.trackActivity(Key) {
      configureUnknownSdks(project)
    }
  }
}

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class UnknownSdkActivityFinishedService(private val project: Project, coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): UnknownSdkActivityFinishedService = project.service()
  }

  private val fix = coroutineScope.async(start = CoroutineStart.LAZY) {
    runUnknownSdkFix(project)
  }

  internal suspend fun await() = fix.await()
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.workspaceModel.ide.JpsProjectLoadingManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private class FrameworkDetectionManagerProjectActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val jpsProjectLoadingManager = project.serviceAsync<JpsProjectLoadingManager>()
    suspendCancellableCoroutine { continuation ->
      jpsProjectLoadingManager.jpsProjectLoaded { continuation.resume(Unit) }
    }
    project.serviceAsync<FrameworkDetectionManager>().jpsProjectLoaded(serviceAsync<FrameworkDetectorRegistry>())
  }
}
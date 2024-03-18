// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.diagnostic.CoroutineTracerShim
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.execution.RunManager
import com.intellij.execution.RunManager.Companion.IS_RUN_MANAGER_INITIALIZED
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectServiceContainerInitializedListener
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

private class ProjectRunConfigurationInitializer : ProjectServiceContainerInitializedListener {
  override suspend fun execute(project: Project) {
    val coroutineTracer = CoroutineTracerShim.coroutineTracer
    (project as ComponentManagerEx).getCoroutineScope().launch(if (StartUpMeasurer.isEnabled()) coroutineTracer.rootTrace() else EmptyCoroutineContext) {
      if (IS_RUN_MANAGER_INITIALIZED.get(project) == true) {
        return@launch
      }

      // wait for module manager - may be required for module level run configurations
      // it allows us to avoid thread blocking
      // (RunManager itself cannot yet do the same, as the platform doesn't provide non-blocking load state)
      project.serviceAsync<ModuleManager>()

      coroutineTracer.span("RunManager initialization") {
        // we must not fire beginUpdate here, because message bus will fire queued parent message bus messages
        // (and, so, SOE may occur because all other projectOpened will be processed before us)
        // simply, you should not listen changes until the project opened
        project.serviceAsync<RunManager>()
        IS_RUN_MANAGER_INITIALIZED.set(project, true)
      }
    }
  }
}
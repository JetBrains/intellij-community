// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.diagnostic.runActivity
import com.intellij.execution.RunManager
import com.intellij.execution.RunManager.Companion.IS_RUN_MANAGER_INITIALIZED
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectServiceContainerInitializedListener
import kotlinx.coroutines.launch

private class ProjectRunConfigurationInitializer : ProjectServiceContainerInitializedListener {
  override suspend fun execute(project: Project) {
    project.coroutineScope.launch {
      if (IS_RUN_MANAGER_INITIALIZED.get(project) == true) {
        return@launch
      }

      // wait for module manager - may be required for module level run configurations
      // it allows us to avoid thread blocking
      // (RunManager itself cannot yet do the same, as platform doesn't provide non-blocking load state)
      (project as ComponentManagerEx).getServiceAsync(ModuleManager::class.java).join()

      runActivity("RunManager initialization") {
        // we must not fire beginUpdate here, because message bus will fire queued parent message bus messages (and, so, SOE may occur because all other projectOpened will be processed before us)
        // simply, you should not listen changes until project opened
        readActionBlocking {
          project.getService(RunManager::class.java)
        }
        IS_RUN_MANAGER_INITIALIZED.set(project, true)
      }
    }
  }
}
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ExternalSystemUnlinkedProjectAsyncAware : ExternalSystemUnlinkedProjectAware {
  @Service(Service.Level.PROJECT)
  private class ExternalSystemUnlinkedProjectAsyncAwareService(val coroutineScope: CoroutineScope)

  suspend fun linkAndLoadProjectAsync(project: Project, externalProjectPath: String)

  // prefer linkAndLoadProjectAsync
  override fun linkAndLoadProject(project: Project, externalProjectPath: String) {
    val cs = project.getService(ExternalSystemUnlinkedProjectAsyncAwareService::class.java).coroutineScope
    cs.launch {
      withContext(Dispatchers.Default) {
        linkAndLoadProjectAsync(project, externalProjectPath)
      }
    }
  }

}
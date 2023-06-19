// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ExternalSystemUnlinkedProjectAsyncAware : ExternalSystemUnlinkedProjectAware {
  suspend fun linkAndLoadProjectAsync(project: Project, externalProjectPath: String)

  // prefer linkAndLoadProjectAsync
  override fun linkAndLoadProject(project: Project, externalProjectPath: String) {
    runBlockingCancellable { linkAndLoadProjectAsync(project, externalProjectPath) }
  }

}
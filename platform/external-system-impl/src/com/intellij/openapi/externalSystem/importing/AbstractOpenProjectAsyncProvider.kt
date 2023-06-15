// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.importing

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class AbstractOpenProjectAsyncProvider : AbstractOpenProjectProvider() {
  suspend fun linkToExistingProjectAsync(projectFilePath: String, project: Project) {
    linkToExistingProjectAsync(getProjectFile(projectFilePath), project)
  }

  abstract suspend fun linkToExistingProjectAsync(projectFile: VirtualFile, project: Project)
}
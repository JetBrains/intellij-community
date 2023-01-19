// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.importing

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Deprecated("Useless abstraction")
interface OpenProjectProvider {
  fun canOpenProject(file: VirtualFile): Boolean

  suspend fun openProject(projectFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project?

  fun linkToExistingProject(projectFile: VirtualFile, project: Project)
}
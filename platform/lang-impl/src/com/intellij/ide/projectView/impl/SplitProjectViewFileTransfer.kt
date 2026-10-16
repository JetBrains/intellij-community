// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Experimental
sealed interface ProjectViewTransferableFile

@ApiStatus.Internal
data class ProjectViewTransferableFileImpl(val name: String, val transferId: String) : ProjectViewTransferableFile

@ApiStatus.Internal
interface FrontendProjectViewFileTransfer {
  fun isTransferAllowed(): Boolean

  fun allowTransfer(file: Path): ProjectViewTransferableFileImpl

  companion object {
    fun getInstanceOrNull(): FrontendProjectViewFileTransfer? = serviceOrNull()
  }
}

@ApiStatus.Experimental
interface BackendProjectViewFileTransfer {
  fun copyFromClient(files: List<ProjectViewTransferableFile>, targetDirectory: VirtualFile, project: Project)

  suspend fun sendToClient(files: List<VirtualFile>, project: Project)

  companion object {
    fun getInstanceOrNull(): BackendProjectViewFileTransfer? = serviceOrNull()
  }
}

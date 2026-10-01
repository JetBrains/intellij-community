// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FileHyperlinkNavigation {
  companion object {
    fun getInstance(): FileHyperlinkNavigation = service()
  }
  suspend fun navigateFileHyperlink(
    project: Project,
    descriptor: OpenFileDescriptor,
    useBrowser: Boolean,
    requestFocus: Boolean = true,
  ): Boolean

  fun navigateFileHyperlinkLegacy(
    project: Project,
    descriptor: OpenFileDescriptor,
    useBrowser: Boolean,
    requestFocus: Boolean = true,
  ): Boolean
}

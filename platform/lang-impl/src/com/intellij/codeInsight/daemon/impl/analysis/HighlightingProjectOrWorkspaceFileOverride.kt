// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point that allows to override the default behavior for project/workspace files
 * and force their inspection even when [com.intellij.openapi.project.ProjectUtil.isProjectOrWorkspaceFile] returns true.
 */
@ApiStatus.Internal
interface HighlightingProjectOrWorkspaceFileOverride {
  
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<HighlightingProjectOrWorkspaceFileOverride> =
      ExtensionPointName.create("com.intellij.codeInsight.highlightingProjectOrWorkspaceFileOverride")
  }
  
  /**
   * Determines whether the given project or workspace file should be inspected
   * despite being classified as a project/workspace file.
   */
  fun shouldInspectFile(file: VirtualFile, project: Project): Boolean
}
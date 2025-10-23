// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis

import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class IgnoredFileHighlightingOverrider: HighlightingProjectOrWorkspaceFileOverride {
  override fun shouldInspectFile(file: VirtualFile, project: Project): Boolean {
    val vcsIgnoreFileNames = VcsFacade.getInstance().getVcsIgnoreFileNames(project)
    return vcsIgnoreFileNames.contains(file.name)
  }
}

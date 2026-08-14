// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

internal class AnalysisIgnoreFileIconProvider : FileIconProvider {

  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    if (!Registry.`is`(ANALYSIS_IGNORE_ENABLED_KEY, true)) return null
    if (!file.isAnalysisIgnoreFile()) return null

    return AllIcons.Vcs.Ignore_file
  }
}

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis

import com.intellij.ide.trustedProjects.TrustedFiles
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Limits highlighting of files opened in the safe mode (see [TrustedFiles]) to [FileHighlightingSetting.SKIP_INSPECTION]:
 * syntax highlighting, annotators, and line markers stay on, while inspections and external annotators
 * (which may pass the file content to external tools) never run.
 */
internal class UntrustedFileHighlightingSettingProvider : DefaultHighlightingSettingProvider(), DumbAware {
  override fun getDefaultSetting(project: Project, file: VirtualFile): FileHighlightingSetting? {
    return if (TrustedFiles.isTrusted(file, project)) null else FileHighlightingSetting.SKIP_INSPECTION
  }
}

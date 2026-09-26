// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.highlighter.JShellFileType
import com.intellij.ide.scratch.ScratchFileTypeFilter
import com.intellij.openapi.fileTypes.FileType

internal class JshellScratchFileTypeFilter : ScratchFileTypeFilter {
  override fun isProhibited(type: FileType): Boolean {
    return type == JShellFileType.INSTANCE
  }
}

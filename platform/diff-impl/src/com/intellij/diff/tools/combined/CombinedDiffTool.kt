// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.icons.AllIcons
import com.intellij.openapi.diff.DiffBundle
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
internal interface CombinedDiffTool : DiffTool {
  fun getIcon(): Icon
}

/**
 * This tool intended only for persistence purpose.
 * Combined diff viewer will be created by the corresponding [CombinedDiffComponentProcessor].
 */
internal class CombinedSideBySideDiffTool : CombinedDiffTool {
  override fun canShow(context: DiffContext, request: DiffRequest): Boolean = false

  override fun getName(): String = DiffBundle.message("combined.side.by.side.viewer")

  override fun getIcon(): Icon = AllIcons.Diff.SideBySide
}

/**
 * This tool intended only for persistence purpose.
 * Combined diff viewer will be created by the corresponding [CombinedDiffComponentProcessor].
 */
internal class CombinedUnifiedDiffTool : CombinedDiffTool {
  override fun canShow(context: DiffContext, request: DiffRequest): Boolean = false

  override fun getName(): String = DiffBundle.message("combined.unified.viewer")

  override fun getIcon(): Icon = AllIcons.Diff.Unified
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation

internal interface CombinedDiffTool : DiffTool

/**
 * This tool intended only for persistence purpose.
 * Combined diff viewer will be created by the corresponding [CombinedDiffComponentProcessor].
 */
@InternalIgnoreDependencyViolation
private class CombinedSideBySideDiffTool : CombinedDiffTool {
  override fun canShow(context: DiffContext, request: DiffRequest): Boolean = false

  override fun getName(): String = DiffBundle.message("combined.side.by.side.viewer")
}

/**
 * This tool intended only for persistence purpose.
 * Combined diff viewer will be created by the corresponding [CombinedDiffComponentProcessor].
 */
@InternalIgnoreDependencyViolation
internal class CombinedUnifiedDiffTool : CombinedDiffTool {
  override fun canShow(context: DiffContext, request: DiffRequest): Boolean = false

  override fun getName(): String = DiffBundle.message("combined.unified.viewer")
}

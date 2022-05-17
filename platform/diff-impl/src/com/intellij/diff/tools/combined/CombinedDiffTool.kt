// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.combined

import com.intellij.diff.DiffContext
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.FrameDiffTool.DiffViewer
import com.intellij.diff.requests.DiffRequest
import com.intellij.openapi.diff.DiffBundle

interface CombinedDiffTool: FrameDiffTool

/**
 * This tool intended only for persistence purpose.
 * Combined diff viewer will be created by the corresponding [CombinedDiffComponentFactory].
 */
internal class CombinedSideBySideDiffTool : CombinedDiffTool {
  override fun canShow(context: DiffContext, request: DiffRequest): Boolean = false

  override fun createComponent(context: DiffContext, request: DiffRequest): DiffViewer = throw UnsupportedOperationException()

  override fun getName(): String = DiffBundle.message("combined.side.by.side.viewer")
}

/**
 * This tool intended only for persistence purpose.
 * Combined diff viewer will be created by the corresponding [CombinedDiffComponentFactory].
 */
internal class CombinedUnifiedDiffTool : CombinedDiffTool {
  override fun canShow(context: DiffContext, request: DiffRequest): Boolean = false

  override fun createComponent(context: DiffContext, request: DiffRequest): DiffViewer = throw UnsupportedOperationException()

  override fun getName(): String = DiffBundle.message("combined.unified.viewer")
}

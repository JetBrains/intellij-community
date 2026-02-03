// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface HighlightingProblemFactory {
  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<HighlightingProblemFactory> = ExtensionPointName("com.intellij.problemsViewHighlightingProblemFactory")
  }
  fun createHighlightingProblem(provider: ProblemsProvider, file: VirtualFile, highlighter: RangeHighlighter): HighlightingProblem
}

@ApiStatus.Internal
class DefaultHighlightingProblemFactory : HighlightingProblemFactory {
  override fun createHighlightingProblem(provider: ProblemsProvider, file: VirtualFile, highlighter: RangeHighlighter): HighlightingProblem {
    return HighlightingProblem(provider, file, highlighter)
  }
}
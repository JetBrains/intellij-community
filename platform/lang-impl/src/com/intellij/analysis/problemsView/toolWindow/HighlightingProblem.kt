// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.util.text.StringUtil.isLineBreak
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

internal class HighlightingProblem(
  override val provider: ProblemsProvider,
  override val file: VirtualFile,
  private val highlighter: RangeHighlighterEx
) : FileProblem {

  private fun getIcon(level: HighlightDisplayLevel) = if (severity >= level.severity.myVal) level.icon else null

  internal val info: HighlightInfo?
    get() = HighlightInfo.fromRangeHighlighter(highlighter)

  override val icon: Icon
    get() = HighlightDisplayLevel.find(info?.severity)?.icon
            ?: getIcon(HighlightDisplayLevel.ERROR)
            ?: getIcon(HighlightDisplayLevel.WARNING)
            ?: HighlightDisplayLevel.WEAK_WARNING.icon

  override val text: String
    get() {
      val text = description ?: return "Invalid"
      val pos = text.indexOfFirst { isLineBreak(it) }
      return if (pos < 0) text else text.substring(0, pos)
    }

  override val description: String?
    get() = info?.description

  val severity: Int
    get() = info?.severity?.myVal ?: -1

  override val offset: Int
    get() = info?.actualStartOffset ?: -1

  override fun hashCode() = highlighter.hashCode()

  override fun equals(other: Any?) = other is HighlightingProblem && other.highlighter == highlighter
}

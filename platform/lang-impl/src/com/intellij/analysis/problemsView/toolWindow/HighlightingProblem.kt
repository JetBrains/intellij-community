// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.CommonBundle
import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.AsyncDescriptionSupplier
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.xml.util.XmlStringUtil.escapeString
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Icon

internal class HighlightingProblem(
  override val provider: ProblemsProvider,
  override val file: VirtualFile,
  val highlighter: RangeHighlighterEx
) : FileProblem {

  private fun getIcon(level: HighlightDisplayLevel): Icon? = when {
    text.isEmpty() || asyncDescriptionRequested.get() -> AnimatedIcon.Default.INSTANCE
    severity >= level.severity.myVal -> level.icon
    else -> null
  }

  private var asyncDescriptionRequested = AtomicBoolean(false)
  private var loading = AtomicBoolean(false)

  val info: HighlightInfo?
    get() {
      val info = HighlightInfo.fromRangeHighlighter(highlighter)
      if (info is AsyncDescriptionSupplier) {
        requestAsyncDescription(info)
      }
      return info
    }

  private fun requestAsyncDescription(info: AsyncDescriptionSupplier) {
    if (!asyncDescriptionRequested.compareAndSet(false, true)) return
    loading.set(true)

    info.requestDescription().onSuccess {
      // we do that to avoid Concurrent modification exception
      ApplicationManager.getApplication().invokeLater {
        val panel = ProblemsViewToolWindowUtils.getTabById(provider.project, HighlightingPanel.ID) as? HighlightingPanel
        panel?.currentRoot?.problemUpdated(this)
        loading.set(false)
      }
    }
  }

  override val icon: Icon
    get() {
      val highlightInfo = info
      val severity = if (highlightInfo == null) HighlightSeverity.INFORMATION else highlightInfo.severity
      return HighlightDisplayLevel.find(severity)?.icon
             ?: getIcon(HighlightDisplayLevel.ERROR)
             ?: getIcon(HighlightDisplayLevel.WARNING)
             ?: HighlightDisplayLevel.WEAK_WARNING.icon
    }

  override val text: String
    get() {
      val text = info?.description ?: return CommonBundle.getLoadingTreeNodeText()
      val pos = text.indexOfFirst { StringUtil.isLineBreak(it) }
      return if (pos < 0 || text.startsWith("<html>", ignoreCase = true)) text
      else text.substring(0, pos) + StringUtil.ELLIPSIS
    }

  override val group: String?
    get() {
      val id = info?.inspectionToolId ?: return null
      return HighlightDisplayKey.getDisplayNameByKey(HighlightDisplayKey.findById(id))
    }

  override val description: String?
    get() {
      val text = info?.description ?: return null
      if (text.isEmpty()) return null
      val pos = text.indexOfFirst { StringUtil.isLineBreak(it) }
      return if (pos < 0 || text.startsWith("<html>", ignoreCase = true)) null
      else "<html>" + StringUtil.join(StringUtil.splitByLines(escapeString(text)), "<br/>")
    }

  val severity: Int
    get() = info?.severity?.myVal ?: -1

  override fun hashCode() = highlighter.hashCode()

  override fun equals(other: Any?) = other is HighlightingProblem && other.highlighter == highlighter

  override val line: Int
    get() = position?.line ?: -1

  override val column: Int
    get() = position?.column ?: -1

  private var position: CachedPosition? = null
    get() = info?.actualStartOffset?.let {
      if (it != field?.offset) field = computePosition(it)
      field
    }

  private fun computePosition(offset: Int): CachedPosition? {
    if (offset < 0) return null
    val document = ProblemsView.getDocument(provider.project, file) ?: return null
    if (offset > document.textLength) return null
    val line = document.getLineNumber(offset)
    return CachedPosition(offset, line, offset - document.getLineStartOffset(line))
  }

  private class CachedPosition(val offset: Int, val line: Int, val column: Int)
}

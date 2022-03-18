// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark

import com.intellij.ide.bookmark.BookmarkBundle.message
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.colors.CodeInsightColors.BOOKMARKS_ATTRIBUTES
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.StringUtil.escapeXmlEntities
import com.intellij.ui.AppUIUtil.invokeLaterIfProjectAlive
import java.lang.ref.WeakReference

internal data class GutterLineBookmarkRenderer(val bookmark: LineBookmark) : DumbAware, GutterIconRenderer() {
  private var reference: WeakReference<RangeHighlighter>? = null

  private val manager
    get() = BookmarksManager.getInstance(bookmark.provider.project)

  private val type
    get() = manager?.getType(bookmark) ?: BookmarkType.DEFAULT

  private val layer
    get() = HighlighterLayer.ERROR + 1

  private val document
    get() = if (bookmark.line < 0) null else FileDocumentManager.getInstance().getCachedDocument(bookmark.file)

  private val markup
    get() = document?.let { DocumentMarkupModel.forDocument(it, bookmark.provider.project, false) as? MarkupModelEx }

  internal val highlighter
    get() = reference?.get() ?: markup?.allHighlighters?.find { it.gutterIconRenderer == this }

  override fun getIcon() = type.gutterIcon

  override fun getAlignment() = Alignment.RIGHT

  override fun getClickAction() = ActionUtil.getAction("ToggleBookmark")

  override fun getMiddleButtonClickAction() = ActionUtil.getAction("EditBookmark")

  override fun getPopupMenuActions() = ActionUtil.getActionGroup("popup@BookmarkContextMenu")

  override fun getAccessibleName() = message("accessible.name.icon.bookmark.0", type)

  override fun getTooltipText(): String {
    val result = StringBuilder(message("bookmark.text"))

    val mnemonic = type.let { if (it == BookmarkType.DEFAULT) null else it.mnemonic }
    mnemonic?.let { result.append(" ").append(it) }

    val description = manager?.defaultGroup?.getDescription(bookmark)
    description?.let { if (it.isNotEmpty()) result.append(": ").append(escapeXmlEntities(it)) }

    val shortcut = mnemonic?.let { getShortcut(it) } ?: getShortcut()
    shortcut?.let { if (it.isNotEmpty()) result.append(" (").append(it).append(")") }

    @Suppress("HardCodedStringLiteral")
    return result.toString()
  }

  private fun getShortcut(): String? {
    val toggle = getFirstKeyboardShortcutText("ToggleBookmark")
    return when {
      toggle.isNotEmpty() -> message("bookmark.shortcut.to.toggle", toggle)
      else -> null
    }
  }

  private fun getShortcut(mnemonic: Char): String? {
    val toggle = getFirstKeyboardShortcutText("ToggleBookmark$mnemonic")
    val jump = getFirstKeyboardShortcutText("GotoBookmark$mnemonic")
    return when {
      toggle.isNotEmpty() && jump.isNotEmpty() -> message("bookmark.shortcut.to.toggle.and.jump", toggle, jump)
      toggle.isNotEmpty() -> message("bookmark.shortcut.to.toggle", toggle)
      jump.isNotEmpty() -> message("bookmark.shortcut.to.jump", jump)
      else -> null
    }
  }

  private fun createHighlighter() {
    reference = markup?.addPersistentLineHighlighter(BOOKMARKS_ATTRIBUTES, bookmark.line, layer)?.let {
      it.gutterIconRenderer = this
      it.errorStripeTooltip = tooltipText
      WeakReference(it)
    }
  }

  private fun releaseHighlighter() {
    @Suppress("SSBasedInspection")
    highlighter?.dispose()
    reference = null
  }

  fun refreshHighlighter(release: () -> Boolean) = invokeLaterIfProjectAlive(bookmark.provider.project) {
    when (release()) {
      true -> releaseHighlighter()
      else -> highlighter?.also {
        it.gutterIconRenderer = null
        it.gutterIconRenderer = this
      } ?: createHighlighter()
    }
  }
}

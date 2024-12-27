// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
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
import org.jetbrains.annotations.Nls
import java.lang.ref.WeakReference
import javax.swing.Icon

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

  internal val highlighter: RangeHighlighter?
    get() = reference?.get() ?: markup?.allHighlighters?.find { it.gutterIconRenderer == this }

  override fun getIcon(): Icon = type.gutterIcon

  override fun getAlignment(): Alignment = Alignment.RIGHT

  override fun getClickAction(): AnAction? = ActionUtil.getAction("ToggleBookmark")

  override fun getMiddleButtonClickAction(): AnAction? = ActionUtil.getAction("EditBookmark")

  override fun getPopupMenuActions(): ActionGroup? = ActionUtil.getActionGroup("popup@BookmarkContextMenu")

  override fun getAccessibleName(): @Nls String = BookmarkBundle.message("accessible.name.icon.bookmark.0", type)

  override fun getTooltipText(): String {
    val result = StringBuilder(BookmarkBundle.message("bookmark.text"))

    val mnemonic = type.let { if (it == BookmarkType.DEFAULT) null else it.mnemonic }
    mnemonic?.let { result.append(" ").append(it) }

    val description = manager?.getGroups(bookmark)?.mapNotNull { group -> group.getDescription(bookmark) }?.singleOrNull()

    description?.let { if (it.isNotEmpty()) result.append(": ").append(escapeXmlEntities(it)) }

    val shortcut = mnemonic?.let { getShortcut(it) } ?: getShortcut()
    shortcut?.let { if (it.isNotEmpty()) result.append(" (").append(it).append(")") }

    @Suppress("HardCodedStringLiteral")
    return result.toString()
  }

  private fun getShortcut(): String? {
    val toggle = getFirstKeyboardShortcutText("ToggleBookmark")
    return when {
      toggle.isNotEmpty() -> BookmarkBundle.message("bookmark.shortcut.to.toggle", toggle)
      else -> null
    }
  }

  private fun getShortcut(mnemonic: Char): String? {
    val toggle = getFirstKeyboardShortcutText("ToggleBookmark$mnemonic")
    val jump = getFirstKeyboardShortcutText("GotoBookmark$mnemonic")
    return when {
      toggle.isNotEmpty() && jump.isNotEmpty() -> BookmarkBundle.message("bookmark.shortcut.to.toggle.and.jump", toggle, jump)
      toggle.isNotEmpty() -> BookmarkBundle.message("bookmark.shortcut.to.toggle", toggle)
      jump.isNotEmpty() -> BookmarkBundle.message("bookmark.shortcut.to.jump", jump)
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

  fun refreshHighlighter(release: () -> Boolean): Unit = invokeLaterIfProjectAlive(bookmark.provider.project) {
    when (release()) {
      true -> releaseHighlighter()
      else -> highlighter?.also {
        it.gutterIconRenderer = null
        it.gutterIconRenderer = this
      } ?: createHighlighter()
    }
  }
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.providers.LineBookmarkProvider
import com.intellij.ide.bookmark.ui.BookmarksView
import com.intellij.ide.bookmark.ui.BookmarksViewState
import com.intellij.ide.bookmark.ui.tree.GroupNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.project.LightEditActionFactory
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.OpenSourceUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JTree


internal val AnActionEvent.bookmarksManager
  get() = project?.let { BookmarksManager.getInstance(it) }

internal val AnActionEvent.bookmarksViewState
  get() = project?.let { BookmarksViewState.getInstance(it) }

internal val AnActionEvent.bookmarksToolWindow
  get() = project?.let { ToolWindowManager.getInstance(it).getToolWindow(ToolWindowId.BOOKMARKS) }

internal val AnActionEvent.bookmarksViewFromToolWindow
  get() = dataContext.getData(PlatformDataKeys.TOOL_WINDOW)?.bookmarksView

internal val AnActionEvent.bookmarksViewFromComponent
  get() = dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT)?.bookmarksView

internal val AnActionEvent.bookmarksView
  get() = bookmarksViewFromComponent ?: bookmarksViewFromToolWindow

internal val ToolWindow.bookmarksView
  get() = contentManagerIfCreated?.selectedContent?.component as? BookmarksView

private val Component.bookmarksView: BookmarksView?
  get() = this as? BookmarksView ?: parent?.bookmarksView

internal val AnActionEvent.selectedGroupNode
  get() = bookmarksView?.selectedNode as? GroupNode

internal val AnActionEvent.contextBookmark: Bookmark?
  get() {
    val editor = getData(CommonDataKeys.EDITOR) ?: getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE)
    val project = editor?.project ?: project ?: return null
    if (editor != null) {
      val provider = LineBookmarkProvider.find(project) ?: return null
      return provider.createBookmark(editor, getData(EditorGutterComponentEx.LOGICAL_LINE_AT_CURSOR))
    }
    val manager = BookmarksManager.getInstance(project) ?: return null
    val window = getData(PlatformDataKeys.TOOL_WINDOW)
    if (window?.id != ToolWindowId.PROJECT_VIEW) return null
    val tree = getData(PlatformDataKeys.CONTEXT_COMPONENT) as? JTree ?: return null
    return manager.createBookmark(TreeUtil.getLastUserObject(TreeUtil.getSelectedPathIfOne(tree)))
  }


internal val Bookmark.bookmarksManager
  get() = BookmarksManager.getInstance(provider.project)

internal val Bookmark.firstGroupWithDescription
  get() = bookmarksManager?.getGroups(this)?.firstOrNull { it.getDescription(this).isNullOrBlank().not() }


/**
 * Creates an action that navigates to a bookmark by a digit or a letter.
 */
internal fun JComponent.registerBookmarkTypeAction(parent: Disposable, type: BookmarkType) = LightEditActionFactory
  .create { it.bookmarksManager?.getBookmark(type)?.run { OpenSourceUtil.navigate(this) } }
  .registerCustomShortcutSet(CustomShortcutSet.fromString(type.mnemonic.toString()), this, parent)

/**
 * Creates an action that navigates to a selected bookmark by the EditSource shortcut.
 */
internal fun JComponent.registerEditSourceAction(parent: Disposable) = LightEditActionFactory
  .create { OpenSourceUtil.navigate(*it.getData(CommonDataKeys.NAVIGATABLE_ARRAY)) }
  .registerCustomShortcutSet(CommonShortcuts.getEditSource(), this, parent)

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.providers.LineBookmarkProvider
import com.intellij.ide.bookmark.ui.BookmarksView
import com.intellij.ide.bookmark.ui.BookmarksViewState
import com.intellij.ide.bookmark.ui.tree.GroupNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.SmartElementDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.project.LightEditActionFactory
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.util.OpenSourceUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.KeyStroke


internal val AnActionEvent.bookmarksManager
  get() = project?.let { BookmarksManager.getInstance(it) }

internal val AnActionEvent.bookmarksViewState
  get() = project?.let { BookmarksViewState.getInstance(it) }

internal val AnActionEvent.bookmarksView
  get() = getData(BookmarksView.BOOKMARKS_VIEW)

internal val AnActionEvent.bookmarkNodes: List<AbstractTreeNode<*>>?
  get() = bookmarksView?.let { getData(PlatformDataKeys.SELECTED_ITEMS)?.asList() as? List<AbstractTreeNode<*>> }

internal val AnActionEvent.selectedGroupNode
  get() = bookmarkNodes?.singleOrNull() as? GroupNode

internal val AnActionEvent.contextBookmark: Bookmark?
  get() {
    val editor = getData(CommonDataKeys.EDITOR) ?: getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE)
    val project = editor?.project ?: project ?: return null
    val manager = BookmarksManager.getInstance(project) ?: return null
    val window = getData(PlatformDataKeys.TOOL_WINDOW)
    if (window?.id == ToolWindowId.BOOKMARKS) return null

    if (place == ActionPlaces.EDITOR_TAB_POPUP || window?.id == ToolWindowId.PROJECT_VIEW) {
      // Create file bookmark
      val file = getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
      return manager.createBookmark(file)
    }

    if (editor != null) {
      val provider = LineBookmarkProvider.find(project) ?: return null
      val line = getData(EditorGutterComponentEx.LOGICAL_LINE_AT_CURSOR)
      return provider.createBookmark(editor, line)
    }

    val component = getData(PlatformDataKeys.CONTEXT_COMPONENT)
    val allowed = UIUtil.getClientProperty(component, BookmarksManager.ALLOWED) ?: (window?.id == ToolWindowId.PROJECT_VIEW)
    if (!allowed) return null

    // TODO mouse shortcuts as in gutter/LOGICAL_LINE_AT_CURSOR
    val items = getData(PlatformDataKeys.SELECTED_ITEMS)
    if (items != null && items.size > 1) return null
    val item = items?.firstOrNull() ?: getData(CommonDataKeys.PSI_ELEMENT) ?: getData(CommonDataKeys.VIRTUAL_FILE)
    return when (item) {
      is AbstractTreeNode<*> -> manager.createBookmark(item.value)
      is SmartElementDescriptor -> manager.createBookmark(item.psiElement)
      is NodeDescriptor<*> -> manager.createBookmark(item.element)
      else -> manager.createBookmark(item)
    }
  }

internal val AnActionEvent.contextBookmarks: List<Bookmark>?
  get() {
    val window = getData(PlatformDataKeys.TOOL_WINDOW)
    if (window?.id != ToolWindowId.PROJECT_VIEW) return null

    val files = getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return null
    if (files.size < 2) return null

    val manager = BookmarksManager.getInstance(window.project) ?: return null
    return files.mapNotNull { manager.createBookmark(it) }
  }


internal val Bookmark.bookmarksManager
  get() = BookmarksManager.getInstance(provider.project)

internal val Bookmark.firstGroupWithDescription
  get() = bookmarksManager?.getGroups(this)?.firstOrNull { it.getDescription(this).isNullOrBlank().not() }


/**
 * Creates and registers an action that navigates to a bookmark by a digit or a letter, if speed search is not active.
 */
internal fun JComponent.registerBookmarkTypeAction(parent: Disposable, type: BookmarkType) = GotoBookmarkTypeAction(type, true)
  .registerCustomShortcutSet(CustomShortcutSet.fromString(type.mnemonic.toString()), this, parent)

/**
 * Creates an action that navigates to a selected bookmark by the EditSource shortcut.
 */
internal fun JComponent.registerEditSourceAction(parent: Disposable) = LightEditActionFactory
  .create { OpenSourceUtil.navigate(*it.getData(CommonDataKeys.NAVIGATABLE_ARRAY)) }
  .registerCustomShortcutSet(CommonShortcuts.getEditSource(), this, parent)

internal fun JTree.registerNavigateOnEnterAction() {
  val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
  // perform previous action if the specified action is failed
  // it is needed to expand/collapse a tree node
  val oldListener = getActionForKeyStroke(enter)
  val newListener = ActionListener {
    when (val node = TreeUtil.getAbstractTreeNode(selectionPath)) {
      null -> oldListener?.actionPerformed(it)
      is GroupNode -> oldListener?.actionPerformed(it)
      else -> node.navigate(true)
    }
  }
  registerKeyboardAction(newListener, enter, JComponent.WHEN_FOCUSED)
}

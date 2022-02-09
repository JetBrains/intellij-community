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
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
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
import com.intellij.ui.speedSearch.SpeedSearchSupply
import com.intellij.util.OpenSourceUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Component
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.SwingUtilities


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
      var line = getData(EditorGutterComponentEx.LOGICAL_LINE_AT_CURSOR)
      if (line != null && place == ActionPlaces.MOUSE_SHORTCUT) {
        // fix calculated gutter line for an action called via mouse shortcut
        val gutter = getData(PlatformDataKeys.CONTEXT_COMPONENT) as? EditorGutterComponentEx
        if (gutter != null && gutter.isShowing) {
          (inputEvent as? MouseEvent)
            ?.run { locationOnScreen }
            ?.also { SwingUtilities.convertPointFromScreen(it, gutter) }
            ?.let { editor.xyToLogicalPosition(it).line }
            ?.let { if (it >= 0) line = it }
        }
      }
      return provider.createBookmark(editor, line)
    }
    val manager = BookmarksManager.getInstance(project) ?: return null
    val window = getData(PlatformDataKeys.TOOL_WINDOW)
    if (window?.id == ToolWindowId.BOOKMARKS) return null
    val component = getData(PlatformDataKeys.CONTEXT_COMPONENT)
    val allowed = UIUtil.getClientProperty(component, BookmarksManager.ALLOWED) ?: (window?.id == ToolWindowId.PROJECT_VIEW)
    return when {
      !allowed -> null
      component is JTree -> {
        val path = TreeUtil.getSelectedPathIfOne(component)
        manager.createBookmark(path)
        ?: when (val node = TreeUtil.getLastUserObject(path)) {
          is AbstractTreeNode<*> -> manager.createBookmark(node.value)
          is NodeDescriptor<*> -> manager.createBookmark(node.element)
          else -> manager.createBookmark(node)
        }
      }
      else -> manager.createBookmark(getData(CommonDataKeys.PSI_ELEMENT))
              ?: manager.createBookmark(getData(CommonDataKeys.VIRTUAL_FILE))
    }
  }


internal val Bookmark.bookmarksManager
  get() = BookmarksManager.getInstance(provider.project)

internal val Bookmark.firstGroupWithDescription
  get() = bookmarksManager?.getGroups(this)?.firstOrNull { it.getDescription(this).isNullOrBlank().not() }


/**
 * Creates and registers an action that navigates to a bookmark by a digit or a letter, if speed search is not active.
 */
internal fun JComponent.registerBookmarkTypeAction(parent: Disposable, type: BookmarkType) = createBookmarkTypeAction(type)
  .registerCustomShortcutSet(CustomShortcutSet.fromString(type.mnemonic.toString()), this, parent)

/**
 * Creates an action that navigates to a bookmark by its type, if speed search is not active.
 */
private fun createBookmarkTypeAction(type: BookmarkType) = GotoBookmarkTypeAction(type) {
  null == it.bookmarksViewFromComponent?.run { SpeedSearchSupply.getSupply(tree) }
}

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

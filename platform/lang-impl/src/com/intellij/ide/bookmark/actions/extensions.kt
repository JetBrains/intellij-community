// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.providers.LineBookmarkProvider
import com.intellij.ide.bookmark.ui.BookmarksView
import com.intellij.ide.bookmark.ui.tree.GroupNode
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Component
import javax.swing.JTree


internal val AnActionEvent.bookmarksManager
  get() = project?.let { BookmarksManager.getInstance(it) }

internal val AnActionEvent.bookmarksViewFromToolWindow
  get() = dataContext.getData(PlatformDataKeys.TOOL_WINDOW)?.bookmarksView

internal val AnActionEvent.bookmarksViewFromComponent
  get() = dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT)?.bookmarksView

internal val AnActionEvent.bookmarksView
  get() = bookmarksViewFromComponent ?: bookmarksViewFromToolWindow

private val ToolWindow.bookmarksView
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

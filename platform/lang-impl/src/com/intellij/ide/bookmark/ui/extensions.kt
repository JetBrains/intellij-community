// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.bookmark.BookmarkBundle.message
import com.intellij.ide.bookmark.BookmarkOccurrence
import com.intellij.ide.bookmark.BookmarksListProviderService
import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.bookmark.ui.tree.FolderNode
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Component
import javax.swing.tree.TreePath

internal val TreePath.asAbstractTreeNode
  get() = TreeUtil.getAbstractTreeNode(this)

internal val TreePath.findFolderNode
  get() = TreeUtil.findObjectInPath(this, FolderNode::class.java)

internal val AbstractTreeNode<*>.bookmarkOccurrence
  get() = (this as? BookmarkNode<*>)?.run { bookmarkGroup?.let { BookmarkOccurrence(it, value) } }

internal val AbstractTreeNode<*>.asDescriptor: OpenFileDescriptor?
  get() {
    val project = project ?: return null
    if (!canNavigateToSource()) return null
    val descriptor = BookmarksListProviderService.getProviders(project).firstNotNullOfOrNull { it.getDescriptor(this) }
    return descriptor ?: asVirtualFile?.let { OpenFileDescriptor(project, it) }
  }

internal val AbstractTreeNode<*>.asVirtualFile
  get() = (this as? ProjectViewNode<*>)?.virtualFile

@Suppress("DialogTitleCapitalization")
internal fun StatusText.initialize(owner: Component) {
  text = message("status.text.no.bookmarks.added")
  val shortcut = KeymapUtil.getFirstKeyboardShortcutText("ToggleBookmark")
  appendLine(when (shortcut.isBlank()) {
               true -> message("status.text.add.bookmark")
               else -> message("status.text.add.bookmark.with.shortcut", shortcut)
             })
  appendLine(message("status.text.add.bookmark.next.line"))
  ActionUtil.getAction("BookmarksView.Create")?.let { action ->
    appendLine(message("bookmark.group.create.action.text"), LINK_PLAIN_ATTRIBUTES) {
      ActionUtil.invokeAction(action, owner, "BookmarksView", null, null)
    }
  }
  appendLine("")
  appendLine(AllIcons.General.ContextHelp, message("status.text.context.help"), LINK_PLAIN_ATTRIBUTES) {
    HelpManager.getInstance().invokeHelp("bookmarks.tool.window.help")
  }
}

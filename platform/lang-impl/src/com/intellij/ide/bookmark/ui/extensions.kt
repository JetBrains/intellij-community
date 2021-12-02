// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.bookmark.BookmarkBundle.message
import com.intellij.ide.bookmark.BookmarkOccurrence
import com.intellij.ide.bookmark.BookmarksListProvider
import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES
import com.intellij.util.ui.StatusText
import java.awt.Component

internal val AbstractTreeNode<*>.bookmarkOccurrence
  get() = (this as? BookmarkNode<*>)?.run { bookmarkGroup?.let { BookmarkOccurrence(it, value) } }

internal val AbstractTreeNode<*>.asDescriptor: OpenFileDescriptor?
  get() {
    val project = project ?: return null
    if (!canNavigateToSource()) return null
    val descriptor = BookmarksListProvider.EP.getExtensions(project).firstNotNullOfOrNull { it.getDescriptor(this) }
    if (descriptor != null) return descriptor
    val node = this as? ProjectViewNode<*>
    return node?.virtualFile?.let { OpenFileDescriptor(project, it) }
  }

@Suppress("DialogTitleCapitalization")
internal fun StatusText.initialize() {
  text = message("status.text.no.bookmarks.added")
  appendLine("")
  val shortcut = KeymapUtil.getFirstKeyboardShortcutText("ToggleBookmark")
  appendLine(when (shortcut.isBlank()) {
               true -> message("status.text.add.bookmark")
               else -> message("status.text.add.bookmark.with.shortcut", shortcut)
             })
  appendLine(message("status.text.add.bookmark.next.line"))
  appendLine("")
  appendLine(AllIcons.General.ContextHelp, message("status.text.context.help"), LINK_PLAIN_ATTRIBUTES) {
    HelpManager.getInstance().invokeHelp("bookmarks.tool.window.help")
  }
}

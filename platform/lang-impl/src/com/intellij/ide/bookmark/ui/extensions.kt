// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkOccurrence
import com.intellij.ide.bookmark.BookmarksListProvider
import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.SimpleTextAttributes
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
internal fun StatusText.initialize(owner: Component) {
  text = BookmarkBundle.message("status.text.no.bookmarks.added")
  appendAction(owner, "BookmarksView.Create", "BookmarksView") {
    it.appendText(BookmarkBundle.message("status.text.add.bookmark.group.or"))
  }
  appendAction(owner, "ToggleBookmark", null) {
    it.appendText(BookmarkBundle.message("status.text.add.bookmark.to.code"))
  }
}

@Suppress("DialogTitleCapitalization")
private fun StatusText.appendAction(owner: Component, id: String, place: String?, after: (StatusText) -> Unit) {
  val action = ActionUtil.getAction(id) ?: return
  val name = action.templateText ?: return
  if (name.isBlank()) return
  appendLine("")
  when (place) {
    null -> appendText(name)
    else -> appendText(name, SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
      ActionUtil.invokeAction(action, owner, place, null, null)
    }
  }
  val shortcut = KeymapUtil.getFirstKeyboardShortcutText(action)
  if (shortcut.isNotBlank()) appendText(" ($shortcut)")
  after(appendText(" "))
}

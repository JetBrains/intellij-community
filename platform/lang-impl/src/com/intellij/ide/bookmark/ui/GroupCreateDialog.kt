// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.bookmark.BookmarkBundle.message
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.openapi.project.Project
import java.awt.Component
import javax.swing.JTextField

class GroupCreateDialog(project: Project?, parent: Component?, val manager: BookmarksManager)
  : GroupInputDialog<JTextField>(project, parent) {

  override val component = JTextField(30)

  fun showAndGetGroup(addBookmark: Boolean): BookmarkGroup? {
    GroupInputValidator(manager, emptyList())
      .apply { component.text = findValidName(message("dialog.group.new.group.name")) }
      .install(disposable, component) { component.text }
      .andRegisterOnDocumentListener(component)
      .revalidate()

    title = if (addBookmark) message("dialog.bookmark.add.title") else message("dialog.group.create.title")
    setOKButtonText(if (addBookmark) message("dialog.bookmark.add.button") else message("dialog.group.create.button"))
    return showAndGetGroup(false) {
      manager.addGroup(component.text.trim(), it)
    }
  }
}

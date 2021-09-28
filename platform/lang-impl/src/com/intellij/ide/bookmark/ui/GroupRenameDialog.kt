// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.bookmark.BookmarkBundle.message
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.openapi.project.Project
import java.awt.Component
import javax.swing.JTextField

class GroupRenameDialog(project: Project?, parent: Component?, val manager: BookmarksManager, val group: BookmarkGroup)
  : GroupInputDialog<JTextField>(project, parent) {

  override val component = JTextField(group.name, 30)

  fun showAndGetGroup(): BookmarkGroup? {
    GroupInputValidator(manager, listOf(group))
      .install(disposable, component) { component.text }
      .andRegisterOnDocumentListener(component)
      .revalidate()

    title = message("dialog.group.rename.title")
    setOKButtonText(message("dialog.group.rename.button"))
    return showAndGetGroup(group.isDefault) {
      group.apply {
        name = component.text.trim()
        isDefault = it
      }
    }
  }
}

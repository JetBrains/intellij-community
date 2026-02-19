// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.DocumentAdapter
import com.intellij.util.containers.map2Array
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicComboBoxEditor

class GroupSelectDialog(project: Project?, parent: Component?, val manager: BookmarksManager, val groups: List<BookmarkGroup>)
  : GroupInputDialog<ComboBox<String>>(project, parent) {

  override val component: ComboBox<@NlsSafe String> = ComboBox(groups.map2Array { it.name })

  private val groupName
    get() = component.editor?.item?.toString()?.trim() ?: ""

  fun showAndGetGroup(addBookmark: Boolean): BookmarkGroup? {
    val validator = GroupInputValidator(manager, groups)
      .install(disposable, component) { groupName }

    component.isEditable = true
    component.editor = object : BasicComboBoxEditor() {
      override fun createEditorComponent() = JTextField(30).apply {
        border = JBUI.Borders.empty()
        document.addDocumentListener(object : DocumentAdapter() {
          override fun textChanged(event: DocumentEvent) {
            validator.revalidate()
            if (!addBookmark) {
              when (manager.getGroup(groupName)) {
                null -> setOKButtonText(BookmarkBundle.message("dialog.group.create.button"))
                else -> setOKButtonText(BookmarkBundle.message("dialog.group.select.button"))
              }
            }
          }
        })
      }
    }
    validator.revalidate()

    title = if (addBookmark) BookmarkBundle.message("dialog.bookmark.add.title") else BookmarkBundle.message("dialog.group.select.title")
    setOKButtonText(if (addBookmark) BookmarkBundle.message("dialog.bookmark.add.button")
                    else BookmarkBundle.message("dialog.group.select.button"))
    return showAndGetGroup(false) {
      manager.getGroup(groupName)?.apply { isDefault = it } ?: manager.addGroup(groupName, it)
    }
  }
}

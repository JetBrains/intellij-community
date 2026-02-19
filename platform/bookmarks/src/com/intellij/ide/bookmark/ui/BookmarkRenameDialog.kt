// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.bookmark.BookmarkBundle
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import java.awt.Component
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

abstract class BookmarkRenameDialog<C : JComponent>(project: Project?, parent: Component?)
  : DialogWrapper(project, parent, true, IdeModalityType.IDE) {

  private val cbDefault = JCheckBox(BookmarkBundle.message("dialog.group.checkbox.label"))

  abstract val component: C

  override fun getPreferredFocusedComponent(): C = component

  override fun doValidate(): ValidationInfo? = ComponentValidator.getInstance(component).orElse(null)?.validationInfo

  override fun createCenterPanel(): JComponent? = DialogPanel(VerticalLayout(5)).apply {
    add(JLabel(BookmarkBundle.message("dialog.group.message.label")).apply { labelFor = component })
    add(component)
    add(JPanel(HorizontalLayout(10)).apply {
      add(cbDefault)
      add(JLabel(AllIcons.General.ContextHelp).apply {
        toolTipText = BookmarkBundle.message("dialog.group.help.tooltip")
      })
    })
  }

  protected fun showAndGetGroup(isDefault: Boolean, task: (Boolean) -> BookmarkGroup?): BookmarkGroup? {
    init()
    initValidation()
    cbDefault.isSelected = isDefault
    return when {
      showAndGet() -> task(cbDefault.isSelected)
      else -> null
    }
  }
}

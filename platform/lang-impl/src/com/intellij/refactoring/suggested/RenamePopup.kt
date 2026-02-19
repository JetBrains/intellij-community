// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.suggested

import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.refactoring.RefactoringBundle
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

internal class RenamePopup(oldName: String, newName: String) : JPanel(BorderLayout()) {
  private val button = object : JButton(RefactoringBundle.message("suggested.refactoring.rename.button.text")) {
    override fun isDefaultButton() = true
  }

  init {
    val buttonPanel = JPanel(BorderLayout()).apply {
      add(button, BorderLayout.EAST)
    }

    val label = JLabel(RefactoringBundle.message("suggested.refactoring.rename.popup.text", oldName, newName))
    label.border = JBUI.Borders.empty(0, 0, 0, 24)
    add(label, BorderLayout.NORTH)
    add(Box.createVerticalStrut(18))
    add(buttonPanel, BorderLayout.SOUTH)

    border = JBUI.Borders.empty(5, 2)

    button.addActionListener {
      WriteIntentReadAction.run {
        onRefactor()
      }
    }
  }

  lateinit var onRefactor: () -> Unit
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.statistics

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JTextArea

/**
 * Internal action to display the JSON representation of daily statistics.
 * This action is available in the Internal menu and is intended for debugging purposes.
 */
internal class ShowCompletionLocalStatisticsAction : AnAction(), DumbAware {

  override fun actionPerformed(e: AnActionEvent) {
    val representation = LocalStatistics.getInstance().generateRepresentation()
    StatisticsDialog(representation).show()
  }

  /**
   * Dialog to display the statistics in a scrollable text area.
   */
  private class StatisticsDialog(private val content: String) : DialogWrapper(true) {

    init {
      @Suppress("HardCodedStringLiteral")
      title = "Local Statistics"
      init()
    }

    override fun createCenterPanel(): JComponent {
      val textArea = JTextArea(content)
      textArea.isEditable = false
      return JBScrollPane(textArea).also {
        it.preferredSize = Dimension(800, 600)
      }
    }
  }
}

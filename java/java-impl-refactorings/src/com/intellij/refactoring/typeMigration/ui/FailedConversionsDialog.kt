// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.ui

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JEditorPane

// DialogWrapper was initially used
@Suppress("SplitModeApiUsage")
class FailedConversionsDialog(private val myConflictDescriptions: Array<@Nls String>, project: Project) :
  DialogWrapper(project, true) {

  companion object {
    const val VIEW_USAGES_EXIT_CODE: Int = NEXT_USER_EXIT_CODE
  }

  init {
    title = RefactoringBundle.message("problems.detected.title")
    setOKButtonText(JavaRefactoringBundle.message("ignore.button"))
    okAction.putValue(Action.MNEMONIC_KEY, 'I'.code)
    init()
  }

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, ViewUsagesAction(), CancelAction())
  }

  override fun createCenterPanel(): JComponent {
    val messagePane = JEditorPane(UIUtil.HTML_MIME, "")
    messagePane.editorKit = HTMLEditorKitBuilder.simple()
    messagePane.isEditable = false
    messagePane.margin = JBUI.insets(5)

    val builder = HtmlBuilder()
    for (conflictDescription in myConflictDescriptions) {
      builder.appendRaw(conflictDescription).br().br()
    }
    messagePane.text = builder.wrapWithHtmlBody().toString()

    return panel {
      row {
        scrollCell(messagePane)
          .label(RefactoringBundle.message("the.following.problems.were.found"), LabelPosition.TOP)
          .align(Align.FILL)
      }.resizableRow()
    }.apply {
      preferredSize = JBUI.size(500, 400)
    }
  }

  override fun getDimensionServiceKey(): String {
    return "#com.intellij.refactoring.typeMigration.ui.FailedConversionsDialog"
  }

  private inner class CancelAction : AbstractAction(RefactoringBundle.message("cancel.button")) {
    override fun actionPerformed(e: ActionEvent) {
      doCancelAction()
    }
  }

  private inner class ViewUsagesAction : AbstractAction(RefactoringBundle.message("view.usages")) {
    init {
      putValue(MNEMONIC_KEY, 'V'.code)
      putValue(DEFAULT_ACTION, true)
    }

    override fun actionPerformed(e: ActionEvent) {
      close(VIEW_USAGES_EXIT_CODE)
    }
  }
}

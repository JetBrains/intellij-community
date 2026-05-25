// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class TestJbProtocolCommandAction : DumbAwareAction() {
  @Suppress("HardCodedStringLiteral")
  override fun actionPerformed(e: AnActionEvent) {
    val dialog = TestJbProtocolCommandDialog(e.project)
    if (!dialog.showAndGet()) return
    val input = dialog.url.takeIf { it.isNotBlank() } ?: return

    val query = input.trim()
      .removePrefix("jetbrains://")
      .removePrefix("jetbrains:/")

    val owner = e.project?.let(ModalTaskOwner::project) ?: ModalTaskOwner.guess()
    val result = runWithModalProgressBlocking(owner, "Running JB Protocol Command…", TaskCancellation.cancellable()) {
      JBProtocolCommand.execute(query)
    }
    result.message?.let {
      Messages.showInfoMessage(e.project, it, "JB Protocol Result")
    }
  }
}

@Suppress("HardCodedStringLiteral")
private class TestJbProtocolCommandDialog(project: Project?) : DialogWrapper(project) {
  private val urlField = JBTextField(
    "jetbrains://open/project?git_url=https://github.com/JetBrains/phpstorm-xdebug-validation",
    60,
  )

  val url: String get() = urlField.text

  init {
    title = "Test JB Protocol Command"
    init()
  }

  override fun getPreferredFocusedComponent(): JComponent = urlField

  override fun createCenterPanel(): JComponent = panel {
    row("URL:") {
      cell(urlField).align(AlignX.FILL).resizableColumn()
    }
    row("Examples:") {
      val examples = """
        jetbrains://tool/plugin/install?id=IdeaVIM
        jetbrains://open/project?git_url=https://github.com/JetBrains/phpstorm-xdebug-validation
      """.trimIndent()
      val area = JBTextArea(examples, 2, 60).apply {
        isEditable = false
        lineWrap = false
      }
      cell(area).align(AlignX.FILL)
    }
  }
}

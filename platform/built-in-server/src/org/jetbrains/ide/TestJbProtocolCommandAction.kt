// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking

internal class TestJbProtocolCommandAction : DumbAwareAction() {
  @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization")
  override fun actionPerformed(e: AnActionEvent) {
    val input = Messages.showInputDialog(
      e.project,
      "jetbrains:// URL (or query starting with command name):",
      "Test JB Protocol Command",
      null,
      "jetbrains://idea/openProject?gitUrl=https://github.com/JetBrains/jcp-ide-test-repo",
      null,
    ) ?: return

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

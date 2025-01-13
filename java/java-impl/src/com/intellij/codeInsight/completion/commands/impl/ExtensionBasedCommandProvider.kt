// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.ApplicableCompletionCommand
import com.intellij.codeInsight.completion.commands.api.CommandProvider
import com.intellij.codeInsight.completion.commands.api.CompletionCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile


class ExtensionPointCommandProvider : CommandProvider, DumbAware {
  override fun getCommands(project: Project, editor: Editor, offset: Int, psiFile: PsiFile, originalEditor: Editor, originalOffset: Int, originalFile: PsiFile, isNonWritten: Boolean): List<CompletionCommand> {
    val completionCommands = DumbService.getDumbAwareExtensions(project, ApplicableCompletionCommand.EP_NAME)
    return completionCommands.filter {
      !(isNonWritten && !it.supportNonWrittenFiles()) && it.isApplicable(offset, psiFile, editor)
    }
  }

  override fun getId(): String {
    return "ExtensionPointCommandProvider"
  }

  override fun supportsNonWrittenFiles(): Boolean {
    return true
  }
}
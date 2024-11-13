// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.impl

import com.intellij.codeInsight.completion.commands.api.CompletionCommand
import com.intellij.codeInsight.completion.commands.api.CommandProvider
import com.intellij.codeInsight.completion.commands.api.OldCompletionCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile


class ExtensionPointCommandProvider : CommandProvider {
  override fun getCommands(project: Project, editor: Editor, offset: Int, psiFile: PsiFile, originalEditor: Editor, originalOffset: Int, originalFile: PsiFile): List<CompletionCommand> {
    val completionCommands = CompletionCommand.EP_NAME.extensionList
    return completionCommands.filter {
      if (it is OldCompletionCommand) {
        it.isApplicable(offset, psiFile, editor)
      }
      else {
        it.isApplicable(offset, psiFile)
      }
    }
  }

  override fun getId(): String {
    return "ExtensionPointCommandProvider"
  }
}
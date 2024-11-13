// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.api

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface CommandProvider {
  companion object {
    val EP_NAME = ExtensionPointName<CommandProvider>("com.intellij.codeInsight.completion.command.provider")
  }

  /**
   * must be applicable!
   */
  fun getCommands(project: Project, editor: Editor, offset: Int, psiFile: PsiFile,
                  originalEditor: Editor, originalOffset: Int, originalFile: PsiFile): List<CompletionCommand>

  fun getId(): String
}

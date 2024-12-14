// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.api

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a provider of completion commands used in code completion mechanisms.
 * Classes implementing this interface supply a collection of completion commands
 * based on the given context within a project and editor.
 */
@ApiStatus.Experimental
interface CommandProvider {
  companion object {
    val EP_NAME = ExtensionPointName<CommandProvider>("com.intellij.codeInsight.completion.command.provider")
  }


  /**
   * Retrieves a list of completion commands based on the specified context.
   * Completion commands are used to provide suggestions and actions during the code completion process.
   * Should be applicable to this context
   *
   * @param project the project in which the code completion is invoked
   * @param editor the editor instance where the code completion is triggered
   * @param offset the position within the document where the completion is invoked
   * @param psiFile the PSI file representing the file being edited
   * @param originalEditor the original editor instance, may differ in specific use cases (e.g., injected editors)
   * @param originalOffset the position within the document in the original editor
   * @param originalFile the PSI file in the context of the original editor
   * @return a list of completion commands to be executed or displayed during the code completion process
   */
  fun getCommands(project: Project, editor: Editor, offset: Int, psiFile: PsiFile,
                  originalEditor: Editor, originalOffset: Int, originalFile: PsiFile): List<CompletionCommand>

  fun getId(): String
}

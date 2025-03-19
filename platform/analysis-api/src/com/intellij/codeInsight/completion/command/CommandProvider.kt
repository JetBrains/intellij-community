// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.command

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.PossiblyDumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Represents a provider of completion commands used in code completion mechanisms.
 * Classes implementing this interface supply a collection of completion commands
 * based on the given context within a project and editor.
 *
 * Should be DumbAware to support dumb mode
 */
interface CommandProvider : PossiblyDumbAware {


  /**
   * Retrieves a list of completion commands based on the specified context.
   * Completion commands are used to provide suggestions and actions during the code completion process.
   * It should be applicable to this context
   * It should process injected elements
   *
   * @return a list of completion commands to be executed or displayed during the code completion process
   */
  fun getCommands(context: CommandCompletionProviderContext): List<CompletionCommand>

  fun getId(): String = javaClass.name

  /**
   * Indicates whether the implementation supports non-written files.
   * (For example, a command can navigate to another file)
   *
   * @return true if non-written files are supported; false otherwise.
   */
  fun supportsReadOnly(): Boolean = false
}

/**
 * Represents the context required for providing command-based completions in the editor.
 * This context includes both the original and copied states of the PSI files and editors,
 * enabling accurate suggestions during code completion operations.

 * @property project the project in which the code completion is invoked
 * @property editor the editor instance where the code completion is triggered
 * @property offset the position within the document where the completion is invoked
 * @property psiFile the PSI file representing the file being edited
 * @property originalEditor the original editor instance, may differ in specific use cases (e.g., injected editors)
 * @property originalOffset the position within the document in the original editor
 * @property originalPsiFile the PSI file in the context of the original editor
 * @property isReadOnly it is not allowed to write in this PSI file. For example, a command can navigate to another file
 *                        (e.g., an imaginary file or preview state).
 */
data class CommandCompletionProviderContext(
  val project: Project,
  val editor: Editor,
  val offset: Int,
  val psiFile: PsiFile,
  val originalEditor: Editor,
  val originalOffset: Int,
  val originalPsiFile: PsiFile,
  val isReadOnly: Boolean,
)

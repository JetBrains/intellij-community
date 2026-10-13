// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Utility for executing [ModCommand]-based completion insertions.
 * Shared between local completion ([com.intellij.codeInsight.lookup.impl.LookupImpl])
 * and remote development (FrontendInsertHandler).
 */
@ApiStatus.Internal
object ModCompletionInserter {
  /**
   * Execute a [ModCommand] for completion insertion.
   *
   * This method handles the standard insertion flow:
   * 1. Delete the completion prefix text from the document (in a write action)
   * 2. Create an [ActionContext] from the editor/file
   * 3. Execute the command via [ModCommandExecutor.executeInteractively]
   *
   * @param editor The editor where completion is happening
   * @param psiFile The PSI file being edited
   * @param prefixStart The start offset of the completion prefix
   * @param prefixEnd The end offset of the completion prefix (typically current caret position)
   * @param modCommand The [ModCommand] to execute
   */
  @RequiresEdt
  @JvmStatic
  fun executeModCommand(
    editor: Editor,
    psiFile: PsiFile,
    prefixStart: Int,
    prefixEnd: Int,
    modCommand: ModCommand
  ) {
    WriteAction.run<RuntimeException> { editor.document.deleteString(prefixStart, prefixEnd) }
    val actionContext = ActionContext.from(editor, psiFile)
    ModCommandExecutor.getInstance().executeInteractively(actionContext, modCommand, editor)
  }
}

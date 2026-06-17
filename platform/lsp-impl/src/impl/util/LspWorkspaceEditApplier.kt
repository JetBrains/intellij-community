package com.intellij.platform.lsp.impl.util

import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspIntentionAction
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.WorkspaceEdit

internal class LspWorkspaceEditApplier private constructor(private val intentionAction: LspIntentionAction) {

  @RequiresEdt
  fun applyWorkspaceEdit() {
    intentionAction.invoke(null)
  }

  companion object {
    @RequiresReadLock
    fun create(lspClient: LspClient, workspaceEdit: WorkspaceEdit): LspWorkspaceEditApplier? {
      val codeAction = CodeAction().apply { edit = workspaceEdit }
      val intentionAction = LspIntentionAction(lspClient, codeAction)
      return if (intentionAction.isAvailable()) LspWorkspaceEditApplier(intentionAction) else null
    }
  }
}
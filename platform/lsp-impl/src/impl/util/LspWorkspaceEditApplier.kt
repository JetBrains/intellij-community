package com.intellij.platform.lsp.impl.util

import com.intellij.platform.lsp.api.LspServer
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
    fun create(lspServer: LspServer, workspaceEdit: WorkspaceEdit): LspWorkspaceEditApplier? {
      val codeAction = CodeAction().apply { edit = workspaceEdit }
      val intentionAction = LspIntentionAction(lspServer, codeAction)
      return if (intentionAction.isAvailable()) LspWorkspaceEditApplier(intentionAction) else null
    }
  }
}
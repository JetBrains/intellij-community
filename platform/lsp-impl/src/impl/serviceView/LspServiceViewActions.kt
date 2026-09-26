package com.intellij.platform.lsp.impl.serviceView

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl

internal val LSP_CLIENT_DATA_KEY: DataKey<LspClient> = DataKey.create("lsp.serviceView.client")

// Toolbar action instances must not change on every toolbar update, so a single shared group is used by all descriptors
internal val lspClientServiceViewActionGroup: DefaultActionGroup by lazy {
  DefaultActionGroup(RestartLspClientServiceAction(), StopLspClientServiceAction())
}

private class RestartLspClientServiceAction :
  AnAction(LspBundle.message("action.RestartLspServerAction.text"), null, AllIcons.Actions.Restart), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getData(LSP_CLIENT_DATA_KEY) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val lspClient = e.getData(LSP_CLIENT_DATA_KEY) as? LspClientImpl ?: return
    LspClientManagerImpl.getInstanceImpl(lspClient.project).restartClient(lspClient)
  }
}

private class StopLspClientServiceAction :
  AnAction(LspBundle.message("action.services.lsp.stop.text"), null, AllIcons.Actions.Suspend), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val state = e.getData(LSP_CLIENT_DATA_KEY)?.state
    e.presentation.isEnabledAndVisible = state == LspServerState.Initializing || state == LspServerState.Running
  }

  override fun actionPerformed(e: AnActionEvent) {
    val lspClient = e.getData(LSP_CLIENT_DATA_KEY) as? LspClientImpl ?: return
    LspClientManagerImpl.getInstanceImpl(lspClient.project).stopRunningServer(lspClient)
  }
}

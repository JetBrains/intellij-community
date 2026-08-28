package com.intellij.platform.lsp.impl.serviceView

import com.intellij.execution.runners.ExecutionUtil
import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.ui.LayeredIcon
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JComponent

internal class LspClientServiceViewDescriptor(
  private val project: Project,
  private val lspClient: LspClient,
) : ServiceViewDescriptor, UiDataProvider {

  // ServiceView recreates this descriptor on each SERVICE_CHANGED event, so the presentation reflects the current client state
  private val presentation: ItemPresentation = createPresentation()

  override fun getPresentation(): ItemPresentation = presentation

  override fun getId(): String =
    lspClient.providerClass.name +
    "/" + lspClient.descriptor.presentableName +
    "/" + lspClient.descriptor.roots.joinToString(",") { it.path }

  override fun getContentComponent(): JComponent? =
    LspServiceViewSupport.getInstance(project).getOrCreateConsole(lspClient)?.getComponent()

  override fun getToolbarActions(): ActionGroup = lspClientServiceViewActionGroup

  override fun uiDataSnapshot(sink: DataSink) {
    sink[LSP_CLIENT_DATA_KEY] = lspClient
  }

  private fun createPresentation(): ItemPresentation {
    val presentationData = PresentationData()
    val state = lspClient.state

    val baseIcon = AllIcons.Webreferences.Server
    presentationData.setIcon(when (state) {
      LspServerState.Initializing -> baseIcon
      LspServerState.Running -> ExecutionUtil.withLiveIndicator(baseIcon)
      LspServerState.ShutdownNormally -> IconLoader.getDisabledIcon(baseIcon)
      LspServerState.ShutdownUnexpectedly -> LayeredIcon.layeredIcon(arrayOf(baseIcon, AllIcons.Nodes.ErrorMark))
    })

    presentationData.addText(lspClient.descriptor.presentableName, SimpleTextAttributes.REGULAR_ATTRIBUTES)

    val details = mutableListOf<@NlsSafe String>()
    lspClient.initializeResult?.serverInfo?.version?.let { details.add(it) }
    rootPostfix()?.let { details.add(it) }
    when (state) {
      LspServerState.Initializing -> details.add(LspBundle.message("services.lsp.state.initializing"))
      LspServerState.Running -> {}
      LspServerState.ShutdownNormally -> details.add(LspBundle.message("services.lsp.state.stopped"))
      LspServerState.ShutdownUnexpectedly -> details.add(LspBundle.message("services.lsp.state.terminated"))
    }
    if (details.isNotEmpty()) {
      @Suppress("HardCodedStringLiteral")
      val detailsText: @NlsSafe String = details.joinToString(separator = " ", prefix = " ")
      presentationData.addText(detailsText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    return presentationData
  }

  private fun rootPostfix(): @NlsSafe String? {
    val roots = lspClient.descriptor.roots
    val lspClients = LspClientManager.getInstance(project).getClients(lspClient.providerClass)
    return if (lspClients.size >= 2 && roots.size == 1) "…/${roots[0].name}" else null
  }
}

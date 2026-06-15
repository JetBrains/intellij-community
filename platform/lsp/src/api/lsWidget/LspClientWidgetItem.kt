// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.lsWidget

import com.intellij.icons.AllIcons
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lang.lsWidget.LanguageServicePopupSection
import com.intellij.platform.lang.lsWidget.LanguageServicePopupSection.ForCurrentFile
import com.intellij.platform.lang.lsWidget.LanguageServicePopupSection.Other
import com.intellij.platform.lang.lsWidget.LanguageServiceWidgetItem
import com.intellij.platform.lang.lsWidget.OpenSettingsAction
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.util.containers.addIfNotNull
import javax.swing.Icon

/**
 * @param icon used in the Language Services popup and also in the status bar,
 * but in the latter case, the Platform will colorize the icon to be status-bar-friendly in the current UI theme.
 * Implementations may override [statusBarIcon] if they want to provide different icons for the status bar and for the popup item.
 */
open class LspClientWidgetItem(
  protected val lspClient: LspClient,
  currentFile: VirtualFile?,
  private val icon: Icon = AllIcons.Json.Object,
  private val settingsPageClass: Class<out Configurable>? = null,
) : LanguageServiceWidgetItem() {

  override val statusBarIcon: Icon = icon

  override val statusBarTooltip: String
    get() = lspClient.descriptor.presentableName + versionPostfix

  override val isError: Boolean = lspClient.state == LspServerState.ShutdownUnexpectedly

  override val widgetActionLocation: LanguageServicePopupSection by lazy {
    if (currentFile != null &&
        currentFile.isInLocalFileSystem &&
        lspClient.descriptor.isSupportedFile(currentFile) &&
        lspClient.descriptor.roots.any { root -> VfsUtil.isAncestor(root, currentFile, true) } &&
        ProjectFileIndex.getInstance(lspClient.project).isInContent(currentFile)) {
      ForCurrentFile
    }
    else Other
  }

  protected open val widgetActionText: @NlsActions.ActionText String
    get() = when (lspClient.state) {
      LspServerState.Initializing -> LangBundle.message("language.services.widget.item.initializing", itemLabel)
      LspServerState.Running -> itemLabel
      LspServerState.ShutdownNormally -> LangBundle.message("language.services.widget.item.shutdown.normally", itemLabel)
      LspServerState.ShutdownUnexpectedly -> LangBundle.message("language.services.widget.item.shutdown.unexpectedly", itemLabel)
    }

  protected open val itemLabel: @NlsSafe String
    @Suppress("DEPRECATION")
    get() = serverLabel

  @Deprecated("Renamed to itemLabel", ReplaceWith("itemLabel"))
  protected open val serverLabel: @NlsSafe String
    get() = lspClient.descriptor.presentableName + versionPostfix + rootPostfix

  protected open val versionPostfix: @NlsSafe String
    // Maybe try shortening long version strings automatically? Example `1.36.4 (release, aarch64-apple-darwin)` -> `1.36.4…`
    get() = lspClient.initializeResult?.serverInfo?.version?.let { " $it" } ?: ""

  protected open val rootPostfix: @NlsSafe String
    get() {
      val roots = lspClient.descriptor.roots
      val lspClients = LspClientManager.getInstance(lspClient.project).getClients(lspClient.providerClass)
      return if (lspClients.size >= 2 && roots.size == 1) " …/${roots[0].name}" else ""
    }

  override fun createWidgetMainAction(): AnAction =
    settingsPageClass?.let {
      OpenSettingsAction(it, widgetActionText, icon)
    }
    ?: object : AnAction(widgetActionText, null, icon) {
      override fun actionPerformed(e: AnActionEvent) {
        // There's no single reasonable action that would work for each LSP API-based plugin.
        // The plugins are strongly recommended to override `LspIntegrationProvider.createWidgetItem()`.
        // Typical implementation:
        //     override fun createWidgetItem(...) = LspClientWidgetItem(lspClient, currentFile, fooIcon, FooConfigurable::class.java)
      }
    }

  override fun createWidgetInlineActions(): List<AnAction> {
    val actions = mutableListOf<AnAction>()

    actions.addAll(createAdditionalInlineActions())
    actions.addIfNotNull(createStopOrRestartAction())
    settingsPageClass?.let { actions.add(OpenSettingsAction(it)) }

    return actions
  }

  protected open fun createStopOrRestartAction(): AnAction? {
    return if (widgetActionLocation == ForCurrentFile) {
      RestartLspClientAction(lspClient)
    }
    else {
      when (lspClient.state) {
        LspServerState.Initializing,
        LspServerState.Running -> StopLspClientAction(lspClient)
        LspServerState.ShutdownNormally -> null
        LspServerState.ShutdownUnexpectedly -> RestartLspClientAction(lspClient)
      }
    }
  }

  protected open fun createAdditionalInlineActions(): List<AnAction> {
    if (lspClient.state != LspServerState.ShutdownUnexpectedly) return emptyList()
    val stderrAction = LspWidgetInternalService.getInstance().createShowErrorOutputAction(lspClient)
    return if (stderrAction != null) listOf(stderrAction) else emptyList()
  }
}


private class RestartLspClientAction(
  private val lspClient: LspClient,
) : AnAction(LspBundle.message("action.RestartLspServerAction.text"), null, AllIcons.Actions.StopAndRestart), DumbAware {
  override fun actionPerformed(e: AnActionEvent) = LspWidgetInternalService.getInstance().restartLspClient(lspClient)
}


private class StopLspClientAction(
  private val lspClient: LspClient,
) : AnAction(LspBundle.message("action.StopLspServerAction.text"), null, AllIcons.Actions.StopAndRestart), DumbAware {
  override fun actionPerformed(e: AnActionEvent) = LspWidgetInternalService.getInstance().stopLspClient(lspClient)
}

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
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState.Initializing
import com.intellij.platform.lsp.api.LspServerState.Running
import com.intellij.platform.lsp.api.LspServerState.ShutdownNormally
import com.intellij.platform.lsp.api.LspServerState.ShutdownUnexpectedly
import com.intellij.util.containers.addIfNotNull
import javax.swing.Icon

/**
 * @param icon used in the Language Services popup and also in the status bar,
 * but in the latter case, the Platform will colorize the icon to be status-bar-friendly in the current UI theme.
 * Implementations may override [statusBarIcon] if they want to provide different icons for the status bar and for the popup item.
 */
open class LspServerWidgetItem(
  protected val lspServer: LspServer,
  currentFile: VirtualFile?,
  private val icon: Icon = AllIcons.Json.Object,
  private val settingsPageClass: Class<out Configurable>? = null,
) : LanguageServiceWidgetItem() {

  override val statusBarIcon: Icon = icon

  override val statusBarTooltip: String
    get() = lspServer.descriptor.presentableName + versionPostfix

  override val isError: Boolean = lspServer.state == ShutdownUnexpectedly

  override val widgetActionLocation: LanguageServicePopupSection by lazy {
    if (currentFile != null &&
        currentFile.isInLocalFileSystem &&
        lspServer.descriptor.isSupportedFile(currentFile) &&
        lspServer.descriptor.roots.any { root -> VfsUtil.isAncestor(root, currentFile, true) } &&
        ProjectFileIndex.getInstance(lspServer.project).isInContent(currentFile)) {
      ForCurrentFile
    }
    else Other
  }

  protected open val widgetActionText: @NlsActions.ActionText String
    get() = when (lspServer.state) {
      Initializing -> LangBundle.message("language.services.widget.item.initializing", serverLabel)
      Running -> serverLabel
      ShutdownNormally -> LangBundle.message("language.services.widget.item.shutdown.normally", serverLabel)
      ShutdownUnexpectedly -> LangBundle.message("language.services.widget.item.shutdown.unexpectedly", serverLabel)
    }

  protected open val serverLabel: @NlsSafe String
    get() = lspServer.descriptor.presentableName + versionPostfix + rootPostfix

  protected open val versionPostfix: @NlsSafe String
    // Maybe try shortening long version strings automatically? Example `1.36.4 (release, aarch64-apple-darwin)` -> `1.36.4…`
    get() = lspServer.initializeResult?.serverInfo?.version?.let { " $it" } ?: ""

  protected open val rootPostfix: @NlsSafe String
    get() {
      val roots = lspServer.descriptor.roots
      val lspServers = LspServerManager.getInstance(lspServer.project).getServersForProvider(lspServer.providerClass)
      return if (lspServers.size >= 2 && roots.size == 1) " …/${roots[0].name}" else ""
    }

  override fun createWidgetMainAction(): AnAction =
    settingsPageClass?.let {
      OpenSettingsAction(it, widgetActionText, icon)
    }
    ?: object : AnAction(widgetActionText, null, icon) {
      override fun actionPerformed(e: AnActionEvent) {
        // There's no single reasonable action that would work for each LSP API-based plugin.
        // The plugins are strongly recommended to override `LspServerSupportProvider.getLspServerWidgetItem()`.
        // Typical implementation:
        //     override fun getLspServerWidgetItem(...) = LspServerWidgetItem(context, lspServer, fooIcon, FooConfigurable::class.java)
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
      RestartLspServerAction(lspServer)
    }
    else {
      when (lspServer.state) {
        Initializing, Running -> StopLspServerAction(lspServer)
        ShutdownNormally -> null
        ShutdownUnexpectedly -> RestartLspServerAction(lspServer)
      }
    }
  }

  protected open fun createAdditionalInlineActions(): List<AnAction> {
    if (lspServer.state != ShutdownUnexpectedly) return emptyList()
    val stderrAction = LspWidgetInternalService.getInstance().createShowErrorOutputAction(lspServer)
    return if (stderrAction != null) listOf(stderrAction) else emptyList()
  }
}


private class RestartLspServerAction(
  private val lspServer: LspServer,
) : AnAction(LspBundle.message("action.RestartLspServerAction.text"), null, AllIcons.Actions.StopAndRestart), DumbAware {
  override fun actionPerformed(e: AnActionEvent) = LspWidgetInternalService.getInstance().restartLspServer(lspServer)
}


private class StopLspServerAction(
  private val lspServer: LspServer,
) : AnAction(LspBundle.message("action.StopLspServerAction.text"), null, AllIcons.Actions.StopAndRestart), DumbAware {
  override fun actionPerformed(e: AnActionEvent) = LspWidgetInternalService.getInstance().stopLspServer(lspServer)
}

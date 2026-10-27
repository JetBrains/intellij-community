package com.intellij.platform.lsp.impl.serviceView

import com.intellij.execution.services.ServiceViewManager
import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.getServerId
import com.intellij.platform.lsp.impl.lsWidget.openLspServerErrorOutput
import org.jetbrains.annotations.VisibleForTesting
import java.lang.ref.WeakReference

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

/**
 * The server log of [lspClient]. The server log is the console of the client in the Services tool window.
 * The Services tool window is a plugin. Without it, [show] opens the server error output of the client.
 *
 * The user can stop or restart the client before [show]. Then the console of the client is gone.
 * In that case, [show] opens the server error output that the old client keeps.
 * When the old client has no server error output, [show] selects the console of the new client.
 * When there is no new client either, the status bar tells the user that the server log is not available.
 */
internal class LspServerLog(lspClient: LspClientImpl) {
  private val project: Project = lspClient.project
  private val serverId: String = lspClient.getServerId()
  private val presentableName: String = lspClient.descriptor.presentableName

  // The Notifications tool window can keep a notification for a long time. A weak reference lets the old client go.
  private val clientReference: WeakReference<LspClientImpl> = WeakReference(lspClient)

  fun show() {
    if (project.isDisposed) return

    val serviceViewManager = project.serviceOrNull<ServiceViewManager>()
    when (val location = find(servicesViewAvailable = serviceViewManager != null)) {
      is Location.Console -> serviceViewManager?.select(location.lspClient, LspServiceViewContributor::class.java, true, true)
      is Location.ErrorOutput -> openLspServerErrorOutput(location.lspClient)
      Location.NotAvailable -> StatusBar.Info.set(LspBundle.message("status.bar.server.log.not.available", presentableName), project)
    }
  }

  /** @param servicesViewAvailable `false` when the Services tool window plugin is not installed or is disabled */
  @VisibleForTesting
  fun find(servicesViewAvailable: Boolean): Location {
    val trackedClients = LspClientManagerImpl.getInstanceImpl(project).getAllClients()
    val lspClient = clientReference.get()
    return when {
      lspClient != null && lspClient in trackedClients -> liveLocation(lspClient, servicesViewAvailable)
      lspClient?.errorOutput != null -> Location.ErrorOutput(lspClient)
      else -> trackedClients.firstOrNull { it.getServerId() == serverId }?.let { liveLocation(it, servicesViewAvailable) }
              ?: Location.NotAvailable
    }
  }

  private fun liveLocation(lspClient: LspClientImpl, servicesViewAvailable: Boolean): Location =
    if (servicesViewAvailable) Location.Console(lspClient) else Location.ErrorOutput(lspClient)

  @VisibleForTesting
  sealed interface Location {
    /** The console of [lspClient] in the Services tool window. */
    data class Console(val lspClient: LspClientImpl) : Location

    /** The server error output that [lspClient] keeps. A live client can have an empty server error output. */
    data class ErrorOutput(val lspClient: LspClientImpl) : Location

    data object NotAvailable : Location
  }
}

/** Shows the [LspServerLog] from an error or warning notification of the server. */
internal class ShowLspServerLogAction(lspClient: LspClientImpl) :
  NotificationAction(LspBundle.message("action.ShowLspServerLogAction.text")), DumbAware {

  @VisibleForTesting
  val serverLog: LspServerLog = LspServerLog(lspClient)

  override fun actionPerformed(e: AnActionEvent, notification: Notification): Unit = serverLog.show()
}

/**
 * Shows the [LspServerLog] from the item of the server in the Language Services widget.
 *
 * The widget creates its items again each time it opens, so the action looks for the log only when it runs.
 */
internal class ShowLspServerLogWidgetAction(private val lspClient: LspClient) :
  AnAction(LspBundle.message("action.ShowLspServerLogWidgetAction.text"), null, AllIcons.Toolwindows.ToolWindowMessages), DumbAware {

  @VisibleForTesting
  fun serverLog(): LspServerLog = LspServerLog(lspClient as LspClientImpl)

  override fun actionPerformed(e: AnActionEvent): Unit = serverLog().show()
}

package com.intellij.platform.lsp.impl.serviceView

import com.intellij.execution.services.ServiceEventListener
import com.intellij.execution.services.ServiceEventListener.ServiceEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import java.util.concurrent.ConcurrentHashMap

private val logger = logger<LspServiceViewSupport>()

/**
 * Keeps per-[LspClient] consoles and translates [LspClientManagerListener] events
 * into Services tool window ([ServiceEventListener]) events.
 *
 * Console lifetime matches the client's lifetime in [LspClientManagerImpl]'s list: a console survives an unexpected shutdown
 * (the client stays visible as 'terminated') and is disposed when the client is explicitly stopped/restarted or the project closes.
 */
@Service(Service.Level.PROJECT)
internal class LspServiceViewSupport(private val project: Project) : Disposable {
  companion object {
    fun getInstance(project: Project): LspServiceViewSupport = project.service()

    /**
     * Never-throwing accessor for hot paths like the LSP protocol reader/writer threads: an exception thrown there
     * would break the LSP connection. Doesn't create the service, so it is also safe during project shutdown.
     */
    fun getInstanceIfCreated(project: Project): LspServiceViewSupport? =
      if (project.isDisposed) null else project.serviceIfCreated<LspServiceViewSupport>()
  }

  private val consoles = ConcurrentHashMap<LspClient, LspClientConsole>()

  init {
    LspClientManager.getInstance(project).addListener(
      listener = object : LspClientManagerListener {
        override fun clientAdded(lspClient: LspClient) {
          printSafely(lspClient) {
            it.printLifecycle(LspBundle.message("services.lsp.console.server.starting", lspClient.descriptor.presentableName))
          }
          fireServiceEvent(ServiceEvent.createServiceAddedEvent(lspClient, LspServiceViewContributor::class.java, null))
        }

        override fun serverStateChanged(lspClient: LspClient) {
          printSafely(lspClient) { console ->
            when (lspClient.state) {
              LspServerState.Initializing -> {}
              LspServerState.Running -> {
                val serverInfo = lspClient.initializeResult?.serverInfo
                val nameAndVersion = listOfNotNull(serverInfo?.name ?: lspClient.descriptor.presentableName, serverInfo?.version)
                  .joinToString(" ")
                console.printLifecycle(LspBundle.message("services.lsp.console.server.initialized", nameAndVersion))
              }
              LspServerState.ShutdownNormally ->
                console.printLifecycle(LspBundle.message("services.lsp.console.server.stopped"))
              LspServerState.ShutdownUnexpectedly ->
                console.printLifecycle(LspBundle.message("services.lsp.console.server.terminated"), error = true)
            }
          }
          fireServiceEvent(ServiceEvent.createEvent(ServiceEventListener.EventType.SERVICE_CHANGED, lspClient,
                                                    LspServiceViewContributor::class.java))
        }

        override fun clientRemoved(lspClient: LspClient) {
          consoles.remove(lspClient)?.let { Disposer.dispose(it) }
          fireServiceEvent(ServiceEvent.createEvent(ServiceEventListener.EventType.SERVICE_REMOVED, lspClient,
                                                    LspServiceViewContributor::class.java))
        }
      },
      parentDisposable = this,
    )

    // Consoles for the clients that started before this service was created (they missed the `clientAdded` event)
    LspClientManagerImpl.getInstanceImpl(project).getAllClients().forEach { getOrCreateConsole(it) }
  }

  /**
   * Returns `null` for a client that is no longer tracked by [LspClientManagerImpl]
   * (so that late messages don't recreate a console for a removed client) or when this service is already disposed.
   */
  fun getOrCreateConsole(lspClient: LspClient): LspClientConsole? {
    consoles[lspClient]?.let { return it }

    if (!LspClientManagerImpl.getInstanceImpl(project).getAllClients().contains(lspClient)) return null

    val newConsole = LspClientConsole(project)
    val existing = consoles.putIfAbsent(lspClient, newConsole)
    if (existing != null) {
      Disposer.dispose(newConsole)
      return existing
    }
    if (!Disposer.tryRegister(this, newConsole)) {
      consoles.remove(lspClient, newConsole)
      Disposer.dispose(newConsole)
      return null
    }
    return newConsole
  }

  private fun printSafely(lspClient: LspClient, print: (LspClientConsole) -> Unit) {
    try {
      getOrCreateConsole(lspClient)?.let(print)
    }
    catch (e: Exception) {
      logger.warn("Failed to print to the LSP console", e)
    }
  }

  private fun fireServiceEvent(event: ServiceEvent) {
    if (!project.isDisposed) {
      project.messageBus.syncPublisher(ServiceEventListener.TOPIC).handle(event)
    }
  }

  override fun dispose() {
    consoles.clear()
  }
}

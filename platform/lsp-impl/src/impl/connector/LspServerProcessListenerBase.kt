package com.intellij.platform.lsp.impl.connector

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.application.ReadAction
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl

internal open class LspServerProcessListenerBase(private val lspClient: LspClientImpl) : ProcessListener {

  override fun startNotified(event: ProcessEvent) = lspClient.logInfo("LSP server process started: ${event.processHandler}")

  override fun processTerminated(event: ProcessEvent) {
    val text = "Exit code: ${event.exitCode}\nCommand line: ${event.processHandler}"
    lspClient.logInfo("LSP server process terminated. $text")

    val lspServerManager = ReadAction.computeBlocking<LspClientManagerImpl?, Throwable> {
      if (!lspClient.project.isDisposed) LspClientManagerImpl.getInstanceImpl(lspClient.project) else null
    }
    lspServerManager?.handleMaybeUnexpectedServerStop(lspClient, text)
  }
}

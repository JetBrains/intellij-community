package com.intellij.platform.lsp.impl.connector

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.application.ReadAction
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.LspServerManagerImpl

internal open class LspServerProcessListenerBase(private val lspServer: LspServerImpl) : ProcessListener {

  override fun startNotified(event: ProcessEvent) = lspServer.logInfo("LSP server process started: ${event.processHandler}")

  override fun processTerminated(event: ProcessEvent) {
    val text = "Exit code: ${event.exitCode}\nCommand line: ${event.processHandler}"
    lspServer.logInfo("LSP server process terminated. $text")

    val lspServerManager = ReadAction.compute<LspServerManagerImpl?, Throwable> {
      if (!lspServer.project.isDisposed) LspServerManagerImpl.getInstanceImpl(lspServer.project) else null
    }
    lspServerManager?.handleMaybeUnexpectedServerStop(lspServer, text)
  }
}

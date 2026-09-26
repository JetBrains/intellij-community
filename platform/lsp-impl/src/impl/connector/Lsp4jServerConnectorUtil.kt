package com.intellij.platform.lsp.impl.connector

import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.platform.lsp.impl.LspClientImpl

internal fun stopProcess(processHandler: ProcessHandler, lspClient: LspClientImpl) {
  if (!processHandler.isProcessTerminated) {
    lspClient.logInfo("Stopping LSP server process: $processHandler")
    ExecutionManagerImpl.stopProcess(processHandler)
    if (!processHandler.waitFor(1000)) {
      // ExecutionManagerImpl.stopProcess first tries to shut down the process gracefully,
      // but sometimes the process ignores the CTRL+C signal.
      // So, if the process is not finished within a second,
      // the second call to stopProcess will kill it if it is a KillableProcess.
      lspClient.logInfo("Failed to stop LSP server process gracefully in 1 second. Killing process: $processHandler")
      ExecutionManagerImpl.stopProcess(processHandler)
    }
  }
}
package com.intellij.platform.lsp.impl.connector

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Key
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.serviceView.LspClientConsole
import com.intellij.platform.lsp.impl.serviceView.LspServiceViewSupport

internal open class LspServerProcessListenerBase(private val lspClient: LspClientImpl) : ProcessListener {

  override fun startNotified(event: ProcessEvent) = lspClient.logInfo("LSP server process started: ${event.processHandler}")

  override fun processTerminated(event: ProcessEvent) {
    val text = "Exit code: ${event.exitCode}\nCommand line: ${event.processHandler}"
    lspClient.logInfo("LSP server process terminated. $text")

    // The process handler calls processTerminated after it has read all the output, so no output comes after the flush
    console()?.flushProcessOutput()

    val manager = ReadAction.computeBlocking<LspClientManagerImpl?, Throwable> {
      if (!lspClient.project.isDisposed) LspClientManagerImpl.getInstanceImpl(lspClient.project) else null
    }
    manager?.handleMaybeUnexpectedServerStop(lspClient, text)
  }

  /**
   * Prints a raw chunk of the server process output to the console of the client in the Services tool window.
   * Give it each chunk without a change: the console puts the chunks together into lines.
   */
  protected fun printProcessOutputToConsole(event: ProcessEvent, outputType: Key<*>) {
    console()?.printProcessOutput(event.text, outputType)
  }

  private fun console(): LspClientConsole? {
    val project = lspClient.project
    return if (project.isDisposed) null else LspServiceViewSupport.getInstance(project).getOrCreateConsole(lspClient)
  }
}

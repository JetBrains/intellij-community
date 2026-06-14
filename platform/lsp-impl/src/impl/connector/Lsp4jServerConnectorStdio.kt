package com.intellij.platform.lsp.impl.connector

import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Key
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.util.ReflectionUtil
import com.intellij.util.io.IOUtil
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets

internal class Lsp4jServerConnectorStdio(private val lspClient: LspClientImpl) : Lsp4jServerConnector(lspClient) {
  private val processHandler: BaseProcessHandler<*> = lspClient.descriptor.startServerProcess()

  private val processListener: LspServerProcessListener = LspServerProcessListener(lspClient, ::logStdErr).also {
    processHandler.addProcessListener(it)
    processHandler.startNotify()
  }
  override val ideToServerStream: OutputStream = processHandler.processInput
  override val serverToIdeStream: InputStream = processListener.pipedInputStream

  override fun prepareConnect() {}

  override fun isConnectionAlive(): Boolean = processHandler.isStartNotified && !processHandler.isProcessTerminated

  override fun disconnect() {
    if (!processHandler.isProcessTerminated) {
      lspClient.logInfo("Stopping LSP server process: ${processHandler.commandLineForLog}")
      ExecutionManagerImpl.stopProcess(processHandler)
    }
  }
}

private class LspServerProcessListener(private val lspClient: LspClientImpl, private val logStdErr: (String) -> Unit) :
  LspServerProcessListenerBase(lspClient) {
  private val pipedOutputStream: PipedOutputStream = PipedOutputStream()
  private val outputStreamWriter: OutputStreamWriter = OutputStreamWriter(pipedOutputStream, StandardCharsets.UTF_8)
  val pipedInputStream: PipedInputStream = PipedInputStream(pipedOutputStream)

  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
    if (ProcessOutputType.isStdout(outputType)) {
      try {
        outputStreamWriter.write(event.text)
        outputStreamWriter.flush()
      }
      catch (e: IOException) {
        val debugInfo = ReflectionUtil.dumpFields(PipedInputStream::class.java, pipedInputStream,
                                                  "readSide", "writeSide", "closedByReader", "closedByWriter")
        lspClient.logError("Problem proxying data to the listener: ${e.message}\n" +
                           "Stopping LSP server process: ${event.processHandler}\n" +
                           debugInfo)

        ExecutionManagerImpl.stopProcess(event.processHandler)
      }
    }
    else if (ProcessOutputType.isStderr(outputType)) {
      val text = event.text.trimEnd().takeIf { it.isNotEmpty() } ?: return
      lspClient.logInfo("STDERR: ${text}")
      lspClient.appendServerErrorOutput(text)
      logStdErr(text)
    }
  }

  override fun processTerminated(event: ProcessEvent) {
    IOUtil.closeSafe(thisLogger(), outputStreamWriter, pipedOutputStream)
    super.processTerminated(event)
  }
}

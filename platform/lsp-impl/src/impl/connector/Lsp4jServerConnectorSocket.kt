package com.intellij.platform.lsp.impl.connector

import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Key
import com.intellij.platform.lsp.api.LspCommunicationChannel
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.intellij.util.io.IOUtil
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket

internal class Lsp4jServerConnectorSocket(private val lspClient: LspClientImpl) : Lsp4jServerConnector(lspClient) {
  private lateinit var socket: Socket
  private lateinit var outputStream: DataOutputStream
  private lateinit var inputStream: DataInputStream
  private lateinit var processHandler: BaseProcessHandler<*>
  private val socketInfo: LspCommunicationChannel.Socket = lspClient.descriptor.lspCommunicationChannel as LspCommunicationChannel.Socket

  init {
    if (socketInfo.startProcess) {
      processHandler = lspClient.descriptor.startServerProcess()
      processHandler.addProcessListener(object : LspServerProcessListenerBase(lspClient) {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          val text = event.text.trimEnd().takeIf { it.isNotEmpty() } ?: return
          lspClient.logInfo("${if (ProcessOutputType.isStderr(outputType)) "STDERR" else "STDOUT"}: ${event.text}")
          if (ProcessOutputType.isStderr(outputType)) {
            lspClient.appendServerErrorOutput(text)
            logStdErr(text)
          }
        }
      })
      processHandler.startNotify()
    }
  }

  override val ideToServerStream: OutputStream
    get() = if (::outputStream.isInitialized) outputStream else OutputStream.nullOutputStream()


  override val serverToIdeStream: InputStream
    get() = if (::inputStream.isInitialized) inputStream else InputStream.nullInputStream()

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  override fun prepareConnect() {
    var lastException: Throwable? = null

    if (socketInfo.startProcess) {
      for (i in 1..10) {
        if (!processHandler.process.isAlive || ::socket.isInitialized) break
        try {
          connectSocket()
          lastException = null
          break
        }
        catch (e: Throwable) {
          lastException = e
          lspClient.logDebug("Attempt $i: failed to create a socket connection (port=${socketInfo.port}): ${e.message}")
          Thread.sleep(1000)
        }
      }
    }
    else {
      connectSocket()
      lastException = null
    }

    if (lastException != null) {
      lspClient.logWarn("All attempts to create a socket connection failed (port=${socketInfo.port})")
      throw lastException
    }
  }

  private fun connectSocket() {
    socket = Socket(InetAddress.getLoopbackAddress().hostAddress, socketInfo.port)
    outputStream = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
    inputStream = DataInputStream(BufferedInputStream(socket.getInputStream()))
  }

  override fun isConnectionAlive(): Boolean =
    ::socket.isInitialized &&
    ::outputStream.isInitialized &&
    ::inputStream.isInitialized &&
    !socket.isClosed &&
    if (socketInfo.startProcess) processHandler.isStartNotified && !processHandler.isProcessTerminated else true

  override fun disconnect() {
    if (::socket.isInitialized && !socket.isClosed)
      IOUtil.closeSafe(thisLogger(), socket) // outputStream and inputStream are automatically closed with socket

    if (::processHandler.isInitialized && !processHandler.isProcessTerminated) {
      lspClient.logInfo("Stopping LSP server process: $processHandler")
      ExecutionManagerImpl.stopProcess(processHandler)
    }
  }
}

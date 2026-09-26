package com.intellij.platform.lsp.impl.connector

import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Key
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.util.ReflectionUtil
import java.io.Closeable
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

  override fun releaseServerToIdeStream() {
    processListener.stopStreamForwarding()
  }

  override fun disconnect() {
    // Release serverToIdeStream before waiting for the process to stop: otherwise the process output
    // reader may be blocked writing into a full buffer, `processTerminated` would never fire, and the process
    // (and its threads) would leak (IJPL-250254).
    processListener.stopStreamForwarding()
    stopProcess(processHandler, lspClient)
  }
}

private class LspServerProcessListener(private val lspClient: LspClientImpl, private val logStdErr: (String) -> Unit) :
  LspServerProcessListenerBase(lspClient) {
  private val pipedOutputStream: PipedOutputStream = PipedOutputStream()
  private val outputStreamWriter: OutputStreamWriter = OutputStreamWriter(pipedOutputStream, StandardCharsets.UTF_8)
  val pipedInputStream: PipedInputStream = PipedInputStream(pipedOutputStream)

  /** Set once serverToIdeStream has been closed on purpose, so that a resulting write failure is expected, not an error. */
  @Volatile
  private var streamForwardingStopped = false

  /**
   * Stops re-buffering the server output into serverToIdeStream. Closing the write side immediately wakes
   * both a process output reader blocked in [PipedInputStream.awaitSpace] on a full buffer (it gets an
   * [IOException]) and the LSP listener blocked in `read` (it gets EOF). We close the underlying stream
   * ([pipedOutputStream]) directly rather than the wrapping [outputStreamWriter], whose `close` would flush first
   * and could itself block on the full buffer; closing the stream does not flush, so it cannot block. Safe to
   * call repeatedly and from any thread (closing an already-closed stream is a no-op). Without this, a stalled
   * reader lets the output reader block forever, `processTerminated` never fires, and the whole process leaks (IJPL-250254).
   */
  fun stopStreamForwarding() {
    streamForwardingStopped = true
    closeQuietly(pipedOutputStream)
  }

  override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
    if (ProcessOutputType.isStdout(outputType)) {
      if (streamForwardingStopped) {
        // Forwarding was already stopped on purpose because the LSP listener stopped reading: nothing would
        // drain a write and there is nothing to flush, so skip the write entirely. Drop the trailing server
        // output and make sure the process is going down (IJPL-250254).
        lspClient.logInfo("Dropping server output received after the listener stopped")
        stopProcess(event.processHandler, lspClient)
        return
      }
      try {
        outputStreamWriter.write(event.text)
        outputStreamWriter.flush()
      }
      catch (e: IOException) {
        if (streamForwardingStopped) {
          // Forwarding was stopped on another thread while this write was in flight — the same expected case
          // as the up-front check above, only observed as a write failure. Drop the trailing output.
          lspClient.logInfo("Dropping server output received after the listener stopped: ${e.message}")
        }
        else {
          val debugInfo = ReflectionUtil.dumpFields(PipedInputStream::class.java, pipedInputStream,
                                                    "readSide", "writeSide", "closedByReader", "closedByWriter")
          lspClient.logError("Problem proxying data to the listener: ${e.message}\n" + debugInfo)
        }
        stopProcess(event.processHandler, lspClient)
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
    // No more stdout is expected after termination, but set the flag first anyway: closing the stream below
    // makes any racing write fail, and we want that treated as expected output rather than logged as an error.
    streamForwardingStopped = true
    // outputStreamWriter goes through closeQuietly, not a plain close(): once stopStreamForwarding has closed
    // the stream, the writer's flush throws — an expected failure here.
    closeQuietly(outputStreamWriter, pipedOutputStream, pipedInputStream)
    super.processTerminated(event)
  }

  private fun closeQuietly(vararg closeables: Closeable) {
    for (closeable in closeables) {
      try {
        closeable.close()
      }
      catch (e: IOException) {
        thisLogger().debug("Failed to close ${closeable.javaClass.simpleName}", e)
      }
    }
  }
}

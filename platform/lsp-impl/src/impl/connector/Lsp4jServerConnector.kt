package com.intellij.platform.lsp.impl.connector

import com.google.gson.JsonParseException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.Lsp4jServer
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.logging.LanguageServiceLogger
import com.intellij.platform.lsp.impl.logging.LanguageServiceLoggerService
import com.intellij.platform.lsp.impl.serviceView.LspServiceViewSupport
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageProducer
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.StringReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private val logger = logger<Lsp4jServerConnector>()

private val defaultMessageToFixRegex =
  Regex("\\{\"jsonrpc\":\"2.0\",(\"method\":\"exit\"|\"id\":\"[^\"]+\",\"method\":\"shutdown\")(,\"params\":null)}")

internal abstract class Lsp4jServerConnector protected constructor(private val lspClient: LspClientImpl) {
  private val descriptor: LspClientDescriptor = lspClient.descriptor
  private val lsp4jClient: Lsp4jClient = descriptor.createLsp4jClient(lspClient.serverNotificationsHandler)
  lateinit var lsp4jServer: Lsp4jServer

  protected abstract val ideToServerStream: OutputStream
  protected abstract val serverToIdeStream: InputStream

  private var lsCommunicationLogger: LanguageServiceLogger? = null

  protected abstract fun prepareConnect()

  protected abstract fun isConnectionAlive(): Boolean

  protected abstract fun disconnect()

  /**
   * Called on the LSP listener thread right after it has stopped reading [serverToIdeStream], either
   * because the server closed the stream (clean EOF) or because the listener loop failed. Implementations
   * that re-buffer the server output must release [serverToIdeStream] here so that the process output reader
   * can never stay blocked writing into a full buffer while nobody drains it. Otherwise that reader thread
   * stays blocked, the process handler never observes the process ending (`processTerminated` never fires),
   * so neither the handler and its reader threads nor the external server process (a separate executable)
   * are ever cleaned up — and killing that OS process by hand would not help, because the thread is stuck
   * writing into an in-process Java pipe, not into the process (IJPL-250254).
   */
  protected open fun releaseServerToIdeStream() {}

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  internal fun connect(onSuccess: (InitializeResult) -> Unit) {
    prepareConnect()

    val messageJsonHandler = createMessageJsonHandler()
    val remoteEndpoint = RemoteEndpoint(
      StreamMessageConsumer(ideToServerStream, messageJsonHandler), ServiceEndpoints.toEndpoint(lsp4jClient))
    messageJsonHandler.methodProvider = remoteEndpoint
    LspClientManagerImpl.getInstanceImpl(lspClient.project).let { manager ->
      // get manager before touching lsp4jServer to avoid leaking partial state in case of AlreadyDisposedException
      lsp4jServer = ServiceEndpoints.toServiceObject(remoteEndpoint, descriptor.lsp4jServerClass)
      lsp4jServer = if (descriptor is Lsp4jServerWrapperCreator) descriptor.wrapLsp4jServer(lsp4jServer) else lsp4jServer
      lsp4jServer = manager.wrapLsp4jServer(lspClient, lsp4jServer)
    }

    ApplicationManager.getApplication().executeOnPooledThread {
      ConcurrencyUtil.runUnderThreadName("LSP Listener: $descriptor") {
        logger.debug("$descriptor: LSP server listener thread started")
        if (LanguageServiceLoggerService.isDebugLogEnabled()) {
          lsCommunicationLogger = LanguageServiceLoggerService.getInstance().connect(descriptor.presentableName, true)
        }
        try {
          StreamMessageProducer(serverToIdeStream, messageJsonHandler).use { messageProducer ->
            messageProducer.listen(remoteEndpoint)
          }
        }
        catch (e: Throwable) {
          lspClient.appendServerErrorOutput(e.stackTraceToString())
          logger.error(descriptor.toString(), e)
        }
        finally {
          logger.debug("$descriptor: LSP server listener thread finished")
          // This thread was the only consumer of serverToIdeStream. Now that it stopped reading, release
          // the stream so that the process output reader cannot block forever writing into a full buffer
          // while the server process is still alive (IJPL-250254).
          try {
            releaseServerToIdeStream()
          }
          catch (e: Throwable) {
            logger.warn("$descriptor: failed to release the serverToIdeStream stream", e)
          }
          val manager = ReadAction.computeBlocking<LspClientManagerImpl?, Throwable> {
            if (!lspClient.project.isDisposed) LspClientManagerImpl.getInstanceImpl(lspClient.project) else null
          }
          val text = "${descriptor.lspCommunicationChannel.javaClass.simpleName} connection closed"
          // handleMaybeUnexpectedServerStop normally tires to run shutdown & exit, it's important NOT to do it in this finally block
          manager?.handleMaybeUnexpectedServerStop(lspClient, text)
        }
      }
    }

    initializeServer(onSuccess)
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  private fun initializeServer(onSuccess: (InitializeResult) -> Unit) {
    logger.debug("$descriptor: initializing LSP server")

    var error: Throwable? = null
    val countDownLatch = CountDownLatch(1)

    lsp4jServer.initialize(descriptor.createInitializeParams())
      .whenComplete { result: InitializeResult?, th: Throwable? ->
        if (result != null) {
          lsp4jServer.initialized(InitializedParams())
          onSuccess(result)
        }
        else {
          error = th ?: RuntimeException("No InitializeResult")
        }
        countDownLatch.countDown()
      }

    val timeoutInSeconds = RegistryManager.getInstance().get("lsp.server.connect.timeout").asInteger()
    val success = countDownLatch.await(timeoutInSeconds.toLong(), TimeUnit.SECONDS)
    if (!success) {
      throw RuntimeException("'initialized' response not received from the server in $timeoutInSeconds seconds")
    }
    error?.let { throw LspInitializationException("LSP server failed to initialize", it) }
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  internal fun shutdownExitDisconnect(graceful: Boolean) {
    try {
      // On an unexpected stop the listener thread is no longer reading serverToIdeStream, so the shutdown response
      // can never arrive and `shutdown().get(...)` could only time out — skip it and just disconnect.
      // (Alternatively, we could send shutdown without waiting for a response.)
      if (graceful) {
        gracefulShutdownAndExit()
      }
    }
    finally {
      disconnectAndNotifyStopped()
    }
  }

  private fun gracefulShutdownAndExit() {
    try {
      if (::lsp4jServer.isInitialized && isConnectionAlive()) {
        val future = lsp4jServer.shutdown()
        future.get(10, TimeUnit.SECONDS)
      }
    }
    catch (e: Exception) {
      logger.warn("$descriptor: `shutdown` request failed: $e")
    }
    finally {
      if (::lsp4jServer.isInitialized && isConnectionAlive()) {
        lsp4jServer.exit()
      }
    }
  }

  private fun disconnectAndNotifyStopped() {
    try {
      lsCommunicationLogger?.let { LanguageServiceLoggerService.getInstance().disconnect(it) }
      lsCommunicationLogger = null
      disconnect()
    }
    finally {
      descriptor.lspServerListener?.serverStopped(lspClient.state == LspServerState.ShutdownNormally)
    }
  }

  private fun createMessageJsonHandler(): MessageJsonHandler {
    val lsp4jServerClass = descriptor.lsp4jServerClass
    val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap(ServiceEndpoints.getSupportedMethods(lsp4jServerClass))
    supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(lsp4jClient.javaClass))
    return object : MessageJsonHandler(supportedMethods, { it.disableHtmlEscaping() }) {
      override fun serialize(message: Message): String {
        val serialized = super.serialize(message)
        val fixed = fixMessage(serialized)
        lsCommunicationLogger?.logOutbound(fixed)
        printTrafficSafely(outbound = true, message, fixed)
        return fixed
      }

      private val messageToFixRegex: Regex = descriptor.asSafely<LspMessageFixRegexProvider>()?.messageToFixRegex
                                             ?: defaultMessageToFixRegex

      // https://github.com/eclipse-lsp4j/lsp4j/issues/655
      private fun fixMessage(input: String): String {
        val m = messageToFixRegex.matchEntire(input) ?: return input
        val paramsRange = m.groups[2]!!.range
        return input.removeRange(paramsRange)
      }

      @Throws(JsonParseException::class)
      override fun parseMessage(input: Reader): Message? {
        val content = input.readText()
        lsCommunicationLogger?.logInbound(content)
        val message = super.parseMessage(StringReader(content))
        if (message != null) {
          printTrafficSafely(outbound = false, message, content)
        }
        return message
      }
    }
  }

  /**
   * Must never throw: an exception thrown from [MessageJsonHandler.serialize]/[MessageJsonHandler.parseMessage]
   * would break the LSP connection.
   */
  private fun printTrafficSafely(outbound: Boolean, message: Message, json: String) {
    try {
      LspServiceViewSupport.getInstanceIfCreated(lspClient.project)?.getOrCreateConsole(lspClient)?.printTraffic(outbound, message, json)
    }
    catch (e: Exception) {
      logger.warn("Failed to print LSP traffic to the console", e)
    }
  }

  protected fun logStdErr(message: CharSequence): Unit? = lsCommunicationLogger?.logError(message)
}

internal class LspInitializationException(message: String, cause: Throwable) : RuntimeException(message, cause)

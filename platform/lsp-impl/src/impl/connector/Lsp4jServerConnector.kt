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
import com.intellij.util.ConcurrencyUtil
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

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  internal fun connect(onSuccess: (InitializeResult) -> Unit) {
    prepareConnect()

    val messageJsonHandler = createMessageJsonHandler()
    val remoteEndpoint = RemoteEndpoint(
      StreamMessageConsumer(ideToServerStream, messageJsonHandler), ServiceEndpoints.toEndpoint(lsp4jClient))
    messageJsonHandler.methodProvider = remoteEndpoint
    val clientManager = LspClientManagerImpl.getInstanceImpl(lspClient.project)
    lsp4jServer = ServiceEndpoints.toServiceObject(remoteEndpoint, descriptor.lsp4jServerClass)
    lsp4jServer = if (descriptor is Lsp4jServerWrapperCreator) descriptor.wrapLsp4jServer(lsp4jServer) else lsp4jServer
    lsp4jServer = clientManager.wrapLsp4jServer(lspClient, lsp4jServer)

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
          val manager = ReadAction.computeBlocking<LspClientManagerImpl?, Throwable> {
            if (!lspClient.project.isDisposed) LspClientManagerImpl.getInstanceImpl(lspClient.project) else null
          }
          val text = "${descriptor.lspCommunicationChannel.javaClass.simpleName} connection closed"
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
  internal fun shutdownExitDisconnect() {
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
      try {
        if (::lsp4jServer.isInitialized && isConnectionAlive()) {
          lsp4jServer.exit()
        }
      }
      finally {
        try {
          lsCommunicationLogger?.let { LanguageServiceLoggerService.getInstance().disconnect(it) }
          lsCommunicationLogger = null
          disconnect()
        }
        finally {
          descriptor.lspServerListener?.serverStopped(lspClient.state == LspServerState.ShutdownNormally)
        }
      }
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
        return fixed
      }

      private val messageToFixRegex =
        Regex("\\{\"jsonrpc\":\"2.0\",(\"method\":\"exit\"|\"id\":\"[^\"]+\",\"method\":\"shutdown\")(,\"params\":null)}")

      // https://github.com/eclipse-lsp4j/lsp4j/issues/655
      private fun fixMessage(input: String): String {
        val m = messageToFixRegex.matchEntire(input) ?: return input
        val paramsRange = m.groups[2]!!.range
        return input.removeRange(paramsRange)
      }

      @Throws(JsonParseException::class)
      override fun parseMessage(input: Reader): Message? {
        val logger = lsCommunicationLogger
        if (logger != null) {
          val content = input.readText()
          logger.logInbound(content)
          return super.parseMessage(StringReader(content))
        }
        return super.parseMessage(input)
      }
    }
  }

  protected fun logStdErr(message: CharSequence): Unit? = lsCommunicationLogger?.logError(message)
}

internal class LspInitializationException(message: String, cause: Throwable) : RuntimeException(message, cause)

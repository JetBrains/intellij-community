package com.intellij.platform.lsp.common

import com.intellij.execution.process.SelfKiller
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.AppExecutorUtil
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.ServerInfo
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.jsonrpc.Endpoint
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

private val LOG = logger<FakeLspServer>()

internal class ExpectationHolder {
  private val lock = Any()
  private val requests = mutableListOf<ExpectedRequest>()
  private val notifications = mutableListOf<ExpectedNotification>()

  fun addRequest(request: ExpectedRequest) {
    synchronized(lock) { requests.add(request) }
  }

  fun removeRequest(request: ExpectedRequest) {
    synchronized(lock) { requests.remove(request) }
  }

  fun addNotification(notification: ExpectedNotification) {
    synchronized(lock) { notifications.add(notification) }
  }

  fun removeNotification(notification: ExpectedNotification) {
    synchronized(lock) { notifications.remove(notification) }
  }

  fun matchRequest(method: String, parameter: Any?): ExpectedRequest? {
    synchronized(lock) {
      val match = requests.find { it.method == method && it.predicate(parameter) } ?: return null
      requests.remove(match)
      match.deferred.complete(Unit)
      return match
    }
  }

  fun matchNotification(method: String, parameter: Any?) {
    synchronized(lock) {
      val match = notifications.find { it.method == method && it.predicate(parameter) } ?: return
      notifications.remove(match)
      match.deferred.complete(Unit)
    }
  }
}

internal class FakeLspServer(configureServerCapabilities: (ServerCapabilities.() -> Unit)?) : Process(), SelfKiller {
  private val serverName = "FakeLspServer"

  private val serverCapabilities = ServerCapabilities().apply {
    textDocumentSync = Either.forRight(TextDocumentSyncOptions().apply {
      openClose = true
      change = TextDocumentSyncKind.Full
    })
    configureServerCapabilities?.invoke(this)
  }

  private val didSaveNotificationCount = AtomicInteger(0)

  fun getDidSaveNotificationCount(): Int = didSaveNotificationCount.get()

  private val clientInputStream = PipedInputStream()
  private val clientOutputStream = PipedOutputStream()
  private val serverInputStream = PipedInputStream(clientOutputStream)
  private val serverOutputStream = PipedOutputStream(clientInputStream)

  private val endpoint: FakeEndpoint
  private val serverLauncher: Launcher<LanguageClient>
  private val serverListening: Future<Void>

  private val processRunningLatch = CountDownLatch(1)

  @Volatile
  var initialized: Boolean = false
    private set

  val expectations = ExpectationHolder()

  val remoteLanguageClient: LanguageClient get() = serverLauncher.remoteProxy

  init {
    endpoint = FakeEndpoint()
    val server = ServiceEndpoints.toServiceObject(endpoint, LanguageServer::class.java)
    serverLauncher = LSPLauncher.Builder<LanguageClient>()
      .setLocalService(server)
      .setRemoteInterface(LanguageClient::class.java)
      .setInput(serverInputStream)
      .setOutput(serverOutputStream)
      .validateMessages(true)
      .setExecutorService(AppExecutorUtil.createBoundedApplicationPoolExecutor(serverName, 1))
      //.wrapMessages(null)
      .create()

    serverListening = serverLauncher.startListening()
  }

  override fun getOutputStream(): OutputStream = clientOutputStream
  override fun getInputStream(): InputStream = clientInputStream
  override fun getErrorStream(): InputStream = InputStream.nullInputStream() // the error stream is not a part of LSP

  override fun exitValue(): Int = 0 // todo throw IllegalThreadStateException when still running

  override fun waitFor(): Int {
    processRunningLatch.await()
    return 0
  }

  override fun destroy() {
    serverListening.cancel(true)
    clientInputStream.close()
    clientOutputStream.close()
    serverInputStream.close()
    serverOutputStream.close()
    processRunningLatch.countDown()
  }

  private inner class FakeEndpoint : Endpoint {
    override fun request(method: String, parameter: Any?): CompletableFuture<*> {
      val waiter = expectations.matchRequest(method, parameter)
      if (waiter != null) {
        return CompletableFuture.completedFuture(waiter.resultProvider(parameter))
      }

      val result = when (method) {
        "initialize" -> {
          InitializeResult(serverCapabilities, ServerInfo(serverName, "0.0.1"))
        }
        "shutdown" -> {
          null
        }
        else -> {
          LOG.warn("Unmatched LSP request: $method")
          null
        }
      }
      return CompletableFuture.completedFuture(result)
    }

    override fun notify(method: String, parameter: Any?) {
      expectations.matchNotification(method, parameter)

      when (method) {
        "initialized" -> {
          initialized = true
        }
        "textDocument/didSave" -> {
          didSaveNotificationCount.incrementAndGet()
        }
        "exit" -> {
          serverListening.cancel(true)
        }
      }
    }
  }
}
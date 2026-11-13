package com.intellij.platform.lsp.common

import com.intellij.execution.process.SelfKiller
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.ServerInfo
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.jsonrpc.Endpoint
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
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

  fun matchNotification(method: String, parameter: Any?): Boolean {
    synchronized(lock) {
      val match = notifications.find { it.method == method && it.predicate(parameter) } ?: return false
      notifications.remove(match)
      match.deferred.complete(Unit)
      return true
    }
  }
}

/**
 * A fake LSP server process. It talks LSP over its stdin and stdout.
 *
 * It answers the lifecycle messages itself. Any other message needs a matching expectation of a [FakeLspServerSession],
 * unless its method is in [allowedUnmatchedMethods]. The server answers an unmatched request with `MethodNotFound`
 * and passes the description of each unmatched message to [onUnmatched].
 */
internal open class FakeLspServer(
  configureServerCapabilities: (ServerCapabilities.() -> Unit)?,
  /** The sessions add their expectations here. A session made before the server started brings its own holder. */
  val expectations: ExpectationHolder,
  private val allowedUnmatchedMethods: Set<String>,
  private val onUnmatched: (String) -> Unit,
) : Process(), SelfKiller {
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

  /** The method and the params of every request, in the order of arrival. */
  private val receivedRequests = ConcurrentLinkedQueue<Pair<String, Any?>>()

  /** The params of every [method] request, with or without an expectation. The server records a request before it answers. */
  fun getReceivedRequests(method: String): List<Any?> = receivedRequests.filter { it.first == method }.map { it.second }

  protected val clientInputStream: PipedInputStream = PipedInputStream()
  protected val clientOutputStream: PipedOutputStream = PipedOutputStream()
  private val serverInputStream = PipedInputStream(clientOutputStream)
  private val serverOutputStream = PipedOutputStream(clientInputStream)

  private val endpoint: FakeEndpoint
  private val serverLauncher: Launcher<LanguageClient>
  private val serverListening: Future<Void>

  private val processRunningLatch = CountDownLatch(1)

  private val errorOutput = FakeProcessOutput()

  // Not a child of the test's scope: a handler that never answers must not keep the test running.
  @OptIn(DelicateCoroutinesApi::class)
  private val handlerScope = GlobalScope.childScope("FakeLspServer handlers", Dispatchers.Default)

  @Volatile
  var initialized: Boolean = false
    private set


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
  override fun getErrorStream(): InputStream = errorOutput

  /** The IDE reads each call as one chunk. */
  fun writeErrorOutput(text: String) {
    errorOutput.write(text.toByteArray())
  }

  override fun exitValue(): Int = 0 // todo throw IllegalThreadStateException when still running

  override fun isAlive(): Boolean = processRunningLatch.count > 0

  override fun waitFor(): Int {
    processRunningLatch.await()
    return 0
  }

  override fun destroy() {
    handlerScope.cancel()
    serverListening.cancel(true)
    clientInputStream.close()
    clientOutputStream.close()
    serverInputStream.close()
    serverOutputStream.close()
    errorOutput.close()
    processRunningLatch.countDown()
  }

  private inner class FakeEndpoint : Endpoint {
    override fun request(method: String, parameter: Any?): CompletableFuture<*> {
      receivedRequests.add(method to parameter)
      val waiter = expectations.matchRequest(method, parameter)
      if (waiter != null) {
        // The handler starts on the message thread, so one that doesn't suspend answers before the next message is read.
        // Once it suspends, the message thread is free to read the client's messages, e.g. responses to the handler's own requests.
        // lsp4j cancels the returned future on $/cancelRequest, which cancels the handler.
        return handlerScope.future(start = CoroutineStart.UNDISPATCHED) {
          try {
            waiter.handler(parameter)
          }
          catch (e: Throwable) {
            // A ResponseErrorException is a deliberate error response. Anything else means the test's handler is broken;
            // lsp4j would only answer InternalError, which the test may never notice.
            if (e !is CancellationException && e !is ResponseErrorException) {
              LOG.error("Handler of $method failed", e)
            }
            throw e
          }
        }
      }

      val result = when (method) {
        "initialize" -> {
          InitializeResult(serverCapabilities, ServerInfo(serverName, "0.0.1"))
        }
        "shutdown" -> {
          null
        }
        else -> {
          if (method !in allowedUnmatchedMethods) {
            reportUnmatched("request", method, parameter)
            return CompletableFuture.failedFuture<Any?>(
              ResponseErrorException(ResponseError(ResponseErrorCode.MethodNotFound, "Unmatched LSP request: $method", null)))
          }
          null
        }
      }
      return CompletableFuture.completedFuture(result)
    }

    override fun notify(method: String, parameter: Any?) {
      val matched = expectations.matchNotification(method, parameter)
      if (!matched && method !in LIFECYCLE_NOTIFICATIONS && method !in allowedUnmatchedMethods) {
        reportUnmatched("notification", method, parameter)
      }

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

    private fun reportUnmatched(kind: String, method: String, parameter: Any?) {
      val description = "Unmatched LSP $kind $method: ${parameter.toString().replace(WHITESPACE, " ").take(500)}"
      // Not LOG.error: in tests it throws, and on the message thread that stops the server from reading
      LOG.warn(description)
      onUnmatched(description)
    }
  }
}

private val LIFECYCLE_NOTIFICATIONS = setOf("initialized", "exit")

private val WHITESPACE = Regex("\\s+")

/**
 * A fake LSP server process that talks LSP over the first connection to [serverSocket].
 * Its stdout is free, so a test can write to it.
 */
internal class FakeSocketLspServer(
  configureServerCapabilities: (ServerCapabilities.() -> Unit)?,
  expectations: ExpectationHolder,
  allowedUnmatchedMethods: Set<String>,
  onUnmatched: (String) -> Unit,
  private val serverSocket: ServerSocket,
) : FakeLspServer(configureServerCapabilities, expectations, allowedUnmatchedMethods, onUnmatched) {
  private val standardOutput = FakeProcessOutput()

  @Volatile
  private var socket: Socket? = null

  init {
    AppExecutorUtil.getAppExecutorService().execute { connectSocketToLauncher() }
  }

  /** Copies the bytes between the socket connection and the streams of the launcher, until [destroy]. */
  private fun connectSocketToLauncher() {
    val connection = try {
      serverSocket.accept()
    }
    catch (_: IOException) {
      return // destroyed before the client connected
    }
    socket = connection
    AppExecutorUtil.getAppExecutorService().execute { copyQuietly(clientInputStream, connection.getOutputStream()) }
    copyQuietly(connection.getInputStream(), clientOutputStream)
  }

  private fun copyQuietly(from: InputStream, to: OutputStream) {
    try {
      val buffer = ByteArray(8192)
      while (true) {
        val count = from.read(buffer)
        if (count < 0) break
        to.write(buffer, 0, count)
        to.flush()
      }
    }
    catch (_: IOException) {
      // destroy() closes the streams
    }
  }

  override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
  override fun getInputStream(): InputStream = standardOutput

  /** The IDE reads each call as one chunk. */
  fun writeStandardOutput(text: String) {
    standardOutput.write(text.toByteArray())
  }

  override fun destroy() {
    socket?.close()
    serverSocket.close()
    standardOutput.close()
    super.destroy()
  }
}

/**
 * An output stream of [FakeLspServer] that a test writes to. The IDE reads it as the process output.
 * The reader gets each [write] as one chunk, so a chunk never ends in the middle of the text that a test wrote.
 * A read blocks until a test writes some bytes or the server is destroyed. After [close], every read returns the end of the stream.
 */
private class FakeProcessOutput : InputStream() {
  private val chunks = LinkedBlockingQueue<ByteArray>()

  @Volatile
  private var closed = false

  // Only the reader thread uses these fields
  private var currentChunk = ByteArray(0)
  private var position = 0
  private var ended = false

  fun write(data: ByteArray) {
    if (!closed && data.isNotEmpty()) chunks.put(data)
  }

  override fun read(): Int {
    val oneByte = ByteArray(1)
    return if (read(oneByte, 0, 1) < 0) -1 else oneByte[0].toInt() and 0xFF
  }

  override fun read(b: ByteArray, off: Int, len: Int): Int {
    if (len == 0) return 0
    if (position == currentChunk.size) {
      if (ended) return -1
      // the process output reader logs an InterruptedException as an error, but it ignores an IOException
      val next = try {
        chunks.take()
      }
      catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw InterruptedIOException(e.message)
      }
      if (next === END_OF_STREAM) {
        ended = true
        return -1
      }
      currentChunk = next
      position = 0
    }
    val count = minOf(len, currentChunk.size - position)
    System.arraycopy(currentChunk, position, b, off, count)
    position += count
    return count
  }

  override fun available(): Int {
    if (position < currentChunk.size) return currentChunk.size - position
    val next = chunks.peek()
    return if (next == null || next === END_OF_STREAM) 0 else next.size
  }

  override fun close() {
    if (closed) return
    closed = true
    chunks.put(END_OF_STREAM)
  }

  private companion object {
    val END_OF_STREAM = ByteArray(0)
  }
}

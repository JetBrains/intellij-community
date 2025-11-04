package fleet.net

import fleet.rpc.core.Blob
import fleet.util.NetUtils
import fleet.util.logging.KLoggers
import fleet.util.Os
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.*
import io.ktor.utils.io.close
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import fleet.multiplatform.shims.MultiplatformConcurrentHashMap
import fleet.multiplatform.shims.multiplatformIO

private object TcpProxy {
  val logger = KLoggers.logger(TcpProxy::class)
}

sealed class TcpProxyMessage {
  data class AddConnection(val host: String, val port: Int) : TcpProxyMessage()
  data class RemoveConnection(val host: String, val port: Int) : TcpProxyMessage()
}

private fun ServerSocket.port(): Int? {
  return when (val address = this.localAddress) {
    is InetSocketAddress -> {
      address.port
    }
    else -> {
      null
    }
  }
}

private val selectorManagers = MultiplatformConcurrentHashMap<Job, ActorSelectorManager>()

@OptIn(InternalCoroutinesApi::class) // unfortunately necessary to unblock selector manager on cancellation
private fun getSelectorManager(parentJob: Job): ActorSelectorManager =
  selectorManagers.computeIfAbsent(parentJob) { parent ->
    ActorSelectorManager(Dispatchers.multiplatformIO).also { selectorManager ->
      parent.invokeOnCompletion(true, false) {
        selectorManagers.remove(parent, selectorManager)
        selectorManager.close()
      }
      runCatching { parent.ensureActive() }.onFailure { selectorManager.close() }.getOrThrow()
    }
  }

data class TcpProxyServer(val localPort: Int, val job: Job)

@Suppress("EXPERIMENTAL_API_USAGE")
suspend fun startTcpProxyServer(
  scope: CoroutineScope,
  localPort: Int? = null,
  connectionNotifier: SendChannel<TcpProxyMessage>? = null,
  connector: suspend (input: ReceiveChannel<Blob>, output: SendChannel<Blob>) -> Unit,
): TcpProxyServer {
  val selectorManager = getSelectorManager(scope.coroutineContext.job)
  val startTime = System.currentTimeMillis()

  val tcp = aSocket(selectorManager).tcp()
  val bindHost = if (Os.INSTANCE.isMac && localPort?.let { it < 1024 } == true) {
    // on macOS, privileged ports can only be used when binding to 0.0.0.0
    "0.0.0.0"
  }
  else NetUtils.localHost()
  val server = localPort?.let { tcp.bind(hostname = bindHost, port = localPort) } ?: tcp.bind(hostname = bindHost)
  val port = server.port() ?: error("Failed to start tcp server")

  val job = scope.launch {
    server.use { server ->
      while (true) {
        try {
          val socket = server.accept()
          TcpProxy.logger.trace { "Accept socket: ${socket.localAddress} -> ${socket.remoteAddress}" }

          val toFsd = Channel<Blob>()
          val fromFsd = Channel<Blob>()
          try {
            connector(toFsd, fromFsd)
          }
          catch (ex: CancellationException) {
            throw ex
          }
          catch (t: Throwable) {
            TcpProxy.logger.warn(t) { "Failed to connect" }
            socket.close()
            continue
          }

          launch {
            socket.use {
              val fromInternet = socket.openReadChannel()
              val toInternet = socket.openWriteChannel(autoFlush = true)
              val remoteAddress = socket.remoteAddress as InetSocketAddress
              connectionNotifier?.send(TcpProxyMessage.AddConnection(remoteAddress.hostname, remoteAddress.port))

              coroutineScope {
                launch {
                  val byteArray = ByteArray(8 * 1024)
                  try {
                    while (true) {
                      val count = fromInternet.readAvailable(byteArray, 0, byteArray.size)
                      if (count == -1) break
                      toFsd.send(Blob(byteArray.sliceArray(0 until count)))
                    }
                  }
                  catch (ex: CancellationException) {
                    throw ex
                  }
                  catch (t: Throwable) {
                    TcpProxy.logger.error(t) { "Error on internet to fsd transport" }
                  }
                  finally {
                    toFsd.close()
                    fromInternet.cancel(null)
                  }
                }

                launch {
                  try {
                    fromFsd.consumeEach { blob ->
                      toInternet.writeFully(blob.bytes, 0, blob.bytes.size)
                    }
                  }
                  catch (ex: CancellationException) {
                    throw ex
                  }
                  catch (t: Throwable) {
                    TcpProxy.logger.error(t) { "Error on fsd to internet transport" }
                  }
                  finally {
                    connectionNotifier?.send(TcpProxyMessage.RemoveConnection(remoteAddress.hostname, remoteAddress.port))
                    fromFsd.cancel()
                    toInternet.close(null)
                  }
                }
              }
            }
          }
        }
        catch (t: CancellationException) {
          throw t
        }
        catch (t: Throwable) {
          TcpProxy.logger.error(t) { "Can't accept socket" }
        }
      }
    }
  }
  job.invokeOnCompletion { connectionNotifier?.close() }

  val executionTime = System.currentTimeMillis() - startTime
  if (executionTime > 20) {
    TcpProxy.logger.warn { "startTcpProxyServer took: ${executionTime}ms" }
  }
  return TcpProxyServer(port, job)
}
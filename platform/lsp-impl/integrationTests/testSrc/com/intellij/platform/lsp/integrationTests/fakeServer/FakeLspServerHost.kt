// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.fakeServer

import com.intellij.ide.starter.path.GlobalPaths
import com.intellij.platform.lsp.integrationTests.util.LspIntegrationTestEnvironment
import com.intellij.tools.ide.util.common.logOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.ServerCapabilities
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.channels.ServerSocketChannel
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.time.Duration.Companion.milliseconds

/**
 * The side of the fake LSP server that lives in the test process.
 *
 * The IDE starts [FakeLspServerRelay] as its server process, and the relay connects to the socket of this host.
 * Each connection is one server process. It gets a [FakeLspServer] with the settings of the [FakeLspScenarioState] that is active
 * at that moment. A connection outside a test is closed at once.
 *
 * Create the host before the IDE starts: a port is forwarded only to the containers created after that.
 */
internal class FakeLspServerHost : AutoCloseable {
  private val serverChannel: ServerSocketChannel = ServerSocketChannel.open().apply {
    bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
    configureBlocking(false)
  }
  private val port: Int = (serverChannel.localAddress as InetSocketAddress).port
  private val exposedPort: LspIntegrationTestEnvironment.ExposedPort? = LspIntegrationTestEnvironment.current.exposeTestProcessPort(port)
  private val activeScenario = AtomicReference<FakeLspScenarioState?>()

  init {
    // The test framework reports a thread that outlives a test, unless it is an idle thread of a coroutine dispatcher.
    // So the loop polls a non-blocking channel instead of blocking a thread in `accept`.
    @OptIn(DelicateCoroutinesApi::class)
    GlobalScope.launch(Dispatchers.IO) { acceptConnections() }
  }

  /** The command line of the server process, for a `java` executable of the machine where the IDE runs. */
  fun relayCommand(javaExecutable: String): List<String> {
    val hostName = exposedPort?.hostName ?: InetAddress.getLoopbackAddress().hostAddress
    return listOf(javaExecutable, "-cp", relayClassesDir.toString(), FakeLspServerRelay::class.java.name, hostName, port.toString())
  }

  /** The compiled relay, copied from the classpath of the tests to a directory that the IDE can read, also in a container. */
  private val relayClassesDir: Path by lazy {
    val relayClass = FakeLspServerRelay::class.java
    val classFile = relayClass.name.replace('.', '/') + ".class"
    val bytes = checkNotNull(relayClass.classLoader.getResourceAsStream(classFile)) { "No $classFile on the classpath" }.use { it.readBytes() }
    val dir = GlobalPaths.instance.getCacheDirectoryFor("fake-lsp-server-relay")
    dir.resolve(classFile).apply { parent.createDirectories() }.writeBytes(bytes)
    dir
  }

  fun startScenario(state: FakeLspScenarioState) {
    check(activeScenario.compareAndSet(null, state)) { "Another test with the fake LSP server is still running" }
  }

  fun endScenario(state: FakeLspScenarioState) {
    activeScenario.compareAndSet(state, null)
    state.stop()
  }

  private suspend fun acceptConnections() {
    while (true) {
      val channel = try {
        serverChannel.accept()
      }
      catch (_: IOException) {
        return // closed
      }
      if (channel == null) {
        delay(ACCEPT_POLL_INTERVAL)
        continue
      }
      channel.configureBlocking(true)
      val socket = channel.socket()
      val scenario = activeScenario.get()
      if (scenario == null) {
        logOutput("FakeLspServerHost: a server process connected outside a test, closing the connection")
        socket.close()
      }
      else {
        scenario.startServer(socket)
      }
    }
  }

  override fun close() {
    serverChannel.close()
    exposedPort?.close()
  }
}

private val ACCEPT_POLL_INTERVAL = 50.milliseconds

/** The fake servers of one test, with their settings and what went wrong in them. */
internal class FakeLspScenarioState(
  private val configureServerCapabilities: (ServerCapabilities.() -> Unit)?,
  private val allowedUnmatchedMethods: Set<String>,
) {
  /** Not a child of anything: a handler that never answers must not keep the test running. Cancelled by [stop]. */
  val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val servers: MutableList<FakeLspServer> = CopyOnWriteArrayList()
  val unmatchedMessages: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
  val handlerFailures: ConcurrentLinkedQueue<Throwable> = ConcurrentLinkedQueue()

  /** The method and the deferred of every expectation of the test. */
  val pendingExpectations: MutableCollection<Pair<String, Deferred<Unit>>> = ConcurrentLinkedQueue()

  /** The jobs that the test started with [FakeLspScenario.launch]. */
  val jobs: MutableCollection<Job> = ConcurrentLinkedQueue()

  /** Set by [FakeLspScenario.unstartedServerSession], taken by the next server that starts. */
  val unstartedServer: AtomicReference<UnstartedServer?> = AtomicReference()

  fun startServer(socket: Socket) {
    val unstarted = unstartedServer.getAndSet(null)
    val server = FakeLspServer(socket, configureServerCapabilities, unstarted?.expectations ?: ExpectationHolder(),
                               allowedUnmatchedMethods, unmatchedMessages::add, handlerFailures::add, scope)
    unstarted?.server = server
    servers.add(server)
    server.start()
  }

  fun stop() {
    scope.cancel()
    servers.forEach { it.terminate() }
  }
}

/** The expectations of a session made before its server started, and the server once it starts. */
internal class UnstartedServer {
  val expectations: ExpectationHolder = ExpectationHolder()

  @Volatile
  var server: FakeLspServer? = null
}

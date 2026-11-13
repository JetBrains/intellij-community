package com.intellij.platform.lsp.common

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspCommunicationChannel
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ServerCapabilities
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * Starts a [FakeLspServer] for the files of the project.
 *
 * The server answers only the messages that a test expects, see [FakeLspServerSession].
 * A message without a matching expectation fails the test, unless its method is in [allowUnmatched].
 * By default, the tests may skip the document sync messages.
 *
 * Use [allowUnmatched] only as a last resort. First expect the message, or configure the capabilities so the client doesn't send it,
 * or set up the expectations on [FakeLspIntegration.unstartedServerSession] before the file opens.
 * When nothing else works, add a comment that says why the test can't expect the message.
 */
internal fun TestFixture<Project>.fakeLspIntegrationFixture(
  lspCustomization: LspCustomization = LspCustomization(),
  configureClientCapabilities: (ClientCapabilities.() -> Unit)? = null,
  configureServerCapabilities: (ServerCapabilities.() -> Unit)? = null,
  createLsp4jClient: ((LspServerNotificationsHandler) -> Lsp4jClient)? = null,
  isSupportedFile: ((VirtualFile) -> Boolean)? = null,
  offerServerLogInNotifications: Boolean = false,
  useSocket: Boolean = false,
  beforeServerStart: (() -> Unit)? = null,
  allowUnmatched: ServerSessionProtocolScope.() -> Set<LspMethod> = { DOCUMENT_SYNC },
): TestFixture<FakeLspIntegration> = testFixture { _ ->
  val projectFixture = this@fakeLspIntegrationFixture
  val project = projectFixture.init()

  check(project.getUserData(FAKE_LSP_CONFIG_KEY) == null) {
    "fakeLspIntegrationFixture is already set up for $project, probably by an enclosing test class. " +
    "Declare it either in the outer class or in each @Nested class, not in both."
  }

  extensionPointFixture(LspIntegrationProvider.EP_NAME) {
    FakeLspIntegrationProvider()
  }.init()

  val config = FakeLspConfig(lspCustomization, configureClientCapabilities, configureServerCapabilities,
                             createLsp4jClient, isSupportedFile, offerServerLogInNotifications, useSocket, beforeServerStart,
                             FakeLspProtocol.allowUnmatched().mapTo(HashSet()) { it.method })
  project.putUserData(FAKE_LSP_CONFIG_KEY, config)

  initialized(FakeLspIntegration(project, config)) {
    project.putUserData(FAKE_LSP_CONFIG_KEY, null)
    val unmatched = config.unmatchedMessages.toList()
    if (unmatched.isNotEmpty()) {
      throw AssertionError("The fake LSP server got messages that the test did not expect. " +
                           "Expect them, or pass their methods to `allowUnmatched` of fakeLspIntegrationFixture:\n" +
                           unmatched.joinToString("\n"))
    }
  }
}

/** The fake server that [fakeLspIntegrationFixture] starts. */
internal class FakeLspIntegration(private val project: Project, private val config: FakeLspConfig) {
  /** For the [LspClientManager] calls that take a provider class. Prefer [client] and [serverSession]. */
  val providerClass: Class<out LspIntegrationProvider> = FakeLspIntegrationProvider::class.java

  /** The running client of the fake server. */
  val client: LspClientImpl
    get() = LspClientManagerImpl.getInstanceImpl(project).getClients(providerClass).single { it.state == LspServerState.Running }

  /**
   * Waits for the fake server and returns its session.
   *
   * With [file], waits until the server got `didOpen` for it. Pass the file whenever a file is open in the editor.
   *
   * Without [file], waits until the server is initialized. Right after that, the client sends `didOpen` for the open files,
   * so expectations set up later race with them. Use it only when no file is open, or when the test itself triggers the next message.
   *
   * The client sends some requests right after `didOpen`, e.g. the diagnostic pull, so expectations of them race too.
   * Set those up on [unstartedServerSession] instead.
   *
   * The expectations of the session are children of the calling coroutine's job, so one that never matches keeps the test running.
   */
  suspend fun serverSession(file: VirtualFile? = null): FakeLspServerSession {
    val client = awaitClient((file as? VirtualFileWindow)?.delegate ?: file)
    val descriptor = client.descriptor as FakeLspClientDescriptor
    return FakeLspServerSessionImpl(descriptor.server.expectations, currentCoroutineContext().job) { descriptor }
  }

  /**
   * Returns the session of the fake server that starts next, without waiting for it.
   *
   * Call it before the file that starts the server opens, so the expectations are in place before the client sends anything.
   * Create the file first, e.g. with `addFileToProject`, so that the predicates can refer to it, then open it.
   * Fails when a fake server is already running.
   *
   * Until the server starts, the session can only set up expectations: anything else throws, [FakeLspServerSession.fileUri] too.
   * The predicates and handlers run when a message arrives, so they can use the whole session.
   * To wait until the server is running, await an expectation, e.g. of `didOpen`.
   *
   * The expectations of the session are children of the calling coroutine's job, so one that never matches keeps the test running.
   */
  suspend fun unstartedServerSession(): FakeLspServerSession {
    val running = LspClientManagerImpl.getInstanceImpl(project).getClients(providerClass)
      .filter { it.state == LspServerState.Initializing || it.state == LspServerState.Running }
    check(running.isEmpty()) { "A fake LSP server is already running. Call unstartedServerSession() before the file that starts it opens" }

    val unstartedServer = UnstartedServer()
    check(config.unstartedServer.compareAndSet(null, unstartedServer)) { "unstartedServerSession() is already called, and no server has started since" }
    return FakeLspServerSessionImpl(unstartedServer.expectations, currentCoroutineContext().job) {
      unstartedServer.descriptor ?: error("The fake LSP server has not started yet. Await an expectation, e.g. of didOpen, first")
    }
  }

  /**
   * Returns the messages that the fake server got without a matching expectation, and forgets them, so they don't fail the test.
   * For the tests of the fake server itself.
   */
  fun takeUnmatchedMessages(): List<String> = generateSequence { config.unmatchedMessages.poll() }.toList()

  private suspend fun awaitClient(topLevelFile: VirtualFile?): LspClientImpl {
    val manager = LspClientManagerImpl.getInstanceImpl(project)
    val ready = CompletableDeferred<LspClientImpl>()
    val disposable = Disposer.newDisposable("FakeLspIntegration.serverSession")
    try {
      manager.addListener(object : LspClientManagerListener {
        override fun serverStateChanged(lspClient: LspClient) {
          if (lspClient.providerClass != providerClass) return
          when (lspClient.state) {
            LspServerState.Initializing -> Unit
            LspServerState.Running -> if (topLevelFile == null) ready.complete(lspClient as LspClientImpl)
            LspServerState.ShutdownNormally, LspServerState.ShutdownUnexpectedly ->
              ready.completeExceptionally(AssertionError("LSP server initialization failed"))
          }
        }

        override fun fileOpened(lspClient: LspClient, file: VirtualFile) {
          if (lspClient.providerClass == providerClass && file == topLevelFile) ready.complete(lspClient as LspClientImpl)
        }
      }, disposable, true)
      // the listener gets `fileOpened` for the files opened earlier, but not the Running state
      if (topLevelFile == null) {
        manager.getClients(providerClass).firstOrNull { it.state == LspServerState.Running }?.let { ready.complete(it) }
      }
      return withTimeout(DEFAULT_TEST_TIMEOUT) { ready.await() }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }
}

/** The options of [fakeLspIntegrationFixture], passed as is to every [FakeLspClientDescriptor] it starts. */
internal class FakeLspConfig(
  val lspCustomization: LspCustomization = LspCustomization(),
  val configureClientCapabilities: (ClientCapabilities.() -> Unit)? = null,
  val configureServerCapabilities: (ServerCapabilities.() -> Unit)? = null,
  val createLsp4jClient: ((LspServerNotificationsHandler) -> Lsp4jClient)? = null,
  val isSupportedFile: ((VirtualFile) -> Boolean)? = null,
  val offerServerLogInNotifications: Boolean = false,
  val useSocket: Boolean = false,
  /** Runs on the thread that starts the server process, before the process starts. A test can block it to hold the client in [LspServerState.Initializing]. */
  val beforeServerStart: (() -> Unit)? = null,
  val allowedUnmatchedMethods: Set<String> = FakeLspProtocol.DOCUMENT_SYNC.mapTo(HashSet()) { it.method },
) {
  val unmatchedMessages: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()

  /** Set by [FakeLspIntegration.unstartedServerSession], taken by the next server that starts. */
  val unstartedServer: AtomicReference<UnstartedServer?> = AtomicReference()
}

/** The expectations of a session made before its server started, and the descriptor of the server once it starts. */
internal class UnstartedServer {
  val expectations: ExpectationHolder = ExpectationHolder()

  @Volatile
  var descriptor: FakeLspClientDescriptor? = null
}

private object FakeLspProtocol : ServerSessionProtocolScope()

private val FAKE_LSP_CONFIG_KEY = Key.create<FakeLspConfig>("FAKE_LSP_CONFIG_KEY")

private class FakeLspIntegrationProvider : LspIntegrationProvider {
  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    val config = project.getUserData(FAKE_LSP_CONFIG_KEY) ?: FakeLspConfig()
    val descriptor = if (config.useSocket) FakeSocketLspClientDescriptor(project, config) else FakeLspClientDescriptor(project, config)
    clientStarter.ensureClientStarted(descriptor)
  }
}

internal open class FakeLspClientDescriptor(
  project: Project,
  protected val config: FakeLspConfig = FakeLspConfig(),
  presentableName: String = "FakeLspServer",
) : ProjectWideLspClientDescriptor(project, presentableName) {
  lateinit var server: FakeLspServer

  override val lspCustomization: LspCustomization = config.lspCustomization

  override val offerServerLogInNotifications: Boolean = config.offerServerLogInNotifications

  override fun isSupportedFile(file: VirtualFile) = config.isSupportedFile?.invoke(file) ?: true

  override val clientCapabilities: ClientCapabilities
    get() = super.clientCapabilities.apply {
      config.configureClientCapabilities?.invoke(this)
    }

  override fun createLsp4jClient(handler: LspServerNotificationsHandler): Lsp4jClient =
    config.createLsp4jClient?.invoke(handler) ?: super.createLsp4jClient(handler)

  override fun createCommandLine(): GeneralCommandLine {
    /** command is usable for debugging **/
    return object : GeneralCommandLine("fake --lsp") {
      override fun startProcess(): Process {
        config.beforeServerStart?.invoke()
        val unstartedServer = config.unstartedServer.getAndSet(null)
        val fakeServer = createServer(unstartedServer?.expectations ?: ExpectationHolder())
        server = fakeServer
        unstartedServer?.descriptor = this@FakeLspClientDescriptor
        return fakeServer
      }
    }
  }

  protected open fun createServer(expectations: ExpectationHolder): FakeLspServer =
    FakeLspServer(config.configureServerCapabilities, expectations, config.allowedUnmatchedMethods, config.unmatchedMessages::add)
}

/** Talks LSP over a socket, so a test can write to the stdout of the server. */
internal class FakeSocketLspClientDescriptor(project: Project, config: FakeLspConfig) : FakeLspClientDescriptor(project, config) {
  // Lazy: the provider creates a descriptor for each opened file, but only the started one opens a socket. The server closes it.
  private val serverSocket: ServerSocket by lazy { ServerSocket(0, 1, InetAddress.getLoopbackAddress()) }

  override val lspCommunicationChannel: LspCommunicationChannel
    get() = LspCommunicationChannel.Socket(serverSocket.localPort)

  override fun createServer(expectations: ExpectationHolder): FakeLspServer =
    FakeSocketLspServer(config.configureServerCapabilities, expectations, config.allowedUnmatchedMethods, config.unmatchedMessages::add,
                        serverSocket)
}
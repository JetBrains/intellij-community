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

internal fun TestFixture<Project>.fakeLspIntegrationFixture(
  lspCustomization: LspCustomization = LspCustomization(),
  configureClientCapabilities: (ClientCapabilities.() -> Unit)? = null,
  configureServerCapabilities: (ServerCapabilities.() -> Unit)? = null,
  createLsp4jClient: ((LspServerNotificationsHandler) -> Lsp4jClient)? = null,
  isSupportedFile: ((VirtualFile) -> Boolean)? = null,
  offerServerLogInNotifications: Boolean = false,
  useSocket: Boolean = false,
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

  project.putUserData(FAKE_LSP_CONFIG_KEY, FakeLspConfig(lspCustomization, configureClientCapabilities, configureServerCapabilities,
                                                          createLsp4jClient, isSupportedFile, offerServerLogInNotifications,
                                                          useSocket))

  initialized(FakeLspIntegration(project)) {
    project.putUserData(FAKE_LSP_CONFIG_KEY, null)
  }
}

/** The fake server that [fakeLspIntegrationFixture] starts. */
internal class FakeLspIntegration(private val project: Project) {
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
   * The expectations of the session are children of the calling coroutine's job, so one that never matches keeps the test running.
   */
  suspend fun serverSession(file: VirtualFile? = null): FakeLspServerSession {
    val client = awaitClient((file as? VirtualFileWindow)?.delegate ?: file)
    return FakeLspServerSessionImpl(client.descriptor as FakeLspClientDescriptor, currentCoroutineContext().job)
  }

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
)

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
        val fakeServer = createServer()
        server = fakeServer
        return fakeServer
      }
    }
  }

  protected open fun createServer(): FakeLspServer = FakeLspServer(config.configureServerCapabilities)
}

/** Talks LSP over a socket, so a test can write to the stdout of the server. */
internal class FakeSocketLspClientDescriptor(project: Project, config: FakeLspConfig) : FakeLspClientDescriptor(project, config) {
  // Lazy: the provider creates a descriptor for each opened file, but only the started one opens a socket. The server closes it.
  private val serverSocket: ServerSocket by lazy { ServerSocket(0, 1, InetAddress.getLoopbackAddress()) }

  override val lspCommunicationChannel: LspCommunicationChannel
    get() = LspCommunicationChannel.Socket(serverSocket.localPort)

  override fun createServer(): FakeLspServer = FakeSocketLspServer(config.configureServerCapabilities, serverSocket)
}
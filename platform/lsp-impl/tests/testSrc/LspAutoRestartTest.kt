// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerListener
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.common.FakeLspClientDescriptor
import com.intellij.platform.lsp.common.FakeLspConfig
import com.intellij.platform.lsp.common.awaitRunningClient
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.common.stopAndWait
import com.intellij.platform.lsp.common.withListener
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.getServerId
import com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.xml.breadcrumbs.BreadcrumbsXmlWrapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.TextDocumentContentRegistrationOptions
import org.eclipse.lsp4j.TextDocumentContentResult
import org.eclipse.lsp4j.WorkspaceServerCapabilities
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private const val MAX_COUNT_KEY = "lsp.server.auto.restart.max.count"
private const val DELAY_KEY = "lsp.server.auto.restart.delay.ms"

/** The default of [MAX_COUNT_KEY], so a lower value of that key limits the descriptors of the tests. */
private const val TEST_MAX_RESTARTS = 4

@TestApplication
internal class LspAutoRestartTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
    maxUnexpectedTerminationRestarts = TEST_MAX_RESTARTS,
    configureServerCapabilities = {
      workspace = WorkspaceServerCapabilities().apply { textDocumentContent = TextDocumentContentRegistrationOptions(listOf("jar")) }
    },
  )

  private val providerClass get() = fakeLspIntegration.providerClass

  private val manager: LspClientManagerImpl get() = LspClientManagerImpl.getInstanceImpl(project)

  private fun optedInConfig(configureServerCapabilities: (ServerCapabilities.() -> Unit)? = null) =
    FakeLspConfig(configureServerCapabilities = configureServerCapabilities, maxUnexpectedTerminationRestarts = TEST_MAX_RESTARTS)

  @Test
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `a server killed after initialization is started again`(@TestDisposable disposable: Disposable) = timeoutRunBlocking(30.seconds) {
    val file = openFile("killed.txt")
    val client = startClient(file)
    val events = CopyOnWriteArrayList<Pair<String, LspClient>>()
    manager.addListener(object : LspClientManagerListener {
      override fun clientAdded(lspClient: LspClient) {
        events.add("added" to lspClient)
      }

      override fun clientRemoved(lspClient: LspClient) {
        events.add("removed" to lspClient)
      }
    }, disposable, false)

    val restarted = awaitRestart(client, file) { kill(client) }

    assertEquals(listOf("removed" to client, "added" to restarted), events.toList())
    assertFalse(client in manager.getClients(providerClass), "the stopped client must be removed")
    assertSame(client.descriptor, restarted.descriptor)
    assertEquals(client.getServerId(), restarted.getServerId())
    assertTrue(restarted.isFileOpened(file), "the new client must open the file")
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `a server killed in the initialization listener is started again`(@TestDisposable disposable: Disposable) =
    timeoutRunBlocking(30.seconds) {
      val initializations = AtomicInteger()
      val descriptor = object : FakeLspClientDescriptor(project, optedInConfig(), presentableName = "FakeLspServerKilledEarly") {
        override val lspServerListener = object : LspServerListener {
          override fun serverInitialized(params: InitializeResult) {
            if (initializations.incrementAndGet() == 1) server.destroy()
          }
        }
      }
      val running = runningClients(disposable)

      manager.ensureStarted(providerClass, descriptor)
      val first = running.receive()
      val second = running.receive()

      assertNotSame(first, second)
      assertEquals(LspServerState.ShutdownUnexpectedly, first.state)
      assertSame(second, manager.getClients(providerClass).single())
      stopClientsAndWait()
    }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `a server killed during the post-initialization work of the start is started again`(@TestDisposable disposable: Disposable) =
    timeoutRunBlocking(30.seconds) {
      val initializations = AtomicInteger()
      val armed = AtomicBoolean()
      val killed = AtomicBoolean()
      val descriptor = object : FakeLspClientDescriptor(project, optedInConfig(), presentableName = "FakeLspServerPostInit") {
        override val lspServerListener = object : LspServerListener {
          override fun serverInitialized(params: InitializeResult) {
            if (initializations.incrementAndGet() == 1) armed.set(true)
          }
        }
      }
      // the start thread publishes this topic after the Running state, before the start of the client completes
      ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(BreadcrumbsXmlWrapper.FORCE_RELOAD_BREADCRUMBS, Runnable {
        if (!ApplicationManager.getApplication().isDispatchThread && armed.compareAndSet(true, false)) {
          descriptor.server.destroy()
          killed.set(true)
        }
      })
      val running = runningClients(disposable)

      manager.ensureStarted(providerClass, descriptor)
      val first = running.receive()
      val second = running.receive()

      assertTrue(killed.get(), "the server must be killed during the start")
      assertNotSame(first, second)
      assertEquals(LspServerState.ShutdownUnexpectedly, first.state)
      assertSame(second, manager.getClients(providerClass).single())
      stopClientsAndWait()
    }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a double stop report schedules one restart`() = timeoutRunBlocking(30.seconds) {
    val client = startClient(openFile("double.txt"))

    manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = true)
    val job = manager.pendingAutoRestartJobs().single()
    manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = true)

    assertSame(job, manager.pendingAutoRestartJobs().single(), "the second report must not schedule a restart")
    assertFalse(job.isCancelled)
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a failure in the IDE reported first prevents the restart`() = timeoutRunBlocking(30.seconds) {
    val client = startClient(openFile("failureFirst.txt"))

    manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = false)
    manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = true)

    assertEquals(LspServerState.ShutdownUnexpectedly, client.state)
    assertTrue(manager.pendingAutoRestartJobs().isEmpty(), "the first report decides")
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a death reported first schedules one restart`() = timeoutRunBlocking(30.seconds) {
    val client = startClient(openFile("deathFirst.txt"))

    manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = true)
    manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = false)

    val job = manager.pendingAutoRestartJobs().single()
    assertFalse(job.isCancelled, "the first report decides")
    stopClientsAndWait()
  }

  /** The registry maximum of 2 is lower than the descriptor maximum of [TEST_MAX_RESTARTS]. */
  @Test
  @RegistryKey(key = MAX_COUNT_KEY, value = "2")
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `a crash loop stops at the limit`() = timeoutRunBlocking(30.seconds) {
    val file = openFile("loop.txt")
    val first = startClient(file)
    val second = awaitRestart(first, file) { kill(first) }
    val third = awaitRestart(second, file) { kill(second) }

    manager.handleMaybeUnexpectedServerStop(third, "test", serverGone = true)
    manager.pendingAutoRestartJobs().joinAll()

    assertEquals(LspServerState.ShutdownUnexpectedly, third.state)
    assertSame(third, manager.getClients(providerClass).single(), "the stopped client must stay")
    assertTrue(manager.pendingAutoRestartJobs().isEmpty())
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `a failed start is not retried`() = timeoutRunBlocking(30.seconds) {
    assertFailedStartNotRetried(FakeLspClientDescriptor(project, optedInConfig { throw IllegalStateException("test") }))
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "0")
  @RegistryKey(key = "lsp.server.connect.timeout", value = "1")
  fun `a start that fails in the IDE after the Running state is not retried`() = timeoutRunBlocking(30.seconds) {
    // the exception stops the initialization after the Running state, so the start fails at the connect timeout
    val descriptor = object : FakeLspClientDescriptor(project, optedInConfig(), presentableName = "FakeLspServerFailing") {
      override val lspServerListener = object : LspServerListener {
        override fun serverInitialized(params: InitializeResult) {
          throw IllegalStateException("test")
        }
      }
    }
    assertFailedStartNotRetried(descriptor)
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "0")
  @RegistryKey(key = "lsp.server.connect.timeout", value = "1")
  fun `a death reported before a failure in the IDE restarts the server`(@TestDisposable disposable: Disposable) =
    timeoutRunBlocking(30.seconds) {
      val stoppedUnexpectedly = AtomicBoolean()
      val stoppedBeforeFailure = AtomicBoolean()
      manager.addListener(object : LspClientManagerListener {
        override fun serverStateChanged(lspClient: LspClient) {
          if (lspClient.state == LspServerState.ShutdownUnexpectedly) stoppedUnexpectedly.set(true)
        }
      }, disposable, false)
      val initializations = AtomicInteger()
      // the exception stops the initialization after the Running state, so the start fails at the connect timeout
      val descriptor = object : FakeLspClientDescriptor(project, optedInConfig(), presentableName = "FakeLspServerKilledThenFailing") {
        override val lspServerListener = object : LspServerListener {
          override fun serverInitialized(params: InitializeResult) {
            if (initializations.incrementAndGet() != 1) return
            server.destroy()
            val deadline = TimeSource.Monotonic.markNow() + 10.seconds
            while (!stoppedUnexpectedly.get() && deadline.hasNotPassedNow()) Thread.sleep(10)
            stoppedBeforeFailure.set(stoppedUnexpectedly.get())
            throw IllegalStateException("test")
          }
        }
      }
      val running = runningClients(disposable)

      manager.ensureStarted(providerClass, descriptor)
      val first = running.receive()
      val second = running.receive()

      assertTrue(stoppedBeforeFailure.get(), "the death of the server must be reported before the failure in the IDE")
      assertNotSame(first, second)
      assertSame(second, manager.getClients(providerClass).single())
      stopClientsAndWait()
    }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a server that stops before the Running state is not restarted`() = timeoutRunBlocking(30.seconds) {
    val processStarting = CompletableDeferred<Unit>()
    val release = CountDownLatch(1)
    val descriptor = FakeLspClientDescriptor(project, optedInConfig {
      processStarting.complete(Unit)
      release.await(10, TimeUnit.SECONDS)
    }, presentableName = "FakeLspServerSlowStart")
    manager.ensureStarted(providerClass, descriptor)!!.join()
    processStarting.await()
    val client = manager.getClients(providerClass).single()
    assertEquals(LspServerState.Initializing, client.state)

    // the IDE disconnects from the server only after the start releases the connector, so the report runs on its own thread
    val report = launch(Dispatchers.IO) { manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = true) }
    while (client.state != LspServerState.ShutdownUnexpectedly) delay(10.milliseconds)
    release.countDown()
    report.join()

    assertTrue(manager.pendingAutoRestartJobs().isEmpty())
    assertSame(client, manager.getClients(providerClass).single(), "the stopped client must stay")
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `an explicit stop during the delay cancels the restart`() = timeoutRunBlocking(30.seconds) {
    assertStopCancelsPendingRestart("stop.txt") { client -> stopAndWait(manager, { it === client }) { manager.stopRunningServer(client) } }
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `stopping the clients of the provider during the delay cancels the restart`() = timeoutRunBlocking(30.seconds) {
    assertStopCancelsPendingRestart("stopClients.txt") { stopClientsAndWait() }
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a stop of all clients from the listener of the stopped server prevents the restart`() = timeoutRunBlocking(30.seconds) {
    assertStopFromListenerPreventsRestart { manager.stopClients(providerClass) }
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a stop of the client from the listener of the stopped server prevents the restart`() = timeoutRunBlocking(30.seconds) {
    assertStopFromListenerPreventsRestart { manager.stopRunningServer(it) }
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `the pending restart completes after the new client replaces the stopped client`() = timeoutRunBlocking(30.seconds) {
    val client = startClient(openFile("pending.txt"))

    manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = true)
    manager.pendingAutoRestartJobs().joinAll()

    assertNotSame(client, manager.getClients(providerClass).single())
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a failing listener of the stopped server does not prevent the restart`() = timeoutRunBlocking(30.seconds) {
    val descriptor = object : FakeLspClientDescriptor(project, optedInConfig(), presentableName = "FakeLspServerFailingListener") {
      override val lspServerListener = object : LspServerListener {
        override fun serverStopped(shutdownNormally: Boolean) {
          if (!shutdownNormally) throw IllegalStateException("test")
        }
      }
    }
    val client = awaitRunningClient(manager) { manager.ensureStarted(providerClass, descriptor) }

    assertThrows<IllegalStateException> { manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = true) }

    assertTrue(client.disconnected.isCompleted && !client.disconnected.isCancelled, "the release must succeed")
    assertFalse(manager.pendingAutoRestartJobs().single().isCancelled)
    stopClientsAndWait()
  }

  @Test
  fun `an exception after the stop does not hide the exception of the stop`() = timeoutRunBlocking(30.seconds) {
    val descriptor = object : FakeLspClientDescriptor(project, optedInConfig(), presentableName = "FakeLspServerTwoFailures") {
      // the automatic restart reads the maximum after the stop
      override val maxUnexpectedTerminationRestarts: Int get() = throw IllegalArgumentException("afterStop")

      override val lspServerListener = object : LspServerListener {
        override fun serverStopped(shutdownNormally: Boolean) {
          if (!shutdownNormally) throw IllegalStateException("listener")
        }
      }
    }
    val client = awaitRunningClient(manager) { manager.ensureStarted(providerClass, descriptor) }

    val thrown = assertThrows<IllegalStateException> {
      manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = true)
    }

    assertEquals("listener", thrown.message)
    assertEquals(listOf("afterStop"), thrown.suppressed.map { it.message })
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `a failed release of the stopped server drops the restart`(@TestDisposable disposable: Disposable) = timeoutRunBlocking(30.seconds) {
    val client = startClient(openFile("failedRelease.txt"))
    val added = collectAdded(disposable)
    // the release completes `disconnected` only once, so an earlier failure stands for a failed release
    client.disconnected.completeExceptionally(IllegalStateException("test"))

    manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = true)
    manager.pendingAutoRestartJobs().joinAll()

    assertTrue(added.isEmpty(), "a server that the IDE could not release must not be started again")
    assertSame(client, manager.getClients(providerClass).single(), "the stopped client must stay")
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a stop of all clients while a client crashes prevents the restart`() = timeoutRunBlocking(30.seconds) {
    lateinit var second: LspClientImpl
    val scheduledDuringStop = CopyOnWriteArrayList<Job>()
    // the stop of all clients reaches the second client after the normal stop of the first client
    val firstDescriptor = descriptorWithNormalStopAction("FakeLspServerFirst") {
      manager.handleMaybeUnexpectedServerStop(second, "test", serverGone = true)
      scheduledDuringStop.addAll(manager.pendingAutoRestartJobs())
    }
    val secondDescriptor = FakeLspClientDescriptor(project, optedInConfig(), presentableName = "FakeLspServerSecond")
    awaitRunningClient(manager) { manager.ensureStarted(providerClass, firstDescriptor) }
    second = awaitRunningClient(manager) { manager.ensureStarted(providerClass, secondDescriptor) }

    withContext(Dispatchers.IO) { manager.stopClients(providerClass) }

    assertTrue(scheduledDuringStop.isEmpty(), "the crash during the stop must not schedule a restart")
    assertTrue(manager.getClients(providerClass).isEmpty())
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a stop of all clients in progress prevents a pending restart`() = timeoutRunBlocking(30.seconds) {
    assertStopInProgressDropsRestart("the stop in progress must drop the restart") { capturedStamp -> capturedStamp }
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a client stopped on request is not restarted by a newer start request`() = timeoutRunBlocking(30.seconds) {
    assertStopInProgressDropsRestart("a client stopped on request must not be restarted") { manager.startRequestStamp() }
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a stop of a replaced client cancels the pending restart of its server`() = timeoutRunBlocking(30.seconds) {
    val (replaced, current, job, requestStamp) = crashedReplacement("staleStop.txt")

    manager.stopRunningServer(replaced)

    assertTrue(job.isCancelled)
    assertTrue(manager.pendingAutoRestartJobs().isEmpty())
    manager.restartAfterUnexpectedStop(current, requestStamp)!!.join()
    assertEquals(LspServerState.ShutdownUnexpectedly, current.state)
    assertSame(current, manager.getClients(providerClass).single(), "the stopped client must stay")
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a stop of a replaced client before the commit of the restart keeps the stopped client`() = timeoutRunBlocking(30.seconds) {
    val (replaced, current, _, requestStamp) = crashedReplacement("staleBeforeCommit.txt")

    // the restart commits in a write action on the EDT, so the stop comes before the commit
    val restart = withContext(Dispatchers.EDT) {
      val restart = manager.restartAfterUnexpectedStop(current, requestStamp)
      manager.stopRunningServer(replaced)
      restart
    }
    restart!!.join()

    assertEquals(LspServerState.ShutdownUnexpectedly, current.state)
    assertSame(current, manager.getClients(providerClass).single(), "the stopped client must stay")
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `the client limit does not refuse the restart of a client`(@TestDisposable disposable: Disposable) = timeoutRunBlocking(30.seconds) {
    val running = runningClients(disposable)
    val clients = List(10) { index ->
      val descriptor = FakeLspClientDescriptor(project, optedInConfig(), presentableName = "FakeLspServer$index")
      manager.ensureStarted(providerClass, descriptor)
      running.receive()
    }

    kill(clients[4])
    val restarted = running.receive()

    assertEquals(clients[4].getServerId(), restarted.getServerId())
    val expected = clients.toMutableList().also { it[4] = restarted }
    assertEquals(expected, manager.getClients(providerClass).toList(), "the new client must take the position of the stopped client")
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `a stop of a replaced client does not block a later automatic restart`() = timeoutRunBlocking(30.seconds) {
    assertActionOnReplacedClientKeepsAutoRestart("replacedStop.txt") { manager.stopRunningServer(it) }
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `a restart action on a replaced client does not block the automatic restart`() = timeoutRunBlocking(30.seconds) {
    assertActionOnReplacedClientKeepsAutoRestart("replaced.txt") { manager.restartClient(it) }
  }

  @Test
  @RegistryKey(key = MAX_COUNT_KEY, value = "0")
  fun `a stop of all clients before the commit of the restart drops the restart`(@TestDisposable disposable: Disposable) =
    timeoutRunBlocking(30.seconds) {
      val client = startClient(openFile("dropped.txt"))
      manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = true)
      val requestStamp = manager.startRequestStamp()
      val added = collectAdded(disposable)

      // the restart commits in a write action on the EDT, so the stop comes before the commit
      val restart = withContext(Dispatchers.EDT) {
        val restart = manager.restartAfterUnexpectedStop(client, requestStamp)
        manager.stopClients(providerClass)
        restart
      }
      restart!!.join()

      assertTrue(added.isEmpty(), "the stop must drop the start of the new client")
      assertTrue(manager.getClients(providerClass).isEmpty())
    }

  @Test
  fun `a restart action on a stopped client starts a new client`() = timeoutRunBlocking(30.seconds) {
    val file = openFile("stopped.txt")
    val stopped = startClient(file)
    stopAndWait(manager, { it === stopped }) { manager.stopRunningServer(stopped) }

    awaitRestart(stopped, file) { manager.restartClient(stopped) }
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `the new client is initialized after the stopped client is disconnected`() = timeoutRunBlocking(30.seconds) {
    val events = CopyOnWriteArrayList<String>()
    val initializations = AtomicInteger()
    val initializedAgain = CompletableDeferred<Unit>()
    val descriptor = object : FakeLspClientDescriptor(project, optedInConfig(), presentableName = "FakeLspServerOrdered") {
      override val lspServerListener = object : LspServerListener {
        override fun serverInitialized(params: InitializeResult) {
          events.add("initialized")
          if (initializations.incrementAndGet() == 2) initializedAgain.complete(Unit)
        }

        override fun serverStopped(shutdownNormally: Boolean) {
          events.add("stopping")
          // a slow listener gives a new client the time to start too early
          if (!shutdownNormally) Thread.sleep(300)
          events.add("stopped")
        }
      }
    }
    val client = awaitRunningClient(manager) { manager.ensureStarted(providerClass, descriptor) }

    // under read access, the IDE disconnects from the stopped server on a pooled thread
    ApplicationManager.getApplication().runReadAction { manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = true) }
    initializedAgain.await()

    assertEquals(listOf("initialized", "stopping", "stopped", "initialized"), events.toList())
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a second restart of a replaced client does nothing`(@TestDisposable disposable: Disposable) = timeoutRunBlocking(30.seconds) {
    val file = openFile("unlisted.txt")
    val killed = startClient(file)
    kill(killed)
    awaitPendingRestart()
    val requestStamp = manager.startRequestStamp()
    val restarted = awaitRestart(killed, file) { assertNotNull(manager.restartAfterUnexpectedStop(killed, requestStamp)) }
    val added = collectAdded(disposable)

    manager.restartAfterUnexpectedStop(killed, requestStamp)!!.join()

    assertTrue(added.isEmpty(), "the second restart must not start a client")
    assertSame(restarted, manager.getClients(providerClass).single())
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a client of an unloaded provider is not restarted`(@TestDisposable disposable: Disposable) = timeoutRunBlocking(30.seconds) {
    val client = startClient(openFile("unloaded.txt"))
    kill(client)
    awaitPendingRestart()
    val requestStamp = manager.startRequestStamp()

    ExtensionTestUtil.maskExtensions(LspIntegrationProvider.EP_NAME, emptyList(), disposable, fireEvents = false)
    assertNull(manager.restartAfterUnexpectedStop(client, requestStamp))
    assertSame(client, manager.getClients(providerClass).single(), "the stopped client must stay")
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = MAX_COUNT_KEY, value = "1")
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `an explicit restart clears the count`() = timeoutRunBlocking(30.seconds) {
    val file = openFile("clear.txt")
    val first = startClient(file)
    val second = awaitRestart(first, file) { kill(first) }
    val third = awaitRestart(second, file) { manager.restartClient(second) }

    val fourth = awaitRestart(third, file) { kill(third) }

    assertNotSame(third, fourth)
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = MAX_COUNT_KEY, value = "0")
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `a count of 0 turns the restart off`() = timeoutRunBlocking(30.seconds) {
    val warnings = CopyOnWriteArrayList<String>()
    val warningCollector = object : LoggedErrorProcessor() {
      override fun processWarn(category: String, message: String, t: Throwable?): Boolean {
        warnings.add(message)
        return true
      }
    }

    LoggedErrorProcessor.executeWith(warningCollector).use {
      assertNotRestarted(PluginDescriptor(project))
    }

    assertTrue(warnings.none { "too often" in it }, "a turned-off restart must not log a warning: $warnings")
  }

  @Test
  fun `a descriptor maximum of 0 or less is not restarted`() = timeoutRunBlocking(30.seconds) {
    assertNotRestarted(PluginDescriptor(project, maxRestarts = 0))
    assertNotRestarted(PluginDescriptor(project, maxRestarts = -1))
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `a descriptor maximum lower than the registry maximum stops the restarts earlier`(@TestDisposable disposable: Disposable) =
    timeoutRunBlocking(30.seconds) {
      val running = runningClients(disposable)
      manager.ensureStarted(providerClass, PluginDescriptor(project, maxRestarts = 1))
      val first = running.receive()
      kill(first)
      val second = running.receive()

      manager.handleMaybeUnexpectedServerStop(second, "test", serverGone = true)
      manager.pendingAutoRestartJobs().joinAll()

      assertEquals(LspServerState.ShutdownUnexpectedly, second.state)
      assertSame(second, manager.getClients(providerClass).single(), "the stopped client must stay")
      assertTrue(manager.pendingAutoRestartJobs().isEmpty())
      stopClientsAndWait()
    }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "0")
  fun `an automatically restarted client adopts a content file`() = timeoutRunBlocking(30.seconds) {
    val file = openFile("content.txt")
    val session = fakeLspIntegration.serverSession(file)
    val client = fakeLspIntegration.client
    val uri = "jar:///lib/foo.jar!/com/foo/Bar.class"
    session.expectRequest(session.TEXT_DOCUMENT_CONTENT, { it.uri == uri }) { TextDocumentContentResult("decompiled text") }
    val content = client.dynamicFiles.getOrRequestContent(uri)
    session.awaitExpected()
    assertNotNull(content)

    val restarted = awaitRestart(client, file) { kill(client) }
    val restartedSession = fakeLspIntegration.serverSession(file)
    restartedSession.expectNotification(restartedSession.DID_OPEN) {
      it.textDocument.uri == uri && it.textDocument.text == "decompiled text" && it.textDocument.version == 0
    }
    val clients = readAction { manager.getClientsForFileRequests(content!!) }
    restartedSession.awaitExpected()

    assertEquals(listOf(restarted), clients.toList())
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a plugin that stops and starts its clients during the delay gets one client`() = timeoutRunBlocking(30.seconds) {
    val client = awaitRunningClient(manager) { manager.ensureStarted(providerClass, PluginDescriptor(project)) }
    kill(client)
    val job = awaitPendingRestart()

    manager.stopClients(providerClass)
    val replacement = awaitRunningClient(manager) { manager.ensureClientStarted(providerClass, PluginDescriptor(project)) }

    assertTrue(job.isCancelled)
    assertTrue(manager.pendingAutoRestartJobs().isEmpty())
    assertNotSame(client, replacement)
    assertSame(replacement, manager.getClients(providerClass).single())
    stopClientsAndWait()
  }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a plugin that stops and starts its clients before the commit of the restart gets one client`(@TestDisposable disposable: Disposable) =
    timeoutRunBlocking(30.seconds) {
      val running = runningClients(disposable)
      manager.ensureStarted(providerClass, PluginDescriptor(project))
      val client = running.receive()
      kill(client)
      awaitPendingRestart()
      val requestStamp = manager.startRequestStamp()

      // the restart commits in a write action on the EDT, so the stop and the start come before the commit
      val restart = withContext(Dispatchers.EDT) {
        val restart = manager.restartAfterUnexpectedStop(client, requestStamp)
        manager.stopClients(providerClass)
        manager.ensureClientStarted(providerClass, PluginDescriptor(project))
        restart
      }
      val replacement = running.receive()
      restart!!.join()

      assertNotSame(client, replacement)
      assertEquals(LspServerState.Running, replacement.state)
      assertSame(replacement, manager.getClients(providerClass).single())
      stopClientsAndWait()
    }

  @Test
  @RegistryKey(key = DELAY_KEY, value = "60000")
  fun `a plugin that stops and starts its clients in serverStopped gets one client`(@TestDisposable disposable: Disposable) =
    timeoutRunBlocking(30.seconds) {
      val running = runningClients(disposable)
      val descriptor = PluginDescriptor(project, onUnexpectedStop = {
        manager.stopClients(providerClass)
        manager.ensureClientStarted(providerClass, PluginDescriptor(project))
      })
      manager.ensureStarted(providerClass, descriptor)
      val client = running.receive()

      manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = true)
      val replacement = running.receive()

      assertNotSame(client, replacement)
      assertTrue(manager.pendingAutoRestartJobs().isEmpty())
      assertSame(replacement, manager.getClients(providerClass).single())
      stopClientsAndWait()
    }

  private suspend fun assertStopCancelsPendingRestart(fileName: String, stop: suspend (LspClientImpl) -> Unit) {
    val client = startClient(openFile(fileName))
    kill(client)
    val job = awaitPendingRestart()

    stop(client)

    assertTrue(job.isCancelled)
    assertTrue(manager.pendingAutoRestartJobs().isEmpty())
    assertTrue(manager.getClients(providerClass).isEmpty())
  }

  /** [stop] runs in the `serverStopped` listener of the client that stopped unexpectedly. */
  private suspend fun assertStopFromListenerPreventsRestart(stop: (LspClientImpl) -> Unit) {
    lateinit var client: LspClientImpl
    val descriptor = object : FakeLspClientDescriptor(project, optedInConfig(), presentableName = "FakeLspServerStopping") {
      override val lspServerListener = object : LspServerListener {
        override fun serverStopped(shutdownNormally: Boolean) {
          if (!shutdownNormally) stop(client)
        }
      }
    }
    client = awaitRunningClient(manager) { manager.ensureStarted(providerClass, descriptor) }

    stopAndWait(manager, { it === client }) { manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = true) }

    assertTrue(manager.pendingAutoRestartJobs().isEmpty())
    assertTrue(manager.getClients(providerClass).isEmpty())
  }

  /**
   * The restart of the second client runs in the normal stop of the first client, during the stop of all clients.
   * [restartStamp] gets the stamp captured before that stop and returns the stamp of the restart request.
   */
  private suspend fun assertStopInProgressDropsRestart(message: String, restartStamp: (capturedStamp: Long) -> Long) {
    lateinit var second: LspClientImpl
    var capturedStamp = -1L
    val clientsAfterRestart = CopyOnWriteArrayList<List<LspClientImpl>>()
    val firstDescriptor = descriptorWithNormalStopAction("FakeLspServerFirst") {
      @Suppress("RAW_RUN_BLOCKING")
      runBlocking { manager.restartAfterUnexpectedStop(second, restartStamp(capturedStamp))!!.join() }
      clientsAfterRestart.add(manager.getClients(providerClass).toList())
    }
    val secondDescriptor = FakeLspClientDescriptor(project, optedInConfig(), presentableName = "FakeLspServerSecond")
    awaitRunningClient(manager) { manager.ensureStarted(providerClass, firstDescriptor) }
    second = awaitRunningClient(manager) { manager.ensureStarted(providerClass, secondDescriptor) }
    kill(second)
    awaitPendingRestart()
    capturedStamp = manager.startRequestStamp()

    withContext(Dispatchers.IO) { manager.stopClients(providerClass) }

    assertEquals(listOf(listOf(second)), clientsAfterRestart.toList(), message)
    assertTrue(manager.pendingAutoRestartJobs().isEmpty())
    assertTrue(manager.getClients(providerClass).isEmpty())
  }

  /** [replaced] was stopped, [current] replaced it and crashed, and the restart of [current] is pending. */
  private data class CrashedReplacement(
    val replaced: LspClientImpl,
    val current: LspClientImpl,
    val pendingRestart: Job,
    val requestStamp: Long,
  )

  private suspend fun crashedReplacement(fileName: String): CrashedReplacement {
    val replaced = startClient(openFile(fileName))
    stopAndWait(manager, { it === replaced }) { manager.stopRunningServer(replaced) }
    val current = awaitRunningClient(manager) { manager.ensureClientStarted(providerClass, replaced.descriptor) }
    kill(current)
    val job = awaitPendingRestart()
    return CrashedReplacement(replaced, current, job, manager.startRequestStamp())
  }

  private suspend fun assertActionOnReplacedClientKeepsAutoRestart(fileName: String, action: (replaced: LspClientImpl) -> Unit) {
    val file = openFile(fileName)
    val replaced = startClient(file)
    val current = awaitRestart(replaced, file) { kill(replaced) }

    action(replaced)

    assertSame(current, manager.getClients(providerClass).single())
    awaitRestart(current, file) { kill(current) }
    stopClientsAndWait()
  }

  private suspend fun assertFailedStartNotRetried(descriptor: FakeLspClientDescriptor) {
    val failed = CompletableDeferred<LspClient>()
    manager.withListener(object : LspClientManagerListener {
      override fun serverStateChanged(lspClient: LspClient) {
        if (lspClient.state == LspServerState.ShutdownUnexpectedly) failed.complete(lspClient)
      }
    }) {
      manager.ensureStarted(providerClass, descriptor)!!.join()
      val client = failed.await() as LspClientImpl
      // the release of the failed client ends the stop, and a restart is scheduled only after it
      client.disconnected.join()
      manager.pendingAutoRestartJobs().joinAll()

      assertSame(client, manager.getClients(providerClass).single(), "the failed client must stay")
      assertTrue(manager.pendingAutoRestartJobs().isEmpty())
    }
    stopClientsAndWait()
  }

  private suspend fun assertNotRestarted(descriptor: PluginDescriptor) {
    val client = awaitRunningClient(manager) { manager.ensureStarted(providerClass, descriptor) }

    manager.handleMaybeUnexpectedServerStop(client, "test", serverGone = true)

    assertTrue(manager.pendingAutoRestartJobs().isEmpty())
    assertEquals(LspServerState.ShutdownUnexpectedly, client.state)
    assertSame(client, manager.getClients(providerClass).single(), "the stopped client must stay")
    stopClientsAndWait()
  }

  private fun openFile(name: String): VirtualFile = codeInsightFixture.configureByText(name, "hello").virtualFile

  private suspend fun startClient(file: VirtualFile): LspClientImpl {
    awaitFileOpenedByLspServer(project, file)
    return manager.getClients(providerClass).single()
  }

  private fun kill(client: LspClientImpl) {
    (client.descriptor as FakeLspClientDescriptor).server.destroy()
  }

  /** Runs [action] and returns the new client once it has opened [expectedFile]. */
  private suspend fun awaitRestart(oldClient: LspClientImpl, expectedFile: VirtualFile, action: () -> Unit): LspClientImpl {
    val added = CompletableDeferred<LspClient>()
    val opened = CompletableDeferred<LspClient>()
    return manager.withListener(object : LspClientManagerListener {
      override fun clientAdded(lspClient: LspClient) {
        if (lspClient !== oldClient) added.complete(lspClient)
      }

      override fun fileOpened(lspClient: LspClient, file: VirtualFile) {
        if (lspClient !== oldClient && file == expectedFile) opened.complete(lspClient)
      }
    }) {
      action()
      val newClient = added.await()
      assertSame(newClient, opened.await())
      newClient as LspClientImpl
    }
  }

  private fun descriptorWithNormalStopAction(name: String, onNormalStop: () -> Unit): FakeLspClientDescriptor =
    object : FakeLspClientDescriptor(project, optedInConfig(), presentableName = name) {
      override val lspServerListener = object : LspServerListener {
        override fun serverStopped(shutdownNormally: Boolean) {
          if (shutdownNormally) onNormalStop()
        }
      }
    }

  /** Collects the clients that the manager adds from now on. */
  private fun collectAdded(disposable: Disposable): List<LspClient> {
    val added = CopyOnWriteArrayList<LspClient>()
    manager.addListener(object : LspClientManagerListener {
      override fun clientAdded(lspClient: LspClient) {
        added.add(lspClient)
      }
    }, disposable, false)
    return added
  }

  /** Receives each client that reaches the Running state. */
  private fun runningClients(disposable: Disposable): Channel<LspClientImpl> {
    val running = Channel<LspClientImpl>(Channel.UNLIMITED)
    manager.addListener(object : LspClientManagerListener {
      override fun serverStateChanged(lspClient: LspClient) {
        if (lspClient.state == LspServerState.Running) running.trySend(lspClient as LspClientImpl)
      }
    }, disposable, false)
    return running
  }

  private suspend fun awaitPendingRestart(): Job {
    while (true) {
      manager.pendingAutoRestartJobs().singleOrNull()?.let { return it }
      delay(10.milliseconds)
    }
  }

  private suspend fun stopClientsAndWait(): Unit =
    stopAndWait(manager) { manager.stopClients(providerClass) }
}

/** A descriptor of a plugin that can restart its server itself. Each instance has the same server id. */
private class PluginDescriptor(
  project: Project,
  private val maxRestarts: Int = TEST_MAX_RESTARTS,
  private val onUnexpectedStop: () -> Unit = {},
) : FakeLspClientDescriptor(project, FakeLspConfig(), presentableName = "FakeLspServerPlugin") {
  override val maxUnexpectedTerminationRestarts: Int get() = maxRestarts

  override val lspServerListener = object : LspServerListener {
    override fun serverStopped(shutdownNormally: Boolean) {
      if (!shutdownNormally) onUnexpectedStop()
    }
  }
}

package com.intellij.platform.lsp

import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

@TestApplication
internal class LspNotificationBeforeInitializationTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private val serverStartGate = CountDownLatch(1)

  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
    beforeServerStart = { serverStartGate.await() },
  )

  @Test
  fun `notification sent while the server initializes is skipped`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("before-initialization.txt", "hello").virtualFile
    // The client sends notifications on its own thread, so an error there does not reach the test directly.
    val loggedErrors = ConcurrentLinkedQueue<String>()
    val earlySenderRan = AtomicBoolean()
    val errorCollector = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        loggedErrors.add(t?.toString() ?: message)
        return super.processError(category, message, details, t)
      }
    }

    LoggedErrorProcessor.executeWith(errorCollector).use {
      try {
        val manager = LspClientManagerImpl.getInstanceImpl(project)
        waitUntil("the client must be created") { manager.getClients(fakeLspIntegration.providerClass).isNotEmpty() }
        val lspClient = manager.getClients(fakeLspIntegration.providerClass).single()
        assertEquals(LspServerState.Initializing, lspClient.state)

        lspClient.sendNotification {
          earlySenderRan.set(true)
          it.workspaceService.didChangeConfiguration(DidChangeConfigurationParams("early"))
        }
      }
      finally {
        serverStartGate.countDown()
      }

      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val marker = serverSession.expectNotification(serverSession.DID_CHANGE_CONFIGURATION) { it.hasStringSettings("marker") }

      // The client sends notifications on one thread in order, so the marker arrives after an early notification that was queued.
      fakeLspIntegration.client.sendNotification { it.workspaceService.didChangeConfiguration(DidChangeConfigurationParams("marker")) }
      marker.await()

      assertFalse(earlySenderRan.get(), "The client must not send a notification before the server is initialized")
    }

    assertTrue(loggedErrors.isEmpty(), "The skipped notification must not log an error: $loggedErrors")
  }
}

/** The server reads `settings` as a JSON element, so a string sent by the client arrives as a quoted JSON string. */
private fun DidChangeConfigurationParams.hasStringSettings(text: String): Boolean = settings.toString() == "\"$text\""

package com.intellij.platform.lsp

import com.intellij.idea.TestFor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarInfo
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.common.awaitErrorOutput
import com.intellij.platform.lsp.common.awaitNotification
import com.intellij.platform.lsp.common.collectNotifications
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.common.stopFakeServers
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.serviceView.LspServiceViewContributor
import com.intellij.platform.lsp.impl.serviceView.ShowLspServerLogAction
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

/** A server whose descriptor opts in to the "Show server log" action in its error and warning notifications. */
@TestApplication
internal class LspShowServerLogTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  // The server error output opens in an editor tab with a LightVirtualFile. That file must not start one more fake server.
  @Suppress("unused")
  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(isSupportedFile = { it !is LightVirtualFile },
                                                                              offerServerLogInNotifications = true)

  @Test
  @TestFor(issues = ["IJPL-257420"])
  fun `error and warning messages offer to show the server log`() = timeoutRunBlocking {
    collectNotifications(project) { notifications ->
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      serverSession.sendNotification(serverSession.SHOW_MESSAGE) { MessageParams(MessageType.Error, "error-marker") }
      serverSession.sendNotification(serverSession.SHOW_MESSAGE) { MessageParams(MessageType.Warning, "warning-marker") }
      serverSession.sendNotification(serverSession.SHOW_MESSAGE) { MessageParams(MessageType.Info, "info-marker") }
      serverSession.sendRequestNoWait(serverSession.SHOW_MESSAGE_REQUEST) {
        ShowMessageRequestParams(listOf(MessageActionItem("Retry"))).apply {
          type = MessageType.Error
          message = "request-marker"
        }
      }

      suspend fun actionsOf(marker: String): List<AnAction> = awaitNotification(notifications, marker).actions

      assertTrue(actionsOf("error-marker").any { it is ShowLspServerLogAction }, "An error message should offer to show the server log")
      assertTrue(actionsOf("warning-marker").any { it is ShowLspServerLogAction }, "A warning message should offer to show the server log")
      assertFalse(actionsOf("info-marker").any { it is ShowLspServerLogAction }, "An info message should not offer to show the server log")
      val requestActions = actionsOf("request-marker")
      assertEquals(listOf("Retry", LspBundle.message("action.ShowLspServerLogAction.text")),
                   requestActions.map { it.templatePresentation.text },
                   "An error request should keep the server actions first and then offer to show the server log")

      stopFakeServers(project)
    }
  }

  @Test
  @TestFor(issues = ["IJPL-257420"])
  fun `show server log opens the error output of a stopped server`() = timeoutRunBlocking {
    collectNotifications(project) { notifications ->
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val lspClient = LspServiceViewContributor().getServices(project).single() as LspClientImpl
      serverSession.writeErrorOutput("ERROR stopped-server-marker\n")
      awaitErrorOutput(lspClient, "stopped-server-marker")
      serverSession.sendNotification(serverSession.SHOW_MESSAGE) { MessageParams(MessageType.Error, "error-marker") }
      val notification = awaitNotification(notifications, "error-marker")
      val action = notification.actions.filterIsInstance<ShowLspServerLogAction>().single()

      stopFakeServers(project)

      withContext(Dispatchers.EDT) {
        action.actionPerformed(TestActionEvent.createTestEvent(), notification)
      }
      val fileEditorManager = FileEditorManager.getInstance(project)
      val tabName = LspBundle.message("server.error.output.editor.tab.name", lspClient.descriptor.presentableName)
      waitUntil({ "The error output of the stopped server should open in an editor tab, open files: ${fileEditorManager.openFiles.map { it.name }}" }) {
        fileEditorManager.openFiles.any { it.name == tabName }
      }
      val file = fileEditorManager.openFiles.single { it.name == tabName }
      withContext(Dispatchers.EDT) {
        try {
          assertTrue("stopped-server-marker" in (file as LightVirtualFile).content,
                     "The editor tab should show the server error output, got: ${file.content}")
        }
        finally {
          fileEditorManager.closeFile(file)
        }
      }
    }
  }

  @Test
  @TestFor(issues = ["IJPL-257420"])
  fun `show server log finds the console of the live client, then of the restarted client`() = timeoutRunBlocking {
    collectNotifications(project) { notifications ->
      val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val oldClient = LspServiceViewContributor().getServices(project).single() as LspClientImpl
      serverSession.sendNotification(serverSession.SHOW_MESSAGE) { MessageParams(MessageType.Error, "error-marker") }
      val notification = awaitNotification(notifications, "error-marker")
      val action = notification.actions.filterIsInstance<ShowLspServerLogAction>().single()
      assertEquals(ShowLspServerLogAction.ServerLog.Console(oldClient), action.findServerLog(),
                   "The action should find the console of the live client")

      stopFakeServers(project)
      assertEquals(ShowLspServerLogAction.ServerLog.NotAvailable, action.findServerLog(),
                   "Without the server error output and without a new client, the server log should not be available")
      val statusBarTexts = ConcurrentLinkedQueue<String>()
      val connection = project.messageBus.connect()
      try {
        connection.subscribe(StatusBar.Info.TOPIC, object : StatusBarInfo {
          override fun setInfo(s: String?) = setInfo(s, null)
          override fun setInfo(s: String?, requestor: String?) {
            s?.let(statusBarTexts::add)
          }
          override fun getInfo(): String = ""
        })
        withContext(Dispatchers.EDT) {
          action.actionPerformed(TestActionEvent.createTestEvent(), notification)
        }
      }
      finally {
        connection.disconnect()
      }
      assertEquals(listOf(LspBundle.message("status.bar.server.log.not.available", oldClient.descriptor.presentableName)),
                   statusBarTexts.toList(), "The status bar should tell that the server log is not available")

      val otherFile = codeInsightFixture.configureByText("other.txt", "hello again").virtualFile
      configureServerSession(project, otherFile)
      val newClient = LspServiceViewContributor().getServices(project).single() as LspClientImpl
      assertFalse(newClient === oldClient, "The second file should start a new client")
      assertEquals(ShowLspServerLogAction.ServerLog.Console(newClient), action.findServerLog(),
                   "The action should find the console of the restarted client")

      stopFakeServers(project)
    }
  }
}

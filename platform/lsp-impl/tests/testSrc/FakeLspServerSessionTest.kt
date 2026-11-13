package com.intellij.platform.lsp

import com.intellij.platform.lsp.common.FakeLspServerSession
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

@TestApplication
class FakeLspServerSessionTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

  @Test
  fun `expected request is completed before its handler starts`() = timeoutRunBlocking {
    val serverSession = startServerSession()
    lateinit var requestArrived: Deferred<Unit>
    val arrivedWhenHandlerStarted = CompletableDeferred<Boolean>()
    requestArrived = serverSession.expectRequest(serverSession.EXECUTE_COMMAND) {
      arrivedWhenHandlerStarted.complete(requestArrived.isCompleted)
      "ok"
    }

    executeCommand()

    assertTrue(arrivedWhenHandlerStarted.await())
  }

  @Test
  fun `handler exception is logged and answered with InternalError`() = timeoutRunBlocking {
    val serverSession = startServerSession()
    serverSession.expectRequest(serverSession.EXECUTE_COMMAND) { error("broken handler") }

    val loggedErrors = collectLoggedErrors {
      val e = executeCommandError()
      assertEquals(ResponseErrorCode.InternalError.value, e.responseError.code)
    }

    assertEquals(listOf("broken handler"), loggedErrors.map { it?.message })
  }

  @Test
  fun `ResponseErrorException from a handler is sent to the client and not logged`() = timeoutRunBlocking {
    val serverSession = startServerSession()
    serverSession.expectRequest(serverSession.EXECUTE_COMMAND) {
      throw ResponseErrorException(ResponseError(ResponseErrorCode.InvalidParams, "deliberate", null))
    }

    val loggedErrors = collectLoggedErrors {
      val e = executeCommandError()
      assertEquals(ResponseErrorCode.InvalidParams.value, e.responseError.code)
      assertEquals("deliberate", e.responseError.message)
    }

    assertEquals(emptyList<Throwable?>(), loggedErrors)
  }

  @Test
  fun `unexpected request is answered with MethodNotFound and reported`() = timeoutRunBlocking {
    startServerSession()

    val e = executeCommandError()

    assertEquals(ResponseErrorCode.MethodNotFound.value, e.responseError.code)
    val unmatched = fakeLspIntegration.takeUnmatchedMessages()
    assertTrue(unmatched.single().startsWith("Unmatched LSP request workspace/executeCommand:"), unmatched.toString())
  }

  @Test
  fun `unexpected notification is reported`() = timeoutRunBlocking {
    startServerSession()

    fakeLspIntegration.client.sendNotification {
      it.workspaceService.didChangeConfiguration(DidChangeConfigurationParams(emptyMap<String, Any>()))
    }

    val unmatched = mutableListOf<String>()
    waitUntil("the server must report the notification") {
      unmatched += fakeLspIntegration.takeUnmatchedMessages()
      unmatched.isNotEmpty()
    }
    assertTrue(unmatched.single().startsWith("Unmatched LSP notification workspace/didChangeConfiguration:"), unmatched.toString())
  }

  @Test
  fun `unstarted server session gets the first messages of the server`() = timeoutRunBlocking {
    val serverSession = fakeLspIntegration.unstartedServerSession()
    val initialized = serverSession.expectNotification(serverSession.INITIALIZED)
    val file = codeInsightFixture.addFileToProject("test.txt", "").virtualFile
    val opened = serverSession.expectNotification(serverSession.DID_OPEN) { it.textDocument.uri == serverSession.fileUri(file) }

    codeInsightFixture.configureFromExistingVirtualFile(file)

    initialized.await()
    opened.await()
  }

  @Test
  fun `unstarted server session needs the server for anything but expectations`(): Unit = timeoutRunBlocking {
    val serverSession = fakeLspIntegration.unstartedServerSession()
    val file = codeInsightFixture.addFileToProject("test.txt", "").virtualFile

    assertInstanceOf(IllegalStateException::class.java, runCatching { serverSession.fileUri(file) }.exceptionOrNull())
  }

  @Test
  fun `unstarted server session fails when the server is already running`(): Unit = timeoutRunBlocking {
    startServerSession()

    assertInstanceOf(IllegalStateException::class.java, runCatching { fakeLspIntegration.unstartedServerSession() }.exceptionOrNull())
  }

  private suspend fun startServerSession(): FakeLspServerSession =
    fakeLspIntegration.serverSession(codeInsightFixture.configureByText("test.txt", "").virtualFile)

  private suspend fun executeCommand() {
    fakeLspIntegration.client.sendRequest { it.workspaceService.executeCommand(ExecuteCommandParams("cmd", emptyList())) }
  }

  private suspend fun executeCommandError(): ResponseErrorException =
    assertInstanceOf(ResponseErrorException::class.java, runCatching { executeCommand() }.exceptionOrNull())

  private suspend fun collectLoggedErrors(block: suspend () -> Unit): List<Throwable?> {
    val loggedErrors = ConcurrentLinkedQueue<Throwable?>()
    LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        loggedErrors += t
        return Action.NONE
      }
    }).use { block() }
    return loggedErrors.toList()
  }
}

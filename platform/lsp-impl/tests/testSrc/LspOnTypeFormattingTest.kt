package com.intellij.platform.lsp

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspOnTypeFormattingSupport
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.impl.LspCoroutineScopeService
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.containers.tail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class LspOnTypeFormattingTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)
  private val triggerCharacters = listOf("\n", "{", "}", ";", "\"")

  @Suppress("unused")
  private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
    lspCustomization = object : LspCustomization() {
      override val onTypeFormattingCustomizer = LspOnTypeFormattingSupport()
    },
    configureServerCapabilities = {
      documentOnTypeFormattingProvider = DocumentOnTypeFormattingOptions().apply {
        firstTriggerCharacter = triggerCharacters.first()
        moreTriggerCharacter = triggerCharacters.tail()
      }
    }
  )

  @Test
  fun `on type formatting request sent on Enter typed in text file`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test_enter.txt", "line1<caret>").virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, {
      it.textDocument.uri == serverSession.fileUri(virtualFile) && it.ch == "\n"
    }) { listOf(TextEdit(Range(Position(0, 0), Position(0, 0)), "  ")) }

    withContext(Dispatchers.EDT) {
      codeInsightFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    }

    awaitLspJobs(project)
    codeInsightFixture.checkResult("  line1\n")
  }

  @Test
  fun `on type formatting request sent on Enter typed file with PSI`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test_enter_psi.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <root>
          <user id="1">
              <name>John Doe</name>
              <email>john@example.com</email>
      <active>true</active>
          </user>
      </root><caret>
      """.trimIndent()).virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, {
      it.textDocument.uri == serverSession.fileUri(virtualFile) && it.ch == "\n"
    }) { listOf(TextEdit(Range(Position(5, 0), Position(5, 0)), " ".repeat(8))) }

    withContext(Dispatchers.EDT) {
      codeInsightFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
    }

    awaitLspJobs(project)
    codeInsightFixture.checkResult("""
      <?xml version="1.0" encoding="UTF-8"?>
      <root>
          <user id="1">
              <name>John Doe</name>
              <email>john@example.com</email>
              <active>true</active>
          </user>
      </root>
      
      """.trimIndent())
  }

  @Test
  fun `on type formatting request sent on closing brace`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test_brace.txt", "function(){<caret>").virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, {
      it.textDocument.uri == serverSession.fileUri(virtualFile) && it.ch == "}"
    }) { listOf(TextEdit(Range(Position(0, 10), Position(0, 10)), " ")) }

    withContext(Dispatchers.EDT) {
      codeInsightFixture.type("}")
    }

    awaitLspJobs(project)
    codeInsightFixture.checkResult("function() {}")
  }

  @Test
  fun `on type formatting request sent on double quote`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test_quote.txt", "Doublequote:<caret>").virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, {
      it.textDocument.uri == serverSession.fileUri(virtualFile) && it.ch == "\""
    }) {
      listOf(TextEdit(Range(Position(0, 12), Position(0, 12)), " "),
             TextEdit(Range(Position(0, 6), Position(0, 6)), " "))
    }

    withContext(Dispatchers.EDT) {
      codeInsightFixture.type("\"")
    }

    awaitLspJobs(project)
    codeInsightFixture.checkResult("Double quote: \"")
  }

  @Test
  fun `on type formatting request sent on semicolon`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test_semicolon.txt", "val x = 1<caret>").virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, {
      it.textDocument.uri == serverSession.fileUri(virtualFile) && it.ch == ";"
    }) { listOf(TextEdit(Range(Position(0, 0), Position(0, 0)), "\t")) }

    withContext(Dispatchers.EDT) {
      codeInsightFixture.type(";")
    }

    awaitLspJobs(project)
    codeInsightFixture.checkResult("\tval x = 1;")
  }

  @Test
  fun `on type formatting skipped when live template is active`(): Unit = timeoutRunBlocking {
    val virtualFile =
      codeInsightFixture.configureByText("test_completion.txt", "Test()<caret>").virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, {
      it.textDocument.uri == serverSession.fileUri(virtualFile) && it.ch in triggerCharacters
    }) { listOf(TextEdit(Range(Position(0, 0), Position(0, 0)), "\t")) }

    withContext(Dispatchers.EDT) {
      codeInsightFixture.type(";")
      codeInsightFixture.type("sout")
      codeInsightFixture.performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_CUSTOM)
    }
    awaitLspJobs(project)
    codeInsightFixture.checkResult("Test();sout")
  }

  @Test
  fun `on type formatting skipped with multiple carets`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test_multicarets.txt", "line1<caret>\nline2<caret>").virtualFile
    val serverSession = configureServerSession(project, virtualFile)

    val requestDeferred = serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, {
      it.textDocument.uri == serverSession.fileUri(virtualFile) && it.ch in triggerCharacters
    }) { listOf(TextEdit(Range(Position(0, 0), Position(0, 0)), "\t")) }

    // With multiple carets, on-type formatting should be skipped
    withContext(Dispatchers.EDT) {
      codeInsightFixture.type(";")
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }

    awaitLspJobs(project)
    codeInsightFixture.checkResult("line1;\nline2;")
    assertTrue { requestDeferred.isActive }
    requestDeferred.cancel()
  }

  @Test
  fun `on type formatting triggered once after 'fast' typing of 5 symbols (faster than server response)`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test_trigger.txt", "<caret>").virtualFile
    val serverSession = configureServerSession(project, virtualFile)
    val times = 5
    repeat(times) {
      serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, {
        it.textDocument.uri == serverSession.fileUri(virtualFile) && it.ch in triggerCharacters
      }) { listOf(TextEdit(Range(Position(0, 0), Position(0, 0)), "\t")) }
    }

    withContext(Dispatchers.EDT) {
      repeat(times) {
        codeInsightFixture.type(";")
      }
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    awaitLspJobs(project)
    codeInsightFixture.checkResult("\t;;;;;")
  }

  @Test
  fun `on type formatting not triggered by non-trigger character`(): Unit = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("test_nontrigger.txt", "<caret>").virtualFile
    val serverSession = configureServerSession(project, virtualFile)


    val requestDeferred = serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, {
      it.textDocument.uri == serverSession.fileUri(virtualFile) && it.ch in triggerCharacters
    }) { listOf(TextEdit(Range(Position(0, 0), Position(0, 0)), "\t")) }

    withContext(Dispatchers.EDT) {
      codeInsightFixture.type("a")
      codeInsightFixture.type("b")
      codeInsightFixture.type("c")
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
    awaitLspJobs(project)
    codeInsightFixture.checkResult("abc")
    assertTrue { requestDeferred.isActive }
    requestDeferred.cancel()
  }

  private suspend fun awaitLspJobs(project: Project) {
    LspCoroutineScopeService.getInstance(project).cs.coroutineContext.job.children.toList().joinAll()
  }
}

package com.intellij.platform.lsp

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspOnTypeFormattingSupport
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.impl.LspCoroutineScopeService
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
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
import org.eclipse.lsp4j.DocumentOnTypeFormattingRegistrationOptions
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.TextEdit
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
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

  @Nested
  inner class StaticRegistration {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
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
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

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
    fun `on type formatting request has the indent size as its tab size`(): Unit = timeoutRunBlocking {
      // before the file opens: its document keeps the indent options that it gets then
      val settingsManager = CodeStyleSettingsManager.getInstance(project)
      settingsManager.createTemporarySettings().getIndentOptions(PlainTextFileType.INSTANCE).apply {
        INDENT_SIZE = 3
        TAB_SIZE = 4
        USE_TAB_CHARACTER = false
      }
      try {
        val virtualFile = codeInsightFixture.configureByText("test_indent_size.txt", "line1<caret>").virtualFile
        val serverSession = fakeLspIntegration.serverSession(virtualFile)
        check(CodeStyle.getIndentOptions(project, virtualFile).INDENT_SIZE == 3) { "The temporary code style is not in effect" }
        serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, {
          it.textDocument.uri == serverSession.fileUri(virtualFile) && it.options.tabSize == 3 && it.options.isInsertSpaces
        }) { listOf(TextEdit(Range(Position(0, 0), Position(0, 0)), "   ")) }

        withContext(Dispatchers.EDT) {
          codeInsightFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)
        }

        awaitLspJobs(project)
        codeInsightFixture.checkResult("   line1\n")
      }
      finally {
        settingsManager.dropTemporarySettings()
      }
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
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

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
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

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
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

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
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

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
      val virtualFile = codeInsightFixture.configureByText("test_live_template.txt", "<caret>").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

      val requestDeferred = serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, {
        it.textDocument.uri == serverSession.fileUri(virtualFile) && it.ch in triggerCharacters
      }) { listOf(TextEdit(Range(Position(0, 0), Position(0, 0)), "\t")) }

      withContext(Dispatchers.EDT) {
        // In a unit test, a template ends right after it starts, unless template testing is on.
        TemplateManagerImpl.setTemplateTesting(codeInsightFixture.testRootDisposable)
        val templateManager = TemplateManager.getInstance(project)
        val template = templateManager.createTemplate("", "", "Test(\$ARG\$)")
        template.addVariable("ARG", "", "", true)
        templateManager.startTemplate(codeInsightFixture.editor, template)
        assertNotNull(templateManager.getActiveTemplate(codeInsightFixture.editor), "The live template should be active")
        codeInsightFixture.type(";")
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      }

      awaitLspJobs(project)
      codeInsightFixture.checkResult("Test(;)")
      assertTrue { requestDeferred.isActive }
      requestDeferred.cancel()
    }

    @Test
    fun `on type formatting skipped with multiple carets`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test_multicarets.txt", "line1<caret>\nline2<caret>").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

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
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val times = 5
      val fileUri = serverSession.fileUri(virtualFile)
      // The client skips the request of a character when the next character is typed before the request is sent.
      // So the requests of the first characters are optional, and the request of the last character must come.
      val earlierRequests = List(times - 1) {
        serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, {
          it.textDocument.uri == fileUri && it.ch in triggerCharacters && it.position.character < times
        }) { listOf(TextEdit(Range(Position(0, 0), Position(0, 0)), "\t")) }
      }
      serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, {
        it.textDocument.uri == fileUri && it.ch in triggerCharacters && it.position.character == times
      }) { listOf(TextEdit(Range(Position(0, 0), Position(0, 0)), "\t")) }

      withContext(Dispatchers.EDT) {
        repeat(times) {
          codeInsightFixture.type(";")
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      }
      awaitLspJobs(project)
      earlierRequests.forEach { it.cancel() }
      codeInsightFixture.checkResult("\t;;;;;")
    }

    @Test
    fun `on type formatting not triggered by non-trigger character`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test_nontrigger.txt", "<caret>").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)


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
  }

  @Nested
  inner class DynamicRegistration {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      lspCustomization = object : LspCustomization() {
        override val onTypeFormattingCustomizer = LspOnTypeFormattingSupport()
      },
    )

    @Test
    fun `the trigger characters of every matching dynamic registration send on type formatting`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test_dynamic.txt", "val x = 1<caret>").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(
          Registration("on-type-formatting-1", "textDocument/onTypeFormatting", DocumentOnTypeFormattingRegistrationOptions().apply {
            firstTriggerCharacter = "#"
          }),
          Registration("on-type-formatting-2", "textDocument/onTypeFormatting", DocumentOnTypeFormattingRegistrationOptions().apply {
            firstTriggerCharacter = "%"
          }),
        ))
      }

      // Each request inserts a tab, so the result shows the number of requests.
      for (triggerCharacter in listOf("#", "%")) {
        serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, {
          it.textDocument.uri == serverSession.fileUri(virtualFile) && it.ch == triggerCharacter
        }) { listOf(TextEdit(Range(Position(0, 0), Position(0, 0)), "\t")) }

        withContext(Dispatchers.EDT) {
          codeInsightFixture.type(triggerCharacter)
        }
        awaitLspJobs(project)
      }

      codeInsightFixture.checkResult("\t\tval x = 1#%")
    }
  }

  private suspend fun awaitLspJobs(project: Project) {
    LspCoroutineScopeService.getInstance(project).cs.coroutineContext.job.children.toList().joinAll()
  }
}

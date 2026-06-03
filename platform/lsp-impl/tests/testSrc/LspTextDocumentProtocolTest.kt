package com.intellij.platform.lsp

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.core.CoreBundle
import com.intellij.find.usages.impl.DefaultUsageSearchParameters
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import com.intellij.platform.lsp.api.customization.LspOnTypeFormattingSupport
import com.intellij.platform.lsp.common.FakeLspServerSupportProvider
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.impl.features.usages.LspSearchTarget
import com.intellij.platform.lsp.impl.features.usages.LspUsageSearcher
import com.intellij.platform.lsp.testFramework.checkHighlightingRetrying
import com.intellij.platform.lsp.util.getLsp4jPosition
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.eclipse.lsp4j.CodeLensOptions
import org.eclipse.lsp4j.Color
import org.eclipse.lsp4j.ColorInformation
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticRegistrationOptions
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.DocumentLinkOptions
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds


@TestApplication
internal class LspTextDocumentProtocolTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
    lspCustomization = object : LspCustomization() {
      override val formattingCustomizer = object : LspFormattingSupport() {
        override fun shouldFormatThisFileExclusivelyByServer(
          file: VirtualFile,
          ideCanFormatThisFileItself: Boolean,
          serverExplicitlyWantsToFormatThisFile: Boolean,
        ): Boolean = true
      }
      override val onTypeFormattingCustomizer = LspOnTypeFormattingSupport()
    },
    configureServerCapabilities = {
      textDocumentSync = Either.forRight(TextDocumentSyncOptions().apply {
        openClose = true
        change = TextDocumentSyncKind.Full
        save = Either.forLeft(true)
      })
      hoverProvider = Either.forLeft(true)
      setDefinitionProvider(Either.forLeft(true))
      completionProvider = CompletionOptions()
      codeActionProvider = Either.forLeft(true)
      documentFormattingProvider = Either.forLeft(true)
      foldingRangeProvider = Either.forLeft(true)
      colorProvider = Either.forLeft(true)
      inlayHintProvider = Either.forLeft(true)
      codeLensProvider = CodeLensOptions()
      documentLinkProvider = DocumentLinkOptions()
      semanticTokensProvider = SemanticTokensWithRegistrationOptions().apply {
        full = Either.forLeft(true)
        legend = SemanticTokensLegend(listOf("keyword"), listOf("declaration"))
      }
      diagnosticProvider = DiagnosticRegistrationOptions().apply {
        identifier = "test"
        isInterFileDependencies = false
        isWorkspaceDiagnostics = false
      }
      documentOnTypeFormattingProvider = DocumentOnTypeFormattingOptions().apply {
        firstTriggerCharacter = ";"
      }
      referencesProvider = Either.forLeft(true)
    },
  )

  @Nested
  inner class DocumentSync {
    @Test
    fun `textDocument didOpen -- server receives notification when file opens`() = timeoutRunBlocking {
      // Open the first file to bootstrap the server session
      val bootstrapFile = codeInsightFixture.configureByText("bootstrap.txt", "bootstrap").virtualFile
      val serverSession = configureServerSession(project, bootstrapFile)

      // Now prepare to open a second file and await its didOpen notification
      val secondFile = codeInsightFixture.addFileToProject("opened.txt", "hello world").virtualFile
      val expectedUri = serverSession.fileUri(secondFile)

      serverSession.expectNotification(serverSession.DID_OPEN) {
        it.textDocument.uri == expectedUri
      }

      assertFalse(FileEditorManager.getInstance(project).isFileOpen(secondFile))

      // Opening the file in an editor triggers didOpen
      withContext(Dispatchers.EDT) {
        FileEditorManager.getInstance(project).openFile(secondFile, true)
      }

      withTimeout(1.seconds) {
        serverSession.awaitExpected()
      }
    }

    @Test
    fun `textDocument didClose -- server receives notification when file closes`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("closed.txt", "content to close").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      val didClose = serverSession.expectNotification(serverSession.DID_CLOSE) {
        it.textDocument.uri == fileUri
      }

      withContext(Dispatchers.EDT) {
        FileEditorManager.getInstance(project).closeFile(virtualFile)
      }

      didClose.await()
    }

    @Test
    fun `textDocument didChange -- server receives notification with updated content`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("changed.txt", "original").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      val didChange = serverSession.expectNotification(serverSession.DID_CHANGE) {
        it.textDocument.uri == fileUri
      }

      edtWriteAction {
        codeInsightFixture.editor.document.setText("modified content")
      }

      didChange.await()
    }

    @Test
    fun `textDocument didSave -- server receives save notification`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("saved.txt", "save me").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      val didSave = serverSession.expectNotification(serverSession.DID_SAVE) {
        it.textDocument.uri == fileUri
      }

      // Modify document to make it dirty, then save
      edtWriteAction {
        codeInsightFixture.editor.document.setText("saved content")
      }

      withContext(Dispatchers.EDT) {
        FileDocumentManager.getInstance().saveAllDocuments()
      }

      didSave.await()
    }
  }

  @Nested
  inner class HoverProtocol {
    @Test
    fun `textDocument hover -- markup response rendered`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("hover.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.HOVER, { it.textDocument.uri == fileUri }) {
        Hover(MarkupContent(MarkupKind.PLAINTEXT, "Hello documentation"), Range(Position(0, 0), Position(0, 5)))
      }

      val offset = withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }
      val targets = readAction {
        IdeDocumentationTargetProvider.getInstance(project).documentationTargets(codeInsightFixture.editor, codeInsightFixture.file, offset)
      }
      serverSession.awaitExpected()

      assertEquals(1, targets.size)
      val html = readAction { (targets[0].computeDocumentation() as DocumentationData).html }
      assertEquals("<div class='content'>Hello documentation</div>", html)
    }
  }

  @Nested
  inner class CompletionProtocol {
    @Test
    fun `textDocument completion -- items returned from server`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("complete.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        Either.forLeft(listOf(
          CompletionItem("hello"),
          CompletionItem("world"),
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()

      assertNotNull(lookupElements)
      assertEquals(setOf("hello", "world"), lookupElements!!.map { it.lookupString }.toSet())
    }
  }

  @Nested
  inner class FormattingProtocol {
    @Test
    fun `textDocument formatting -- text edits applied`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("format.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.FORMATTING, { it.textDocument.uri == fileUri }) {
        listOf(TextEdit(Range(Position(0, 5), Position(0, 6)), "\n"))
      }

      writeCommandAction(project, CoreBundle.message("command.name.undefined")) {
        CodeStyleManager.getInstance(project).reformat(codeInsightFixture.file)
      }

      serverSession.awaitExpected()
      codeInsightFixture.checkResult("hello\nworld")
    }
  }

  @Nested
  inner class FoldingRangeProtocol {
    @Test
    fun `textDocument foldingRange -- folding ranges registered`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("fold.txt", "line 1\nline 2\nline 3\nline 4\nline 5").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.FOLDING_RANGE, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        listOf(FoldingRange(0, 2))
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()

      checkFoldRegionsRetrying(1) { regions ->
        val document = codeInsightFixture.editor.document
        assertEquals(0, document.getLineNumber(regions[0].startOffset))
        assertEquals(2, document.getLineNumber(regions[0].endOffset))
      }
    }

    private suspend fun checkFoldRegionsRetrying(expectedCount: Int, check: ((Array<FoldRegion>) -> Unit)? = null) {
      waitUntilAssertSucceeds(message = "Expected $expectedCount LSP fold regions") {
        readAction {
          val regions = codeInsightFixture.editor.foldingModel.allFoldRegions
          assertEquals(expectedCount, regions.size)
          check?.invoke(regions)
        }
      }
    }
  }

  @Nested
  inner class DocumentColorProtocol {
    @Test
    fun `textDocument documentColor -- color information mapped`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val sourceText = "color: red"
      val virtualFile = codeInsightFixture.configureByText("color.txt", sourceText).virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.DOCUMENT_COLOR, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        listOf(ColorInformation(Range(Position(0, 7), Position(0, 10)), Color(1.0, 0.0, 0.0, 1.0)))
      }

      launch(start = CoroutineStart.UNDISPATCHED) {
        checkInlaysRetrying(sourceText, "color: /*<# <image> #>*/red")
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }

    private suspend fun checkInlaysRetrying(sourceText: String, expected: String) {
      waitUntilAssertSucceeds(message = "Inlays don't match expected") {
        codeInsightFixture.doHighlighting()
        assertEquals(expected.trim(), dumpInlays(sourceText).trim())
      }
    }

    private fun dumpInlays(sourceText: String): String {
      return InlayDumpUtil.dumpInlays(
        sourceText,
        codeInsightFixture.editor,
        filter = null,
        renderer = { renderer: com.intellij.openapi.editor.EditorCustomElementRenderer, _: Inlay<*> -> renderer.toString() }
      )
    }
  }

  @Nested
  inner class DiagnosticsProtocol {
    @Test
    fun `textDocument publishDiagnostics -- diagnostics mapped to correct highlighting ranges`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("diag.txt", "<error descr=\"test error\">hello</error> world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      val job = launch(start = CoroutineStart.LAZY) {
        serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
          PublishDiagnosticsParams(fileUri, listOf(
            Diagnostic(Range(Position(0, 0), Position(0, 5)), "test error", DiagnosticSeverity.Error, "test")
          ))
        }
      }

      launch(start = CoroutineStart.UNDISPATCHED) {
        codeInsightFixture.checkHighlightingRetrying()
      }

      job.start()
    }
  }

  @Nested
  inner class PullDiagnosticsProtocol {
    @Test
    fun `textDocument diagnostic -- pull diagnostics requested`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("pulldiag.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(emptyList()))
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class CodeLensProtocol {
    @Test
    fun `textDocument codeLens -- code lenses requested`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("lens.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.CODE_LENS, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        emptyList()
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class DocumentLinkProtocol {
    @Test
    fun `textDocument documentLink -- links requested`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("link.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.DOCUMENT_LINK, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        emptyList()
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class InlayHintProtocol {
    @Test
    fun `textDocument inlayHint -- hints requested`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("hint.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        emptyList()
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class SemanticTokensProtocol {
    @Test
    fun `textDocument semanticTokensFull -- tokens requested`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val virtualFile = codeInsightFixture.configureByText("tokens.txt", "hello world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.SEMANTIC_TOKENS_FULL, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        SemanticTokens(emptyList())
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class DefinitionProtocol {
    @Test
    fun `textDocument definition -- definition location requested`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("def.txt", "hello <caret>world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.DEFINITION, { it.textDocument.uri == fileUri }) {
        Either.forLeft(emptyList())
      }

      codeInsightFixture.performEditorAction("GotoDeclaration")
      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class CodeActionProtocol {
    @Test
    fun `textDocument codeAction -- actions requested for range`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("action.txt", "hello <caret>world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.CODE_ACTION, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        emptyList()
      }

      codeInsightFixture.getAvailableIntentions()
      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class OnTypeFormattingProtocol {
    @Test
    fun `textDocument onTypeFormatting -- formatting requested on trigger character`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("ontype.txt", "<caret>").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, { it.textDocument.uri == serverSession.fileUri(virtualFile) }) {
        emptyList()
      }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.type(";")
      }

      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class ReferencesProtocol {
    @Test
    fun `textDocument references -- references requested`(): Unit = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("refs.txt", "hello <caret>world").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.REFERENCES, { it.textDocument.uri == fileUri }) {
        emptyList()
      }

      val lspServers = LspServerManager.getInstance(project).getServersForProvider(FakeLspServerSupportProvider::class.java)
      val document = readAction { FileDocumentManager.getInstance().getDocument(virtualFile)!! }
      val offset = withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }
      val position = readAction { getLsp4jPosition(document, offset) }

      val searchTarget = LspSearchTarget(lspServers, virtualFile, position)
      val params = DefaultUsageSearchParameters(project, searchTarget, GlobalSearchScope.projectScope(project))
      LspUsageSearcher().collectSearchRequest(params)?.forEach { }

      serverSession.awaitExpected()
    }
  }
}

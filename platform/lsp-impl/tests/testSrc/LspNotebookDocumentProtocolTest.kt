package com.intellij.platform.lsp

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.core.CoreBundle
import com.intellij.find.usages.api.PsiUsage
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
import com.intellij.platform.lsp.api.customization.LspRenameSupport
import com.intellij.psi.PsiFile
import com.intellij.platform.lsp.common.FakeLspServerSupportProvider
import com.intellij.platform.lsp.common.TestNotebookDocumentAdapter
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.impl.LspDocumentAdapter
import com.intellij.platform.lsp.impl.features.usages.LspSearchTarget
import com.intellij.platform.lsp.impl.features.usages.LspUsageSearcher
import com.intellij.platform.lsp.util.getLsp4jPosition
import com.intellij.platform.lsp.testFramework.checkHighlightingRetrying
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.CodeActionKind
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
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.NotebookDocumentSyncRegistrationOptions
import org.eclipse.lsp4j.NotebookSelector
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.RelatedFullDocumentDiagnosticReport
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextDocumentSyncOptions
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test


@TestApplication
internal class LspNotebookDocumentProtocolTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    @Suppress("unused")
    private val adapterFixture = extensionPointFixture(LspDocumentAdapter.EP_NAME) {
      TestNotebookDocumentAdapter()
    }

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
      override val renameCustomizer = object : LspRenameSupport() {
        override fun shouldRunRename(psiFile: PsiFile): Boolean = true
      }
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
      documentOnTypeFormattingProvider = DocumentOnTypeFormattingOptions().apply {
        firstTriggerCharacter = ";"
      }
      renameProvider = Either.forRight(RenameOptions())
      referencesProvider = Either.forLeft(true)
      diagnosticProvider = DiagnosticRegistrationOptions().apply {
        identifier = "test"
        isInterFileDependencies = false
        isWorkspaceDiagnostics = false
      }
      notebookDocumentSync = NotebookDocumentSyncRegistrationOptions().apply {
        notebookSelector = listOf(
          NotebookSelector().apply {
            notebook = Either.forLeft("test-notebook")
          }
        )
        save = true
      }
    },
  )

  @Nested
  inner class NotebookSync {
    @Test
    fun `notebookDocument didOpen -- sends correct notebook structure with 3 cells`() = timeoutRunBlocking {
      val content = "cell zero\n---\ncell one\n---\ncell two"

      val bootstrapFile = codeInsightFixture.configureByText("bootstrap.txt", "bootstrap").virtualFile
      val serverSession = configureServerSession(project, bootstrapFile)

      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val expectedUri = serverSession.fileUri(virtualFile)

      val didOpen = serverSession.expectNotification(serverSession.NOTEBOOK_DID_OPEN) { params ->
        val notebook = params.notebookDocument
        notebook.uri == expectedUri &&
        notebook.notebookType == "test-notebook" &&
        notebook.cells.size == 3 &&
        params.cellTextDocuments.size == 3 &&
        params.cellTextDocuments[0].text == "cell zero" &&
        params.cellTextDocuments[1].text == "cell one" &&
        params.cellTextDocuments[2].text == "cell two" &&
        params.cellTextDocuments[0].uri == "$expectedUri#cell-0" &&
        params.cellTextDocuments[1].uri == "$expectedUri#cell-1" &&
        params.cellTextDocuments[2].uri == "$expectedUri#cell-2"
      }

      withContext(Dispatchers.EDT) {
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
      }

      didOpen.await()
    }

    @Test
    fun `notebookDocument didClose -- sends correct identifiers`() = timeoutRunBlocking {
      val content = "cell zero\n---\ncell one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      val didClose = serverSession.expectNotification(serverSession.NOTEBOOK_DID_CLOSE) { params ->
        params.notebookDocument.uri == fileUri &&
        params.cellTextDocuments.size == 2 &&
        params.cellTextDocuments[0].uri == "$fileUri#cell-0" &&
        params.cellTextDocuments[1].uri == "$fileUri#cell-1"
      }

      withContext(Dispatchers.EDT) {
        FileEditorManager.getInstance(project).closeFile(virtualFile)
      }

      didClose.await()
    }

    @Test
    fun `notebookDocument didSave -- sends notebook identifier`() = timeoutRunBlocking {
      val content = "cell zero\n---\ncell one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      val didSave = serverSession.expectNotification(serverSession.NOTEBOOK_DID_SAVE) { params ->
        params.notebookDocument.uri == fileUri
      }

      edtWriteAction {
        codeInsightFixture.editor.document.setText("cell zero modified\n---\ncell one")
      }

      withContext(Dispatchers.EDT) {
        FileDocumentManager.getInstance().saveAllDocuments()
      }

      didSave.await()
    }

    @Test
    fun `notebookDocument didChange -- sends updated cell structure`() = timeoutRunBlocking {
      val content = "cell zero\n---\ncell one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      val didChange = serverSession.expectNotification(serverSession.NOTEBOOK_DID_CHANGE) { params ->
        params.notebookDocument.uri == fileUri
      }

      edtWriteAction {
        codeInsightFixture.editor.document.setText("cell zero modified\n---\ncell one modified")
      }

      didChange.await()
    }
  }

  @Nested
  inner class FeatureRoundTrip {
    @Test
    fun `textDocument hover -- request uses cell URI for notebook position`() = timeoutRunBlocking {
      val content = "cell zero\n---\ncell <caret>one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.HOVER, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) {
        Hover(MarkupContent(MarkupKind.PLAINTEXT, "Cell hover docs"), Range(Position(0, 0), Position(0, 8)))
      }

      val offset = withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }
      val targets = readAction {
        IdeDocumentationTargetProvider.getInstance(project).documentationTargets(codeInsightFixture.editor, codeInsightFixture.file, offset)
      }
      serverSession.awaitExpected()

      assertTrue(targets.isNotEmpty())
      val html = readAction { (targets[0].computeDocumentation() as DocumentationData).html }
      assertEquals("<div class='content'>Cell hover docs</div>", html)
    }

    @Test
    fun `textDocument hover -- response range from non-first cell mapped to correct host position`() = timeoutRunBlocking {
      // Cell 0 = "cell zero" (host line 0), Cell 1 = "cell one" (host line 2)
      // Server returns hover range (0,0)-(0,8) for cell 1 → should map to host (2,0)-(2,8) = "cell one"
      // Without mapping, the range would be applied to host line 0 → "cell zer"
      val content = "cell zero\n---\ncell <caret>one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.HOVER, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) {
        Hover(MarkupContent(MarkupKind.PLAINTEXT, "Cell hover docs"), Range(Position(0, 0), Position(0, 8)))
      }

      val offset = withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }
      val targets = readAction {
        IdeDocumentationTargetProvider.getInstance(project).documentationTargets(codeInsightFixture.editor, codeInsightFixture.file, offset)
      }
      serverSession.awaitExpected()

      assertTrue(targets.isNotEmpty())
      val presentableText = readAction { targets[0].computePresentation().presentableText }
      assertEquals("cell one", presentableText)
    }

    @Test
    fun `textDocument completion -- request uses cell URI for notebook position`(): Unit = timeoutRunBlocking {
      val content = "cell zero\n---\n<caret>"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.COMPLETION, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) {
        Either.forLeft(listOf(
          CompletionItem("notebook_item_1"),
          CompletionItem("notebook_item_2"),
        ))
      }

      val lookupElements = codeInsightFixture.completeBasic()
      serverSession.awaitExpected()

      assertNotNull(lookupElements)
      assertEquals(setOf("notebook_item_1", "notebook_item_2"), lookupElements!!.map { it.lookupString }.toSet())
    }

    @Test
    fun `textDocument definition -- request uses cell URI for notebook position`(): Unit = timeoutRunBlocking {
      val content = "target definition\n---\ngoto <caret>here"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.DEFINITION, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) {
        Either.forRight(listOf(
          LocationLink("$fileUri#cell-0", Range(Position(0, 0), Position(0, 17)), Range(Position(0, 0), Position(0, 6)), Range(Position(0, 5), Position(0, 9)))
        ))
      }

      codeInsightFixture.performEditorAction("GotoDeclaration")
      serverSession.awaitExpected()
    }

    @Test
    fun `publishDiagnostics -- cell URI diagnostics mapped to host document`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val content = "<error descr=\"test error\">hello</error>\n---\nworld"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      val job = launch(start = CoroutineStart.LAZY) {
        serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
          PublishDiagnosticsParams("$fileUri#cell-0", listOf(
            Diagnostic(Range(Position(0, 0), Position(0, 5)), "test error", DiagnosticSeverity.Error, "test")
          ))
        }
      }

      launch(start = CoroutineStart.UNDISPATCHED) {
        codeInsightFixture.checkHighlightingRetrying()
      }

      job.start()
    }

    @Test
    fun `publishDiagnostics -- non-first cell diagnostics mapped to correct host position`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      // Cell 0 = "hello" (host line 0), Cell 1 = "world" (host line 2)
      // Diagnostic at cell-relative (0,0)-(0,5) in cell 1 → host (2,0)-(2,5)
      val content = "hello\n---\n<error descr=\"test error\">world</error>"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      val job = launch(start = CoroutineStart.LAZY) {
        serverSession.sendNotification(serverSession.PUBLISH_DIAGNOSTICS) {
          PublishDiagnosticsParams("$fileUri#cell-1", listOf(
            Diagnostic(Range(Position(0, 0), Position(0, 5)), "test error", DiagnosticSeverity.Error, "test")
          ))
        }
      }

      launch(start = CoroutineStart.UNDISPATCHED) {
        codeInsightFixture.checkHighlightingRetrying()
      }

      job.start()
    }

    @Test
    fun `textDocument foldingRange -- request uses cell URI for notebook file`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val content = "line 0\nline 1\nline 2\n---\ncell one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.FOLDING_RANGE, { params ->
        params.textDocument.uri == "$fileUri#cell-0"
      }) { listOf(FoldingRange(0, 1)) }

      serverSession.expectRequest(serverSession.FOLDING_RANGE, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) { emptyList() }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }

    @Test
    fun `textDocument documentColor -- request uses cell URI for notebook file`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val sourceText = "color: red\n---\ncell one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", sourceText).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.DOCUMENT_COLOR, { params ->
        params.textDocument.uri == "$fileUri#cell-0"
      }) { listOf(ColorInformation(Range(Position(0, 7), Position(0, 10)), Color(1.0, 0.0, 0.0, 1.0))) }

      serverSession.expectRequest(serverSession.DOCUMENT_COLOR, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) { emptyList() }

      launch(start = CoroutineStart.UNDISPATCHED) {
        checkInlaysRetrying(sourceText, "color: /*<# <image> #>*/red\n---\ncell one")
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }

    @Test
    fun `textDocument codeLens -- request uses cell URI for notebook file`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val content = "hello world\n---\ncell one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.CODE_LENS, { params ->
        params.textDocument.uri == "$fileUri#cell-0"
      }) { emptyList() }

      serverSession.expectRequest(serverSession.CODE_LENS, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) { emptyList() }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }

    @Test
    fun `textDocument documentLink -- request uses cell URI for notebook file`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val content = "hello world\n---\ncell one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.DOCUMENT_LINK, { params ->
        params.textDocument.uri == "$fileUri#cell-0"
      }) { emptyList() }

      serverSession.expectRequest(serverSession.DOCUMENT_LINK, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) { emptyList() }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }

    @Test
    fun `textDocument inlayHint -- request uses cell URI for notebook file`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val content = "hello world\n---\ncell one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.INLAY_HINT, { params ->
        params.textDocument.uri == "$fileUri#cell-0"
      }) { emptyList() }

      serverSession.expectRequest(serverSession.INLAY_HINT, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) { emptyList() }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }

    @Test
    fun `textDocument semanticTokensFull -- request uses cell URI for notebook file`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val content = "hello world\n---\ncell one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.SEMANTIC_TOKENS_FULL, { params ->
        params.textDocument.uri == "$fileUri#cell-0"
      }) { SemanticTokens(emptyList()) }

      serverSession.expectRequest(serverSession.SEMANTIC_TOKENS_FULL, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) { SemanticTokens(emptyList()) }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }

    @Test
    fun `textDocument diagnostic -- pull diagnostics request uses cell URI for notebook file`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val content = "hello world\n---\ncell one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { params ->
        params.textDocument.uri == "$fileUri#cell-0"
      }) { DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(emptyList())) }

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) { DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(emptyList())) }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }

    @Test
    fun `textDocument formatting -- request uses cell URI for notebook file`(): Unit = timeoutRunBlocking {
      val content = "hello world\n---\ncell one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.FORMATTING, { params ->
        params.textDocument.uri == "$fileUri#cell-0"
      }) { listOf(TextEdit(Range(Position(0, 5), Position(0, 6)), "\n")) }

      serverSession.expectRequest(serverSession.FORMATTING, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) { emptyList() }

      writeCommandAction(project, CoreBundle.message("command.name.undefined")) {
        CodeStyleManager.getInstance(project).reformat(codeInsightFixture.file)
      }

      serverSession.awaitExpected()
      codeInsightFixture.checkResult("hello\nworld\n---\ncell one")
    }

    @Test
    fun `textDocument onTypeFormatting -- request uses cell URI for notebook position`(): Unit = timeoutRunBlocking {
      val content = "cell zero\n---\n<caret>"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.ON_TYPE_FORMATTING, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) { emptyList() }

      withContext(Dispatchers.EDT) {
        codeInsightFixture.type(";")
      }

      serverSession.awaitExpected()
    }

    @Test
    fun `textDocument codeAction -- request uses cell URI for notebook position`(): Unit = timeoutRunBlocking {
      val content = "cell zero\n---\ncell <caret>one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.CODE_ACTION, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) { emptyList() }

      codeInsightFixture.getAvailableIntentions()
      serverSession.awaitExpected()
    }

    @Test
    fun `textDocument codeAction quick fix -- uses correct cell URI for diagnostic on non-first cell`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      // Cell 0 = "cell zero" (host lines 0), Cell 1 = "cell one" (host line 2)
      // Diagnostic at cell-relative (0,0)-(0,8) in cell 1
      // Bug: forEachDocumentInFile interprets cell-relative line 0 as host line 0 → cell 0
      val content = "cell zero\n---\ncell <caret>one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      // Pull diagnostics: cell 0 empty, cell 1 has error
      serverSession.expectRequest(serverSession.DIAGNOSTIC, { params ->
        params.textDocument.uri == "$fileUri#cell-0"
      }) { DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(emptyList())) }

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(listOf(
          Diagnostic(Range(Position(0, 0), Position(0, 8)), "test error", DiagnosticSeverity.Error, "test")
        )))
      }

      // Quick fix path should send CODE_ACTION to cell 1 with QuickFix kind
      serverSession.expectRequest(serverSession.CODE_ACTION, { params ->
        params.textDocument.uri == "$fileUri#cell-1" &&
        params.context.only?.contains(CodeActionKind.QuickFix) == true
      }) { emptyList() }

      codeInsightFixture.doHighlighting()
      codeInsightFixture.getAvailableIntentions()
      serverSession.awaitExpected()
    }

    @Test
    fun `textDocument references -- request uses cell URI for notebook position`(): Unit = timeoutRunBlocking {
      val content = "cell zero\n---\ncell <caret>one"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.REFERENCES, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) {
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

    @Test
    fun `textDocument references -- response locations mapped to host document for notebook`(): Unit = timeoutRunBlocking {
      // Cell 0 = "ab" (host line 0), Cell 1 = "cde fgh" (host line 2)
      // Bug: cell-relative (0,0)-(0,3) in cell 1 should map to host (2,0)-(2,3)
      //       but without mapping it applies (0,0)-(0,3) to host line 0 ("ab") which fails
      val content = "ab\n---\ncde <caret>fgh"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      // Server returns a reference in cell-1 at cell-relative position (0,0)-(0,3)
      serverSession.expectRequest(serverSession.REFERENCES, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) {
        listOf(Location("$fileUri#cell-1", Range(Position(0, 0), Position(0, 3))))
      }

      val lspServers = LspServerManager.getInstance(project).getServersForProvider(FakeLspServerSupportProvider::class.java)
      val document = readAction { FileDocumentManager.getInstance().getDocument(virtualFile)!! }
      val offset = withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset }
      val position = readAction { getLsp4jPosition(document, offset) }

      val searchTarget = LspSearchTarget(lspServers, virtualFile, position)
      val params = DefaultUsageSearchParameters(project, searchTarget, GlobalSearchScope.projectScope(project))
      val usages = mutableListOf<com.intellij.find.usages.api.Usage>()
      LspUsageSearcher().collectSearchRequest(params)?.forEach { usages.add(it) }

      serverSession.awaitExpected()

      // Must find 1 usage covering "cde" (offset 8..11 in host), not "ab\n" (offset 0..3)
      assertEquals(1, usages.size)
      readAction {
        val psiUsage = usages[0] as PsiUsage
        val usageText = psiUsage.file.fileDocument.getText(psiUsage.range)
        assertEquals("cde", usageText, "Usage should point to 'cde' in cell 1, not to cell 0 content")
      }
    }

    @Disabled("flaky")
    @Test
    fun `textDocument foldingRange -- folding ranges from non-first cell mapped to correct host lines`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      // Cell 0 = "line 0\nline 1\nline 2" (host lines 0-2), Cell 1 = "aaa\nbbb\nccc" (host lines 4-6)
      // Server returns fold range (0,1) for cell 1 → should map to host lines (4,5)
      val content = "line 0\nline 1\nline 2\n---\naaa\nbbb\nccc"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.FOLDING_RANGE, { params ->
        params.textDocument.uri == "$fileUri#cell-0"
      }) { emptyList() }

      serverSession.expectRequest(serverSession.FOLDING_RANGE, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) { listOf(FoldingRange(0, 1)) }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()

      // Verify fold region is at host lines 4-5, not cell-relative 0-1
      checkFoldRegionsRetrying(1) { regions ->
        val document = codeInsightFixture.editor.document
        assertEquals(4, document.getLineNumber(regions[0].startOffset),
          "Fold start should be at host line 4 (cell 1, cell-relative line 0)")
        assertEquals(5, document.getLineNumber(regions[0].endOffset),
          "Fold end should be at host line 5 (cell 1, cell-relative line 1)")
      }
    }

    @Test
    fun `textDocument pullDiagnostics -- diagnostics from non-first cell mapped to correct host position`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      // Cell 0 = "hello" (host line 0), Cell 1 = "world" (host line 2)
      // Pull diagnostic at cell-relative (0,0)-(0,5) in cell 1 → host line 2 offset range
      val content = "hello\n---\nworld"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { params ->
        params.textDocument.uri == "$fileUri#cell-0"
      }) { DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(emptyList())) }

      serverSession.expectRequest(serverSession.DIAGNOSTIC, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) {
        DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(listOf(
          Diagnostic(Range(Position(0, 0), Position(0, 5)), "pull error", DiagnosticSeverity.Error, "test")
        )))
      }

      // First doHighlighting triggers pull diagnostic requests handled by expectRequest above
      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()

      // Verify diagnostic was mapped to correct host position (line 2, "world")
      val document = codeInsightFixture.editor.document
      val expectedStart = document.getLineStartOffset(2) // "world" starts at host line 2
      val expectedEnd = expectedStart + 5                 // "world".length

      // Second doHighlighting retrieves HighlightInfo list with mapped positions
      val highlights = codeInsightFixture.doHighlighting()
      val pullError = requireNotNull(highlights.find { it.description == "pull error" }) {
        "Expected 'pull error' diagnostic in highlights"
      }
      assertEquals(expectedStart, pullError.startOffset, "Diagnostic start should be at beginning of host line 2")
      assertEquals(expectedEnd, pullError.endOffset, "Diagnostic end should be after 'world' on host line 2")
    }

    @Test
    fun `textDocument definition -- response location links mapped to host document for notebook`(): Unit = timeoutRunBlocking {
      // Cell 0 = "target def" (host line 0), Cell 1 = "goto here" (host line 2)
      // Definition at cell-relative (0,0)-(0,10) in cell 0 → host (0,0)-(0,10)
      // This tests that LocationLink targetUri with cell fragment is mapped back to the host file URI
      val content = "target def\n---\ngoto <caret>here"
      val virtualFile = codeInsightFixture.configureByText("test.test-notebook", content).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.DEFINITION, { params ->
        params.textDocument.uri == "$fileUri#cell-1"
      }) {
        // Server returns a location link with cell-0 targetUri.
        // The response mapper should resolve the cell fragment to the host file.
        Either.forRight(listOf(
          LocationLink(
            "$fileUri#cell-0",
            Range(Position(0, 0), Position(0, 10)),
            Range(Position(0, 0), Position(0, 6)),
            Range(Position(0, 5), Position(0, 9))
          )
        ))
      }

      codeInsightFixture.performEditorAction("GotoDeclaration")
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
}

package com.intellij.platform.lsp

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo.LineMarkerGutterIconRenderer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspDocumentSymbolCustomizer
import com.intellij.platform.lsp.api.customization.LspDocumentSymbolDisabled
import com.intellij.platform.lsp.api.customization.LspInheritanceMarkersCustomizer
import com.intellij.platform.lsp.api.customization.LspInheritanceMarkersSupport
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiElement
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.delay
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration

@TestApplication
internal class LspInheritanceMarkersTest {
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
    lspCustomization = InheritanceMarkersTestCustomization(),
    configureServerCapabilities = {
      documentSymbolProvider = Either.forLeft(true)
      implementationProvider = Either.forLeft(true)
      typeHierarchyProvider = Either.forLeft(true)
    },
  )

  @BeforeEach
  fun setUp() {
    LspHighlightingCache.quiescenceDelayOverride = Duration.ZERO
  }

  @AfterEach
  fun tearDown() {
    LspHighlightingCache.quiescenceDelayOverride = null
  }

  /**
   * A marker response can arrive while a test highlighting pass still runs.
   * The daemon restart from [com.intellij.platform.lsp.impl.features.LspFeaturesRefreshing.refreshLineMarkers]
   * then counts as a model change for the strict test assertion.
   * Allow it, like the other reactive LSP feature tests do.
   */
  private fun configureFile(name: String, text: String): VirtualFile {
    (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    return codeInsightFixture.configureByText(name, text).virtualFile
  }

  private suspend fun gutterTooltips(): List<String?> {
    // findAllGutters runs highlighting itself, so it must stay outside the read action.
    val gutters = codeInsightFixture.findAllGutters()
    return readAction { gutters.map { it.tooltipText } }
  }

  @Test
  fun `method of a subtyped class gets the overridden marker`() = timeoutRunBlocking {
    val virtualFile = configureFile("markers1.txt", "class C { fun foo() }")
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL, { it.textDocument.uri == fileUri }) {
      val member = methodSymbol("foo", selectionRange = range(0, 14, 0, 17))
      listOf(Either.forRight(classSymbol("C", SymbolKind.Class, range(0, 6, 0, 7), member)))
    }
    serverSession.expectRequest(serverSession.PREPARE_TYPE_HIERARCHY, { it.position == Position(0, 6) }) {
      listOf(typeHierarchyItem("C", fileUri, range(0, 6, 0, 7)))
    }
    serverSession.expectRequest(serverSession.TYPE_HIERARCHY_SUBTYPES) {
      listOf(typeHierarchyItem("Sub", fileUri, range(0, 8, 0, 9)))
    }
    serverSession.expectRequest(serverSession.IMPLEMENTATION, { it.position == Position(0, 14) }) {
      Either.forLeft(listOf(Location(fileUri, range(0, 10, 0, 11))))
    }

    waitUntilAssertSucceeds(message = "The gutter markers are not shown") {
      codeInsightFixture.doHighlighting()
      assertEquals(listOf("Has subtypes", "Is overridden"), gutterTooltips().sortedBy { it })
    }
    serverSession.awaitExpected()
  }

  @Test
  fun `interface and its member get the implemented markers`() = timeoutRunBlocking {
    val virtualFile = configureFile("markers2.txt", "interface I { fun m() }")
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL, { it.textDocument.uri == fileUri }) {
      val member = methodSymbol("m", selectionRange = range(0, 18, 0, 19))
      listOf(Either.forRight(classSymbol("I", SymbolKind.Interface, range(0, 10, 0, 11), member)))
    }
    serverSession.expectRequest(serverSession.IMPLEMENTATION, { it.position == Position(0, 18) }) {
      Either.forLeft(listOf(Location(fileUri, range(0, 0, 0, 1))))
    }
    serverSession.expectRequest(serverSession.PREPARE_TYPE_HIERARCHY, { it.position == Position(0, 10) }) {
      listOf(typeHierarchyItem("I", fileUri, range(0, 10, 0, 11)))
    }
    serverSession.expectRequest(serverSession.TYPE_HIERARCHY_SUBTYPES) {
      listOf(typeHierarchyItem("Impl", fileUri, range(0, 14, 0, 15)))
    }

    waitUntilAssertSucceeds(message = "The gutter markers are not shown") {
      codeInsightFixture.doHighlighting()
      assertEquals(listOf("Has implementations", "Has implementations"), gutterTooltips())
    }
    serverSession.awaitExpected()
  }

  @Test
  fun `empty results produce no markers`() = timeoutRunBlocking {
    val virtualFile = configureFile("markers3.txt", "interface I { fun m() }")
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL, { it.textDocument.uri == fileUri }) {
      val member = methodSymbol("m", selectionRange = range(0, 18, 0, 19))
      listOf(Either.forRight(classSymbol("I", SymbolKind.Interface, range(0, 10, 0, 11), member)))
    }
    serverSession.expectRequest(serverSession.IMPLEMENTATION, { it.position == Position(0, 18) }) {
      Either.forLeft(emptyList())
    }
    serverSession.expectRequest(serverSession.PREPARE_TYPE_HIERARCHY, { it.position == Position(0, 10) }) {
      listOf(typeHierarchyItem("I", fileUri, range(0, 10, 0, 11)))
    }
    serverSession.expectRequest(serverSession.TYPE_HIERARCHY_SUBTYPES) { emptyList() }

    codeInsightFixture.doHighlighting()
    serverSession.awaitExpected()

    codeInsightFixture.doHighlighting()
    assertTrue(gutterTooltips().isEmpty(), "No gutter markers expected for empty results")
  }

  @Test
  fun `class without subtypes suppresses its method queries`() = timeoutRunBlocking {
    val virtualFile = configureFile("markers7.txt", "class C { fun foo() }")
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL, { it.textDocument.uri == fileUri }) {
      val member = methodSymbol("foo", selectionRange = range(0, 14, 0, 17))
      listOf(Either.forRight(classSymbol("C", SymbolKind.Class, range(0, 6, 0, 7), member)))
    }
    serverSession.expectRequest(serverSession.PREPARE_TYPE_HIERARCHY, { it.position == Position(0, 6) }) {
      listOf(typeHierarchyItem("C", fileUri, range(0, 6, 0, 7)))
    }
    serverSession.expectRequest(serverSession.TYPE_HIERARCHY_SUBTYPES) { emptyList() }
    val implementationRequested = serverSession.expectRequest(serverSession.IMPLEMENTATION) { Either.forLeft(emptyList()) }

    codeInsightFixture.doHighlighting()
    delay(500)

    assertFalse(implementationRequested.isCompleted, "A method of a class without subtypes must not be queried")
    assertTrue(gutterTooltips().isEmpty())
    // Releases the never-matched expectation, so the test scope can finish.
    implementationRequested.cancel()
  }

  @Test
  fun `top-level function is not queried`() = timeoutRunBlocking {
    val virtualFile = configureFile("markers8.txt", "fun foo() {}")
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL, { it.textDocument.uri == fileUri }) {
      listOf(Either.forRight(methodSymbol("foo", selectionRange = range(0, 4, 0, 7))))
    }
    val implementationRequested = serverSession.expectRequest(serverSession.IMPLEMENTATION) { Either.forLeft(emptyList()) }

    codeInsightFixture.doHighlighting()
    delay(500)

    assertFalse(implementationRequested.isCompleted, "A top-level function must not be queried")
    assertTrue(gutterTooltips().isEmpty())
    // Releases the never-matched expectation, so the test scope can finish.
    implementationRequested.cancel()
  }

  @Test
  fun `no requests when the customizer forbids the file`() = timeoutRunBlocking {
    val virtualFile = configureFile("disabled.txt", "fun foo() {}")
    val serverSession = configureServerSession(project, virtualFile)

    val documentSymbolRequested = serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL) { emptyList() }

    codeInsightFixture.doHighlighting()
    delay(500)

    assertFalse(documentSymbolRequested.isCompleted, "documentSymbol must not be requested for a forbidden file")
    assertTrue(gutterTooltips().isEmpty())
    // Releases the never-matched expectation, so the test scope can finish.
    documentSymbolRequested.cancel()
  }

  @Test
  fun `symbol count over the cap suppresses per-symbol requests`() = timeoutRunBlocking {
    val virtualFile = configureFile("markers4.txt", "x".repeat(250))
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL, { it.textDocument.uri == fileUri }) {
      val members = (2..202).map { i -> methodSymbol("m$i", selectionRange = range(0, i, 0, i + 1)) }
      listOf(Either.forRight(classSymbol("I", SymbolKind.Interface, range(0, 0, 0, 1), *members.toTypedArray())))
    }
    val prepareRequested = serverSession.expectRequest(serverSession.PREPARE_TYPE_HIERARCHY) { emptyList() }
    val implementationRequested = serverSession.expectRequest(serverSession.IMPLEMENTATION) { Either.forLeft(emptyList()) }

    codeInsightFixture.doHighlighting()
    delay(500)

    assertFalse(prepareRequested.isCompleted, "No per-symbol request expected over the symbol cap")
    assertFalse(implementationRequested.isCompleted, "No per-symbol request expected over the symbol cap")
    assertTrue(gutterTooltips().isEmpty())
    // Releases the never-matched expectations, so the test scope can finish.
    prepareRequested.cancel()
    implementationRequested.cancel()
  }

  @Test
  fun `typing cancels the in-flight batch and a new pull follows`() = timeoutRunBlocking {
    val virtualFile = configureFile("markers5.txt", "interface I { fun m() }")
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL, { it.textDocument.uri == fileUri }) {
      val member = methodSymbol("m", selectionRange = range(0, 18, 0, 19))
      listOf(Either.forRight(classSymbol("I", SymbolKind.Interface, range(0, 10, 0, 11), member)))
    }
    serverSession.expectRequest(serverSession.PREPARE_TYPE_HIERARCHY, { it.position == Position(0, 10) }) {
      listOf(typeHierarchyItem("I", fileUri, range(0, 10, 0, 11)))
    }
    serverSession.expectRequest(serverSession.TYPE_HIERARCHY_SUBTYPES) { emptyList() }
    val pendingImplementation = CompletableFuture<Either<List<Location>, List<org.eclipse.lsp4j.LocationLink>>>()
    val implementationRequested = serverSession.expectRequestAsync(serverSession.IMPLEMENTATION) { pendingImplementation }

    codeInsightFixture.doHighlighting()
    implementationRequested.await()

    // The second pull round for the edited document.
    serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL, { it.textDocument.uri == fileUri }) {
      val member = methodSymbol("m", selectionRange = range(0, 18, 0, 19))
      listOf(Either.forRight(classSymbol("I", SymbolKind.Interface, range(0, 10, 0, 11), member)))
    }
    serverSession.expectRequest(serverSession.PREPARE_TYPE_HIERARCHY, { it.position == Position(0, 10) }) {
      listOf(typeHierarchyItem("I", fileUri, range(0, 10, 0, 11)))
    }
    serverSession.expectRequest(serverSession.TYPE_HIERARCHY_SUBTYPES) { emptyList() }
    serverSession.expectRequest(serverSession.IMPLEMENTATION, { it.position == Position(0, 18) }) {
      Either.forLeft(listOf(Location(fileUri, range(0, 0, 0, 1))))
    }

    writeCommandAction(project, "typing") {
      codeInsightFixture.editor.document.insertString(codeInsightFixture.editor.document.textLength, " ")
    }

    waitUntilAssertSucceeds(message = "The in-flight request is not cancelled") {
      codeInsightFixture.doHighlighting()
      assertTrue(pendingImplementation.isCancelled, "The client must cancel the stale in-flight request")
    }
    waitUntilAssertSucceeds(message = "The gutter marker is not shown after the second pull") {
      codeInsightFixture.doHighlighting()
      assertEquals(listOf("Has implementations"), gutterTooltips())
    }
    serverSession.awaitExpected()
  }

  @Test
  fun `gutter click re-requests the targets and navigates`() = timeoutRunBlocking {
    val virtualFile = configureFile("markers6.txt", "interface I { fun m() }")
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)

    serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL, { it.textDocument.uri == fileUri }) {
      val member = methodSymbol("m", selectionRange = range(0, 18, 0, 19))
      listOf(Either.forRight(classSymbol("I", SymbolKind.Interface, range(0, 10, 0, 11), member)))
    }
    serverSession.expectRequest(serverSession.PREPARE_TYPE_HIERARCHY, { it.position == Position(0, 10) }) {
      listOf(typeHierarchyItem("I", fileUri, range(0, 10, 0, 11)))
    }
    serverSession.expectRequest(serverSession.TYPE_HIERARCHY_SUBTYPES) { emptyList() }
    serverSession.expectRequest(serverSession.IMPLEMENTATION, { it.position == Position(0, 18) }) {
      Either.forLeft(listOf(Location(fileUri, range(0, 3, 0, 4))))
    }

    waitUntilAssertSucceeds(message = "The gutter marker is not shown") {
      codeInsightFixture.doHighlighting()
      assertEquals(1, gutterTooltips().size)
    }

    // The click handler re-requests the targets for freshness.
    serverSession.expectRequest(serverSession.IMPLEMENTATION, { it.position == Position(0, 18) }) {
      Either.forLeft(listOf(Location(fileUri, range(0, 3, 0, 4))))
    }

    val renderer = codeInsightFixture.findAllGutters().single() as LineMarkerGutterIconRenderer<*>
    val (handler, anchor) = readAction {
      val info = renderer.lineMarkerInfo
      @Suppress("UNCHECKED_CAST")
      (info.navigationHandler as GutterIconNavigationHandler<PsiElement>) to info.element
    }
    handler.navigate(null, anchor)

    waitUntilAssertSucceeds(message = "The editor caret did not move to the target") {
      val editor = FileEditorManager.getInstance(project).selectedTextEditor
      assertEquals(Position(0, 3), editor?.caretModel?.logicalPosition?.let { Position(it.line, it.column) })
    }
    serverSession.awaitExpected()
  }

  private fun methodSymbol(name: String, selectionRange: Range): DocumentSymbol =
    DocumentSymbol(name, SymbolKind.Method, selectionRange, selectionRange)

  private fun classSymbol(name: String, kind: SymbolKind, selectionRange: Range, vararg members: DocumentSymbol): DocumentSymbol =
    DocumentSymbol(name, kind, selectionRange, selectionRange).apply {
      children = members.toMutableList()
    }

  private fun typeHierarchyItem(name: String, uri: String, itemRange: Range): TypeHierarchyItem =
    TypeHierarchyItem(name, SymbolKind.Interface, uri, itemRange, itemRange)

  private fun range(startLine: Int, startChar: Int, endLine: Int, endChar: Int): Range =
    Range(Position(startLine, startChar), Position(endLine, endChar))
}

private class InheritanceMarkersTestCustomization : LspCustomization() {
  // Keeps the breadcrumbs and structure view features off.
  // Their documentSymbol requests would consume the expectations that the marker pulls need.
  override val documentSymbolCustomizer: LspDocumentSymbolCustomizer = LspDocumentSymbolDisabled

  override val inheritanceMarkersCustomizer: LspInheritanceMarkersCustomizer = object : LspInheritanceMarkersSupport() {
    override fun shouldAskServerForMarkers(file: VirtualFile): Boolean = !file.name.startsWith("disabled")
  }
}

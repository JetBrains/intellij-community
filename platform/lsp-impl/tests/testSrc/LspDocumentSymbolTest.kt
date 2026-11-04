// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel
import com.intellij.ide.structureView.newStructureView.StructureViewComponent
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.impl.stickyLines.StickyLinesCollector
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspDocumentSymbolCustomizer
import com.intellij.platform.lsp.api.customization.LspDocumentSymbolDisabled
import com.intellij.platform.lsp.api.customization.LspDocumentSymbolSupport
import com.intellij.platform.lsp.common.FakeLspIntegration
import com.intellij.platform.lsp.common.FakeLspServerSession
import com.intellij.platform.lsp.common.breadcrumbsCollector
import com.intellij.platform.lsp.common.crumbsAtCaret
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.common.moveCaret
import com.intellij.platform.lsp.common.navBarChildren
import com.intellij.platform.lsp.common.nullResponse
import com.intellij.platform.lsp.impl.features.documentSymbol.LspCrumb
import com.intellij.platform.lsp.impl.features.documentSymbol.LspFileBreadcrumbsCollector
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.StructureViewComponentTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.ui.components.breadcrumbs.Crumb
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.SymbolTag
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
internal class LspDocumentSymbolTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    /**
     * ```
     * class C {
     *   fun foo() {}
     *   fun bar() {}
     * }
     * ```
     */
    private const val TEXT = "class C {\n  fun foo() {}\n  fun bar() {}\n}"
    private const val FOO_NAME_OFFSET = 17
    private const val BAR_NAME_OFFSET = 32

    private fun range(startLine: Int, startChar: Int, endLine: Int, endChar: Int) =
      Range(Position(startLine, startChar), Position(endLine, endChar))

    private fun hierarchicalSymbols(): List<Either<SymbolInformation, DocumentSymbol>> {
      val foo = DocumentSymbol("foo", SymbolKind.Method, range(1, 2, 1, 14), range(1, 6, 1, 9), "()")
      val bar = DocumentSymbol("bar", SymbolKind.Method, range(2, 2, 2, 14), range(2, 6, 2, 9), "()")
      val clazz = DocumentSymbol("C", SymbolKind.Class, range(0, 0, 3, 1), range(0, 6, 0, 7)).apply {
        children = mutableListOf(foo, bar)
      }
      return listOf(Either.forRight(clazz))
    }
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private val documentSymbolServer: ServerCapabilities.() -> Unit = {
    documentSymbolProvider = Either.forLeft(true)
  }

  private fun configure(fileName: String, caretOffset: Int = FOO_NAME_OFFSET): VirtualFile {
    val text = TEXT.substring(0, caretOffset) + "<caret>" + TEXT.substring(caretOffset)
    return codeInsightFixture.configureByText(fileName, text).virtualFile
  }

  private suspend fun FakeLspIntegration.expectDocumentSymbols(
    file: VirtualFile,
    symbols: List<Either<SymbolInformation, DocumentSymbol>>? = hierarchicalSymbols(),
  ): FakeLspServerSession {
    val serverSession = serverSession(file)
    val fileUri = serverSession.fileUri(file)
    serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL, { it.textDocument.uri == fileUri }) { symbols ?: nullResponse() }
    return serverSession
  }

  /** The returned deferred completes if one more `textDocument/documentSymbol` request arrives. Cancel it at the end of the test. */
  private fun FakeLspServerSession.expectExtraDocumentSymbolRequest(): Deferred<Unit> =
    expectRequest(DOCUMENT_SYMBOL) { emptyList() }

  private suspend fun crumbsAt(offset: Int): List<Crumb> {
    codeInsightFixture.moveCaret(offset)
    return codeInsightFixture.crumbsAtCaret()
  }

  /** Collects the sticky lines of the file as the sticky lines pass does, as (start offset, end offset) pairs. */
  private suspend fun stickyLines(file: VirtualFile): List<Pair<Int, Int>> = withContext(Dispatchers.Default) {
    readAction {
      val document = FileDocumentManager.getInstance().getDocument(file)!!
      StickyLinesCollector(project, document).collectLines(file, EmptyProgressIndicator())
        .map { it.textOffset() to it.endOffset() }
        .sortedBy { it.first }
    }
  }

  private suspend fun handledByLspBreadcrumbs(file: VirtualFile): Boolean =
    codeInsightFixture.breadcrumbsCollector(file) is LspFileBreadcrumbsCollector

  private suspend fun navBarNames(psiFile: PsiFile): List<String?> = readAction {
    navBarChildren(psiFile).map { (it as? PsiNamedElement)?.name }
  }

  /** The structure view builder that the platform picks for the file in the editor; null when no language or LSP server provides one. */
  private suspend fun structureViewBuilder(): StructureViewBuilder? = readAction {
    LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(codeInsightFixture.file)
  }

  /**
   * Builds the structure view for the file in the editor, as the Structure tool window does, and passes it to [consumer] on the EDT.
   * The LSP model sends `textDocument/documentSymbol` synchronously while the view is built, so the server must answer without waiting for the test.
   */
  private suspend fun <T> withStructureView(consumer: (StructureViewComponent) -> T): T = withContext(Dispatchers.Default) {
    var result: T? = null
    StructureViewComponentTestUtil.testStructureView(codeInsightFixture) { component ->
      PlatformTestUtil.expandAll(component.tree)
      result = consumer(component)
    }
    @Suppress("UNCHECKED_CAST")
    result as T
  }

  private fun StructureViewComponent.topLevelNames(): List<String?> =
    treeModel.root.children.map { it.presentation.presentableText }

  private fun dump(element: TreeElement, indent: String = ""): String = buildString {
    val presentation = element.presentation
    append(indent).append(presentation.presentableText)
    presentation.locationString?.takeIf { it.isNotEmpty() }?.let { append(" ").append(it) }
    append(" [").append(iconName(presentation.getIcon(false))).append("]")
    if ((presentation as? ColoredItemPresentation)?.textAttributesKey == CodeInsightColors.DEPRECATED_ATTRIBUTES) append(" deprecated")
    append("\n")
    for (child in element.children) append(dump(child, "$indent  "))
  }

  private fun iconName(icon: Any?): String = when (icon) {
    AllIcons.Nodes.Class -> "Class"
    AllIcons.Nodes.Method -> "Method"
    AllIcons.Nodes.Field -> "Field"
    null -> "no icon"
    else -> icon.toString()
  }

  private suspend fun typeAtEnd(file: VirtualFile, text: String) {
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) {
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        document.insertString(document.textLength, text)
      }
    }
  }

  @Nested
  inner class Breadcrumbs {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(configureServerCapabilities = documentSymbolServer)

    @Test
    fun `crumbs at the method name are the class and the method with kind icons`() = timeoutRunBlocking {
      val file = configure("crumbs1.txt")
      fakeLspIntegration.expectDocumentSymbols(file)

      val crumbs = codeInsightFixture.crumbsAtCaret()

      assertEquals(listOf("C", "foo"), crumbs.map { it.text })
      assertEquals(listOf("Class", "Method"), crumbs.map { iconName(it.icon) })
    }

    @Test
    fun `crumbs at the caret outside every method stop at the class`() = timeoutRunBlocking {
      val file = configure("crumbs2.txt", caretOffset = 3)
      fakeLspIntegration.expectDocumentSymbols(file)

      val crumbs = codeInsightFixture.crumbsAtCaret()

      assertEquals(listOf("C"), crumbs.map { it.text })
    }

    @Test
    fun `a flat SymbolInformation answer in random order nests by range`() = timeoutRunBlocking {
      val file = configure("crumbs3.txt")
      val serverSession = fakeLspIntegration.serverSession(file)
      val fileUri = serverSession.fileUri(file)
      fun info(name: String, kind: SymbolKind, range: Range) =
        Either.forLeft<SymbolInformation, DocumentSymbol>(SymbolInformation(name, kind, Location(fileUri, range)))
      fakeLspIntegration.expectDocumentSymbols(file, listOf(
        info("bar", SymbolKind.Method, range(2, 2, 2, 14)),
        info("C", SymbolKind.Class, range(0, 0, 3, 1)),
        info("foo", SymbolKind.Method, range(1, 2, 1, 14)),
      ))

      val fooCrumbs = crumbsAt(FOO_NAME_OFFSET)
      val barCrumbs = crumbsAt(BAR_NAME_OFFSET)

      assertEquals(listOf("C", "foo"), fooCrumbs.map { it.text })
      assertEquals(listOf("C", "bar"), barCrumbs.map { it.text })
    }

    @Test
    fun `navigating a crumb with selection puts the caret at the name and selects the symbol range`() = timeoutRunBlocking {
      val file = configure("crumbs4.txt")
      fakeLspIntegration.expectDocumentSymbols(file)
      val fooCrumb = codeInsightFixture.crumbsAtCaret().last() as LspCrumb
      codeInsightFixture.moveCaret(0)

      val (caret, selection) = withContext(Dispatchers.EDT) {
        val editor = codeInsightFixture.editor
        fooCrumb.navigate(editor, true)
        editor.caretModel.offset to (editor.selectionModel.selectionStart to editor.selectionModel.selectionEnd)
      }

      assertEquals(16, caret, "The caret should be at the start of the selection range")
      assertEquals(12 to 24, selection, "The selection should be the symbol range")
    }

    @Test
    fun `cached symbols serve another caret position without a new request`() = timeoutRunBlocking {
      val file = configure("crumbs5.txt")
      val serverSession = fakeLspIntegration.expectDocumentSymbols(file)
      codeInsightFixture.crumbsAtCaret()
      val extraRequest = serverSession.expectExtraDocumentSymbolRequest()

      val barCrumbs = crumbsAt(BAR_NAME_OFFSET)

      assertEquals(listOf("C", "bar"), barCrumbs.map { it.text })
      assertFalse(extraRequest.isCompleted, "The second caret position should use the cached symbols")
      extraRequest.cancel()
    }

    @Test
    fun `an edit in another file keeps the cache, an edit in this document asks again`() = timeoutRunBlocking {
      val otherFile = codeInsightFixture.addFileToProject("crumbs6-other.txt", "other").virtualFile
      val file = configure("crumbs6.txt")
      val serverSession = fakeLspIntegration.expectDocumentSymbols(file)
      codeInsightFixture.crumbsAtCaret()
      val extraRequest = serverSession.expectExtraDocumentSymbolRequest()

      typeAtEnd(otherFile, " text")
      val crumbsAfterOtherEdit = codeInsightFixture.crumbsAtCaret()

      assertEquals(listOf("C", "foo"), crumbsAfterOtherEdit.map { it.text })
      assertFalse(extraRequest.isCompleted, "An edit in another file should not invalidate the symbols of this file")

      typeAtEnd(file, "\n")
      codeInsightFixture.crumbsAtCaret()

      assertTrue(extraRequest.isCompleted, "An edit in this document should send a new request")
    }

    @Test
    fun `an empty answer gives no crumbs and is cached`() = timeoutRunBlocking {
      val file = configure("crumbs7.txt")
      val serverSession = fakeLspIntegration.expectDocumentSymbols(file, emptyList())

      val firstCrumbs = codeInsightFixture.crumbsAtCaret()
      val extraRequest = serverSession.expectExtraDocumentSymbolRequest()
      val secondCrumbs = crumbsAt(BAR_NAME_OFFSET)

      assertEquals(emptyList<Crumb>(), firstCrumbs)
      assertEquals(emptyList<Crumb>(), secondCrumbs)
      assertFalse(extraRequest.isCompleted, "An empty answer should be cached")
      extraRequest.cancel()
    }

    @Test
    @Disabled("A null documentSymbol answer is not cached (LspPerFileCache drops it), so every caret move asks again")
    fun `a null answer gives no crumbs and is cached`() = timeoutRunBlocking {
      val file = configure("crumbs8.txt")
      val serverSession = fakeLspIntegration.expectDocumentSymbols(file, null)

      val firstCrumbs = codeInsightFixture.crumbsAtCaret()
      val extraRequest = serverSession.expectExtraDocumentSymbolRequest()
      crumbsAt(BAR_NAME_OFFSET)
      crumbsAt(3)

      assertEquals(emptyList<Crumb>(), firstCrumbs)
      assertFalse(extraRequest.isCompleted, "A null answer should be cached until the document changes")
      extraRequest.cancel()
    }

    @Test
    fun `structure view, crumbs and navigation bar asking at the same time send one request`() = timeoutRunBlocking {
      val file = configure("crumbs9.txt")
      val psiFile = codeInsightFixture.file
      val serverSession = fakeLspIntegration.serverSession(file)
      val release = CompletableDeferred<Unit>()
      val firstRequest = serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL) {
        release.await()
        hierarchicalSymbols()
      }
      val extraRequest = serverSession.expectExtraDocumentSymbolRequest()

      val crumbs = async(Dispatchers.Default) { codeInsightFixture.crumbsAtCaret().map { it.text } }
      firstRequest.await()
      val navBar = async(Dispatchers.Default) { navBarNames(psiFile) }
      val structureView = async(Dispatchers.Default) { withStructureView { it.topLevelNames() } }
      // the callers either join the pending request or read the cached answer; both give one request
      delay(100)
      release.complete(Unit)
      awaitAll(crumbs, navBar, structureView)

      assertEquals(listOf("C", "foo"), crumbs.await())
      assertEquals(listOf("C"), navBar.await())
      assertEquals(listOf("C"), structureView.await())
      assertFalse(extraRequest.isCompleted, "All callers should share one documentSymbol request")
      extraRequest.cancel()
    }
  }

  @Nested
  inner class StickyLines {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(configureServerCapabilities = documentSymbolServer)

    @Test
    fun `each sticky line starts at the symbol name and ends at the symbol range end`() = timeoutRunBlocking {
      val file = configure("sticky1.txt")
      fakeLspIntegration.expectDocumentSymbols(file)

      val stickyLines = stickyLines(file)

      assertEquals(listOf(6 to 41, 16 to 24, 31 to 39), stickyLines)
    }

    @Test
    @Disabled("StickyLineInfo throws IllegalArgumentException when the name starts at or after the symbol range end, e.g. an empty range")
    fun `a symbol with an empty range gives no sticky line and no exception`() = timeoutRunBlocking {
      val file = configure("sticky2.txt")
      val empty = DocumentSymbol("empty", SymbolKind.Variable, range(1, 14, 1, 14), range(1, 14, 1, 14))
      val clazz = DocumentSymbol("C", SymbolKind.Class, range(0, 0, 3, 1), range(0, 6, 0, 7)).apply {
        children = mutableListOf(empty)
      }
      fakeLspIntegration.expectDocumentSymbols(file, listOf(Either.forRight(clazz)))

      val stickyLines = stickyLines(file)

      assertEquals(listOf(6 to 41), stickyLines)
    }
  }

  @Nested
  inner class StructureView {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(configureServerCapabilities = documentSymbolServer)

    @Test
    fun `hierarchical symbols give names, details, icons and children`() = timeoutRunBlocking {
      val file = configure("structure1.txt")
      fakeLspIntegration.expectDocumentSymbols(file)

      val (tree, presentations) = withStructureView { component ->
        PlatformTestUtil.print(component.tree, false) to component.treeModel.root.children.joinToString("") { dump(it) }
      }

      assertEquals("""
        -structure1.txt
         -C
          foo
          bar
      """.trimIndent(), tree)
      assertEquals("""
        C [Class]
          foo () [Method]
          bar () [Method]

      """.trimIndent(), presentations)
    }

    @Test
    fun `a Deprecated tag or the deprecated flag uses the deprecated attributes`() = timeoutRunBlocking {
      val file = configure("structure2.txt")
      val foo = DocumentSymbol("foo", SymbolKind.Method, range(1, 2, 1, 14), range(1, 6, 1, 9)).apply {
        tags = listOf(SymbolTag.Deprecated)
      }
      val bar = DocumentSymbol("bar", SymbolKind.Method, range(2, 2, 2, 14), range(2, 6, 2, 9)).apply {
        @Suppress("DEPRECATION")
        deprecated = true
      }
      val clazz = DocumentSymbol("C", SymbolKind.Class, range(0, 0, 3, 1), range(0, 6, 0, 7)).apply {
        children = mutableListOf(foo, bar)
      }
      fakeLspIntegration.expectDocumentSymbols(file, listOf(Either.forRight(clazz)))

      val tree = withStructureView { component -> component.treeModel.root.children.joinToString("") { dump(it) } }

      assertEquals("""
        C [Class]
          foo [Method] deprecated
          bar [Method] deprecated

      """.trimIndent(), tree)
    }

    @Test
    fun `the current editor element is the innermost symbol around the caret`() = timeoutRunBlocking {
      val file = configure("structure3.txt", caretOffset = BAR_NAME_OFFSET)
      fakeLspIntegration.expectDocumentSymbols(file)

      val current = withStructureView { component -> component.treeModel.currentEditorElement }

      assertEquals("bar", (current as DocumentSymbol).name)
    }

    @Test
    fun `navigating a node puts the caret at the start of the selection range`() = timeoutRunBlocking {
      val file = configure("structure4.txt", caretOffset = 0)
      fakeLspIntegration.expectDocumentSymbols(file)

      val caret = withStructureView { component ->
        val bar = component.treeModel.root.children.single().children[1] as StructureViewTreeElement
        bar.navigate(false)
        codeInsightFixture.editor.caretModel.offset
      }

      assertEquals(31, caret)
    }

    @Test
    @Disabled("The model loads symbols once in its constructor, and isValid() checks only the PSI modification count (C12)")
    fun `a model built while the server answers null picks up the symbols the server sends later`() = timeoutRunBlocking {
      val file = configure("structure5.txt")
      val serverSession = fakeLspIntegration.expectDocumentSymbols(file, null)
      val laterRequest = serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL) { hierarchicalSymbols() }

      val (before, after) = withStructureView { component ->
        val model = component.treeModel as TextEditorBasedStructureViewModel
        component.topLevelNames() to if (model.isValid) component.topLevelNames() else null
      }

      assertEquals(emptyList<String?>(), before)
      assertTrue(after == null || after == listOf("C"),
                 "The model should become invalid or show the new symbols, but it is valid with children $after")
      laterRequest.cancel()
    }
  }

  @Nested
  inner class StructureViewDisabledByCustomizer {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      lspCustomization = object : LspCustomization() {
        override val documentSymbolCustomizer: LspDocumentSymbolCustomizer = object : LspDocumentSymbolSupport() {
          override val structureViewSupport: Boolean = false
        }
      },
      configureServerCapabilities = documentSymbolServer,
    )

    @Test
    fun `no LSP structure view, but breadcrumbs work`() = timeoutRunBlocking {
      val file = configure("noStructure1.txt")
      fakeLspIntegration.expectDocumentSymbols(file)

      val builder = structureViewBuilder()
      val crumbs = codeInsightFixture.crumbsAtCaret()

      assertNull(builder, "The structure view should not use LSP")
      assertEquals(listOf("C", "foo"), crumbs.map { it.text })
    }
  }

  @Nested
  inner class BreadcrumbsDisabledByCustomizer {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      lspCustomization = object : LspCustomization() {
        override val documentSymbolCustomizer: LspDocumentSymbolCustomizer = object : LspDocumentSymbolSupport() {
          override val breadcrumbsSupport: Boolean = false
        }
      },
      configureServerCapabilities = documentSymbolServer,
    )

    @Test
    fun `no crumbs, sticky lines or navigation bar members, but the structure view works`() = timeoutRunBlocking {
      val file = configure("noCrumbs1.txt")
      val serverSession = fakeLspIntegration.expectDocumentSymbols(file)

      val handled = handledByLspBreadcrumbs(file)
      val crumbs = codeInsightFixture.crumbsAtCaret()
      val stickyLines = stickyLines(file)
      val navBar = navBarNames(codeInsightFixture.file)
      val structureView = withStructureView { it.topLevelNames() }

      assertFalse(handled, "The platform should not pick the LSP breadcrumbs collector")
      assertEquals(emptyList<Crumb>(), crumbs)
      assertEquals(emptyList<Pair<Int, Int>>(), stickyLines)
      assertEquals(emptyList<String?>(), navBar)
      assertEquals(listOf("C"), structureView)
      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class DocumentSymbolDisabledByCustomizer {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      lspCustomization = object : LspCustomization() {
        override val documentSymbolCustomizer: LspDocumentSymbolCustomizer = LspDocumentSymbolDisabled
      },
      configureServerCapabilities = documentSymbolServer,
    )

    @Test
    fun `no breadcrumbs and no LSP structure view`() = timeoutRunBlocking {
      val file = configure("disabled1.txt")
      fakeLspIntegration.serverSession(file)

      val handled = handledByLspBreadcrumbs(file)
      val builder = structureViewBuilder()

      assertFalse(handled, "The platform should not pick the LSP breadcrumbs collector")
      assertNull(builder, "The structure view should not use LSP")
    }
  }

  @Nested
  inner class NoDocumentSymbolCapability {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

    @Test
    fun `no breadcrumbs and no LSP structure view`() = timeoutRunBlocking {
      val file = configure("noCapability1.txt")
      fakeLspIntegration.serverSession(file)

      val handled = handledByLspBreadcrumbs(file)
      val builder = structureViewBuilder()

      assertFalse(handled, "The platform should not pick the LSP breadcrumbs collector")
      assertNull(builder, "The structure view should not use LSP")
    }
  }

  @Nested
  inner class DocumentSymbolProviderFalse {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      configureServerCapabilities = {
        documentSymbolProvider = Either.forLeft(false)
      },
    )

    @Test
    @Disabled("documentSymbolProvider: false counts as supported, supportsDocumentSymbol checks only != null (C1)")
    fun `no breadcrumbs and no LSP structure view`() = timeoutRunBlocking {
      val file = configure("providerFalse1.txt")
      fakeLspIntegration.serverSession(file)

      val handled = handledByLspBreadcrumbs(file)
      val builder = structureViewBuilder()

      assertFalse(handled, "The platform should not pick the LSP breadcrumbs collector")
      assertNull(builder, "The structure view should not use LSP")
    }
  }
}

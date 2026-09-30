// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.navigation.getCtrlMouseData
import com.intellij.ide.IdeBundle
import com.intellij.model.Symbol
import com.intellij.model.psi.impl.targetSymbols
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.actionSystem.IdeActions.ACTION_GOTO_DECLARATION
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.impl.DirectoryNavigationRequest
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.lsp.common.FakeLspServerSession
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.impl.features.highlighting.LspHighlightingApplier
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkOptions
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [textDocument/documentLink](https://microsoft.github.io/language-server-protocol/specification/#textDocument_documentLink)
 * and its rendering, resolution, navigation and labels.
 *
 * Links reach the editor through [LspHighlightingApplier], so the tests await the highlighting rather than call a provider directly.
 */
@TestApplication
internal class LspDocumentLinkTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    /**
     * `LINK` at offsets 4..8 is the link. Nothing in the text looks like a path or a URL,
     * so no native reference competes with the LSP one.
     */
    private const val TEXT: String = "see LINK for details"
    private val LINK_RANGE: Range = Range(Position(0, 4), Position(0, 8))
    private val LINK_TEXT_RANGE: TextRange = TextRange(4, 8)
    private const val OFFSET_IN_LINK: Int = 6
    private const val URL: String = "https://example.com/page"
    private const val TARGET_FILE_NAME: String = "target.txt"
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
    configureServerCapabilities = {
      documentLinkProvider = DocumentLinkOptions(true)
    },
  )

  private fun FakeLspServerSession.expectDocumentLink(fileUri: String, documentLink: DocumentLink) =
    expectRequest(DOCUMENT_LINK, { it.textDocument.uri == fileUri }) {
      listOf(documentLink)
    }

  private fun documentLink(target: String? = null, tooltip: String? = null): DocumentLink =
    DocumentLink(LINK_RANGE, target, null, tooltip)

  /** Collects what the applier renders, retrying until the response to `textDocument/documentLink` has arrived. */
  private suspend fun awaitLinkHighlighting(): List<HighlightInfo> {
    var highlights: List<HighlightInfo> = emptyList()
    waitUntilAssertSucceeds("Expected the document link to be highlighted") {
      highlights = readAction {
        LspHighlightingApplier.getInstance(project).collectHighlightInfos(
          codeInsightFixture.file, codeInsightFixture.file.virtualFile, codeInsightFixture.editor.document)
      }
      assertTrue(highlights.isNotEmpty(), "No highlighting collected yet")
    }
    return highlights
  }

  private suspend fun awaitLinkLabel(): String? = awaitLinkHighlighting().single().description

  /**
   * Resolves the link, which is what sends `documentLink/resolve`.
   * The request is synchronous, so this must not run on the EDT.
   */
  private suspend fun resolveLinkSymbols(): Collection<Symbol> = readAction {
    targetSymbols(codeInsightFixture.file, OFFSET_IN_LINK)
  }

  private suspend fun resolveLinkSymbol(): Symbol {
    val symbols = resolveLinkSymbols()
    assertEquals(1, symbols.size, "Expected the document link to resolve to a single symbol: $symbols")
    return symbols.single()
  }

  private suspend fun navigationRequest(symbol: Symbol): NavigationRequest? = readAction {
    val targets = (symbol as NavigatableSymbol).getNavigationTargets(project)
    assertEquals(1, targets.size, "Expected a single navigation target: $targets")
    targets.single().navigationRequest()
  }

  private suspend fun presentation(symbol: Symbol): TargetPresentation = readAction {
    (symbol as DocumentationTarget).computePresentation()
  }

  private fun addTargetFile(): VirtualFile =
    codeInsightFixture.addFileToProject(TARGET_FILE_NAME, "target file contents").virtualFile

  @Nested
  inner class Rendering {
    @Test
    fun `given a link when the file is highlighted then its range is rendered as a link`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      serverSession.expectDocumentLink(serverSession.fileUri(virtualFile), documentLink(target = URL))

      val highlighting = awaitLinkHighlighting().single()

      serverSession.awaitExpected()
      assertEquals(LINK_TEXT_RANGE, TextRange(highlighting.startOffset, highlighting.endOffset))
      assertEquals(CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES, highlighting.forcedTextAttributesKey)
    }

    @Test
    fun `given a tooltip from the server when the file is highlighted then it wins over the defaults`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      serverSession.expectDocumentLink(serverSession.fileUri(virtualFile), documentLink(target = URL, tooltip = "Open the manual"))

      val label = awaitLinkLabel()

      serverSession.awaitExpected()
      assertTrue(label!!.startsWith("Open the manual"), "Expected the server tooltip in: $label")
    }

    @Test
    fun `given no tooltip and a URL target when the file is highlighted then the label offers to open a browser`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      serverSession.expectDocumentLink(serverSession.fileUri(virtualFile), documentLink(target = URL))

      val label = awaitLinkLabel()

      serverSession.awaitExpected()
      assertTrue(label!!.startsWith(IdeBundle.message("open.url.in.browser.tooltip")), "Unexpected label: $label")
    }

  }

  @Nested
  inner class Resolution {
    @Test
    fun `given a link without a target when the reference resolves then the resolved target is used`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val targetFile = addTargetFile()
      val serverSession = configureServerSession(project, virtualFile)
      val targetUri = serverSession.fileUri(targetFile)
      serverSession.expectDocumentLink(serverSession.fileUri(virtualFile), documentLink())
      serverSession.expectRequest(serverSession.DOCUMENT_LINK_RESOLVE, { it.range == LINK_RANGE }) {
        documentLink(target = targetUri)
      }

      awaitLinkHighlighting()
      val request = navigationRequest(resolveLinkSymbol())

      serverSession.awaitExpected()
      assertEquals(targetFile, assertInstanceOf(SourceNavigationRequest::class.java, request).file)
    }

    @Test
    fun `given a file target when the reference resolves then navigation opens the file at its start`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val targetFile = addTargetFile()
      val serverSession = configureServerSession(project, virtualFile)
      serverSession.expectDocumentLink(serverSession.fileUri(virtualFile), documentLink(target = serverSession.fileUri(targetFile)))

      awaitLinkHighlighting()
      val request = navigationRequest(resolveLinkSymbol())

      serverSession.awaitExpected()
      val sourceRequest = assertInstanceOf(SourceNavigationRequest::class.java, request)
      assertEquals(targetFile, sourceRequest.file)
      assertEquals(0, sourceRequest.offsetMarker?.startOffset)
    }

    @Test
    fun `given a directory target when the reference resolves then navigation goes to the directory`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val directory = virtualFile.parent
      val serverSession = configureServerSession(project, virtualFile)
      serverSession.expectDocumentLink(serverSession.fileUri(virtualFile), documentLink(target = serverSession.fileUri(directory)))

      awaitLinkHighlighting()
      val request = navigationRequest(resolveLinkSymbol())

      serverSession.awaitExpected()
      assertEquals(directory, assertInstanceOf(DirectoryNavigationRequest::class.java, request).directory.virtualFile)
    }

    @Test
    fun `given a URL target when the reference resolves then navigation leaves the IDE`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      serverSession.expectDocumentLink(serverSession.fileUri(virtualFile), documentLink(target = URL))

      awaitLinkHighlighting()
      val symbol = resolveLinkSymbol()
      val request = navigationRequest(symbol)

      serverSession.awaitExpected()
      assertEquals(URL, presentation(symbol).presentableText)
      assertNotNull(assertInstanceOf(RawNavigationRequest::class.java, request), "Expected the browser to be asked to open the URL")
    }

    @Test
    fun `given a target with no file when the reference resolves then there is no symbol`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val missingFileUri = serverSession.fileUri(virtualFile) + ".missing"
      serverSession.expectDocumentLink(serverSession.fileUri(virtualFile), documentLink(target = missingFileUri))

      awaitLinkHighlighting()
      val symbols = resolveLinkSymbols()

      serverSession.awaitExpected()
      assertTrue(symbols.isEmpty(), "Expected no symbol for a target that resolves to no file: $symbols")
    }
  }

  @Nested
  inner class Labels {
    @Test
    fun `given no link under the pointer then there is no hint`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      serverSession.expectDocumentLink(serverSession.fileUri(virtualFile), documentLink(target = URL))

      awaitLinkHighlighting()
      val hint = readAction {
        getCtrlMouseData(ACTION_GOTO_DECLARATION, codeInsightFixture.editor, codeInsightFixture.file, 0)?.hintText
      }

      serverSession.awaitExpected()
      assertNull(hint, "Expected no hint outside the link range")
    }
  }
}

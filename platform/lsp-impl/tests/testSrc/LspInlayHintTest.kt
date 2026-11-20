package com.intellij.platform.lsp

import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.codeInsight.hints.presentation.DynamicInsetPresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.InsetPresentation
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.codeInsight.hints.presentation.StaticDelegatePresentation
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.common.FakeLspServerSession
import com.intellij.platform.lsp.common.UnansweredRequest
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.impl.LspCoroutineScopeService
import com.intellij.platform.lsp.impl.features.documentation.createLspDocumentationData
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.awaitPendingEditorOpen
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintLabelPart
import org.eclipse.lsp4j.InlayHintRegistrationOptions
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.Unregistration
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent


@TestApplication
internal class LspInlayHintTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private enum class HintChange { EDIT, SERVER_REFRESH }

  @Nested
  inner class TooltipContent {
    @Test
    fun `plain text tooltip escapes HTML without interpreting markdown`(): Unit = timeoutRunBlocking {
      val tooltip = MarkupContent(MarkupKind.PLAINTEXT, "**plain** <pending> & <paid>")
      val html = readAction { createLspDocumentationData(tooltip).toHtml(project) }

      assertEquals("<div class='content'>**plain** &lt;pending&gt; &amp; &lt;paid&gt;</div>", html)
    }

    @Test
    fun `markdown tooltip renders a leading code block and description`(): Unit = timeoutRunBlocking {
      val tooltip = MarkupContent(MarkupKind.MARKDOWN, "```\nDecimal total\n```\n**Amount**")
      val html = readAction { createLspDocumentationData(tooltip).toHtml(project) }

      assertTrue(html.contains("class='definition'"), html)
      assertTrue(html.contains("Decimal"), html)
      assertTrue(html.contains("<b>Amount</b>"), html)
    }
  }

  @Nested
  inner class StaticRegistration {
    private var supportsResolve = true

    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      configureServerCapabilities = {
        inlayHintProvider = Either.forRight(InlayHintRegistrationOptions().apply {
          resolveProvider = supportsResolve
        })
      },
    )

    @Test
    fun `basic inlay hint request`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val sourceText = "hello world"
      val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

      serverSession.expectRequest(
        serverSession.INLAY_HINT,
        { it.textDocument.uri == serverSession.fileUri(virtualFile) },
      ) {
        listOf(InlayHint(Position(0, 5), Either.forLeft(": String")))
      }

      launch(start = CoroutineStart.UNDISPATCHED) {
        checkInlaysRetrying(sourceText, "hello/*<# : String #>*/ world")
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }

    @Test
    fun `multiple inlay hints request`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val sourceText = "foo bar baz"
      val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

      serverSession.expectRequest(
        serverSession.INLAY_HINT,
        { it.textDocument.uri == serverSession.fileUri(virtualFile) },
      ) {
        listOf(
          InlayHint(Position(0, 3), Either.forLeft(": Int")),
          InlayHint(Position(0, 7), Either.forLeft(": String")),
        )
      }

      launch(start = CoroutineStart.UNDISPATCHED) {
        checkInlaysRetrying(sourceText, "foo/*<# : Int #>*/ bar/*<# : String #>*/ baz")
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }

    @Test
    fun `empty inlay hints request`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val sourceText = "hello world"
      val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

      serverSession.expectRequest(
        serverSession.INLAY_HINT,
        { it.textDocument.uri == serverSession.fileUri(virtualFile) },
      ) { emptyList() }

      launch(start = CoroutineStart.UNDISPATCHED) {
        checkInlaysRetrying(sourceText, "hello world")
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }

    @Test
    fun `inlay hint with composite label parts`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val sourceText = "hello world"
      val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

      serverSession.expectRequest(
        serverSession.INLAY_HINT,
        { it.textDocument.uri == serverSession.fileUri(virtualFile) },
      ) {
        listOf(InlayHint(Position(0, 5), Either.forRight(mutableListOf(
          InlayHintLabelPart("hello"),
          InlayHintLabelPart("world"),
        ))))
      }

      launch(start = CoroutineStart.UNDISPATCHED) {
        checkInlaysRetrying(sourceText, "hello/*<# [hello world] #>*/ world")
      }

      codeInsightFixture.doHighlighting()
      serverSession.awaitExpected()
    }

    @Test
    fun `inlays removed when refresh returns no hints`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val sourceText = "hello world"
      val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
        listOf(InlayHint(Position(0, 5), Either.forLeft(": A")))
      }
      checkInlaysRetrying(sourceText, "hello/*<# : A #>*/ world")

      // The server now returns no hints; the existing inlay must be disposed (the diff's "dispose leftovers" path).
      // Append at the end to invalidate the cache so the pass re-requests.
      serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
        emptyList()
      }
      writeCommandAction(project, "") {
        codeInsightFixture.editor.document.insertString(sourceText.length, " x")
      }
      checkInlaysRetrying("hello world x", "hello world x")

      serverSession.awaitExpected()
    }

    @Test
    fun `two inlay hints at the same offset`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val sourceText = "hello world"
      val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
        listOf(
          InlayHint(Position(0, 5), Either.forLeft(": A")),
          InlayHint(Position(0, 5), Either.forLeft(": B")),
        )
      }
      waitUntilAssertSucceeds(message = "Both same-offset inlays should be present") {
        codeInsightFixture.doHighlighting()
        assertEquals(listOf(": A", ": B"), managedInlaysAt(5).map { it.renderer.toString() }.sorted())
      }

      val inlayABefore = managedInlaysAt(5).single { it.renderer.toString() == ": A" }

      // Second response: ": A" unchanged, the co-located ": B" changes to ": C".
      serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
        listOf(
          InlayHint(Position(0, 5), Either.forLeft(": A")),
          InlayHint(Position(0, 5), Either.forLeft(": C")),
        )
      }
      // Append at the end (does not shift offset 5) to invalidate the cache so the pass re-requests.
      writeCommandAction(project, "") {
        codeInsightFixture.editor.document.insertString(sourceText.length, " x")
      }
      waitUntilAssertSucceeds(message = "Co-located hint should be updated") {
        codeInsightFixture.doHighlighting()
        assertEquals(listOf(": A", ": C"), managedInlaysAt(5).map { it.renderer.toString() }.sorted())
      }

      assertSame(inlayABefore, managedInlaysAt(5).single { it.renderer.toString() == ": A" },
                 "Unchanged co-located inlay must be reused, not recreated")

      serverSession.awaitExpected()
    }

    @Test
    fun `unchanged inlay reused across refresh, changed inlay recreated`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val sourceText = "foo bar baz"
      val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
        listOf(
          InlayHint(Position(0, 3), Either.forLeft(": Int")),
          InlayHint(Position(0, 7), Either.forLeft(": String")),
        )
      }
      checkInlaysRetrying(sourceText, "foo/*<# : Int #>*/ bar/*<# : String #>*/ baz")

      val intInlayBefore = managedInlayAt(3)
      val stringInlayBefore = managedInlayAt(7)

      // Second response: the ": Int" hint is identical, the ": String" hint changes to ": Long".
      serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
        listOf(
          InlayHint(Position(0, 3), Either.forLeft(": Int")),
          InlayHint(Position(0, 7), Either.forLeft(": Long")),
        )
      }
      // Append at the end (does not shift the hint offsets before it) to invalidate the cache so the pass re-requests.
      writeCommandAction(project, "") {
        codeInsightFixture.editor.document.insertString(sourceText.length, " x")
      }
      checkInlaysRetrying("foo bar baz x", "foo/*<# : Int #>*/ bar/*<# : Long #>*/ baz x")

      assertSame(intInlayBefore, managedInlayAt(3), "Unchanged inlay must be reused, not recreated")
      assertNotSame(stringInlayBefore, managedInlayAt(7), "Changed inlay must be recreated")

      serverSession.awaitExpected()
    }

    @Test
    fun `refresh re-requests hints without an edit`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val sourceText = "hello world"
      val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
        listOf(InlayHint(Position(0, 5), Either.forLeft(": A")))
      }
      checkInlaysRetrying(sourceText, "hello/*<# : A #>*/ world")

      // The server signals its hints changed even though the document didn't — expect a fresh request, no edit.
      serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
        listOf(InlayHint(Position(0, 5), Either.forLeft(": B")))
      }
      serverSession.sendRequest(serverSession.INLAY_HINT_REFRESH) { }

      checkInlaysRetrying(sourceText, "hello/*<# : B #>*/ world")
      serverSession.awaitExpected()
    }

    @Test
    fun `inlay hints follow the global switch`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val settings = InlayHintsSettings.instance()
      val sourceText = "hello world"
      val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

      serverSession.expectRequest(
        serverSession.INLAY_HINT,
        { it.textDocument.uri == serverSession.fileUri(virtualFile) },
      ) {
        listOf(InlayHint(Position(0, 5), Either.forLeft(": String")))
      }

      try {
        // The switch is on by default, so the hint shows.
        checkInlaysRetrying(sourceText, "hello/*<# : String #>*/ world")
        serverSession.awaitExpected()

        // The switch goes off: the hint goes away without an edit.
        settings.setEnabledGlobally(false)
        checkInlaysRetrying(sourceText, "hello world")

        // The switch goes on again: the cached hint comes back.
        settings.setEnabledGlobally(true)
        checkInlaysRetrying(sourceText, "hello/*<# : String #>*/ world")
      }
      finally {
        settings.setEnabledGlobally(true)
      }
    }

    @Test
    fun `existing location navigates without a resolve request`(): Unit = timeoutRunBlocking {
      checkExistingLocation(resolveSupported = true)
    }

    @Test
    fun `existing location works without resolve support`(): Unit = timeoutRunBlocking {
      checkExistingLocation(resolveSupported = false)
    }

    private suspend fun checkExistingLocation(resolveSupported: Boolean) {
      supportsResolve = resolveSupported
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

      val sourceText = "hello world"
      val virtualFile = codeInsightFixture.configureByText("test.txt", sourceText).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

      serverSession.expectRequest(
        serverSession.INLAY_HINT,
        { it.textDocument.uri == serverSession.fileUri(virtualFile) },
      ) {
        listOf(InlayHint(Position(0, 5), Either.forRight(mutableListOf(
          InlayHintLabelPart("clickable").apply {
            location = Location(serverSession.fileUri(virtualFile), Range(Position(0, 6), Position(0, 6)))
          },
        ))).apply { setTooltip("Existing tooltip") })
      }

      assertNoResolveRequest(serverSession) {
        checkInlaysRetrying(sourceText, "hello/*<# [clickable] #>*/ world")
        interactWithLabel(0)
        checkNavigation(6)
      }
    }

    @Test
    fun `string label hover starts one cancellable tooltip task`(): Unit = timeoutRunBlocking {
      val hint = InlayHint(Position(0, 5), Either.forLeft(": String")).apply { setTooltip("String tooltip") }
      checkTooltipHover(hint, 0, "hello/*<# : String #>*/ world", "stringTooltip.txt")
    }

    @Test
    fun `part hover starts one cancellable tooltip task`(): Unit = timeoutRunBlocking {
      val hint = inlayHint("first", "second")
      hint.label.right[1].setTooltip(MarkupContent(MarkupKind.MARKDOWN, "**Second part**"))
      checkTooltipHover(hint, 1, "hello/*<# [first second] #>*/ world", "partTooltip.txt")
    }

    @Test
    fun `ellipsis hover starts tooltip work without resolving`(): Unit = timeoutRunBlocking {
      val hint = inlayHint("shown", "x".repeat(100)).apply { setTooltip("Whole hint") }
      checkTooltipHover(hint, 1, "hello/*<# [shown ...] #>*/ world", "ellipsisTooltip.txt")
    }

    private suspend fun checkTooltipHover(hint: InlayHint, partIndex: Int, expected: String, fileName: String) {
      val serverSession = inlayHintSession(hint, fileName)
      checkInlaysRetrying("hello world", expected)
      awaitLspJobs()

      assertNoResolveRequest(serverSession) {
        withContext(Dispatchers.EDT) {
          interactWithLabel(partIndex, modifiers = 0, click = false, exit = false)
          val scopeJob = LspCoroutineScopeService.getInstance(project).cs.coroutineContext.job
          val tooltipJob = scopeJob.children.single()
          assertTrue(tooltipJob.isActive)

          interactWithLabel(partIndex, modifiers = 0, click = false, exit = false)
          assertSame(tooltipJob, scopeJob.children.single(), "Moving within the part must reuse the pending task")

          (managedInlayAt(5).renderer as PresentationRenderer).presentation.mouseExited()
          assertTrue(tooltipJob.isCancelled, "Leaving the hint must cancel pending tooltip work")
        }
        interactWithLabel(partIndex, modifiers = 0, click = false, exit = false)
        awaitLspJobs()
        withContext(Dispatchers.EDT) { (managedInlayAt(5).renderer as PresentationRenderer).presentation.mouseExited() }
      }
    }

    @Test
    fun `removing an inlay cancels pending tooltip work`(): Unit = timeoutRunBlocking {
      val hint = InlayHint(Position(0, 5), Either.forLeft(": String")).apply { setTooltip("String tooltip") }
      val serverSession = inlayHintSession(hint, "removedTooltip.txt")
      checkInlaysRetrying("hello world", "hello/*<# : String #>*/ world")
      awaitLspJobs()

      assertNoResolveRequest(serverSession) {
        withContext(Dispatchers.EDT) {
          interactWithLabel(0, modifiers = 0, click = false, exit = false)
          val tooltipJob = LspCoroutineScopeService.getInstance(project).cs.coroutineContext.job.children.single()

          Disposer.dispose(managedInlayAt(5))

          assertTrue(tooltipJob.isCancelled, "Removing the inlay must cancel pending tooltip work")
          assertTrue(managedInlaysAt(5).isEmpty())
        }
      }
    }

    @Test
    fun `part hover uses the whole tooltip without resolving`(): Unit = timeoutRunBlocking {
      val hint = inlayHint("first", "second").apply { setTooltip("Whole hint") }
      hint.label.right[0].setTooltip("First part")
      checkTooltipHover(hint, 1, "hello/*<# [first second] #>*/ world", "fallbackTooltip.txt")
    }

    @Test
    fun `whitespace-only tooltips do not trigger resolve`(): Unit = timeoutRunBlocking {
      val hint = inlayHint("a", "b", "c").apply { setTooltip("Whole hint") }
      val whitespace = " \t\n"
      hint.label.right[0].setTooltip(whitespace)
      hint.label.right[1].setTooltip(MarkupContent(MarkupKind.PLAINTEXT, whitespace))
      hint.label.right[2].setTooltip(MarkupContent(MarkupKind.MARKDOWN, whitespace))
      val serverSession = inlayHintSession(hint, "blankPartTooltips.txt")
      checkInlaysRetrying("hello world", "hello/*<# [a b c] #>*/ world")

      assertNoResolveRequest(serverSession) {
        for (partIndex in hint.label.right.indices) {
          interactWithLabel(partIndex, modifiers = 0, click = false, exit = false)
          awaitLspJobs()
        }
        withContext(Dispatchers.EDT) { (managedInlayAt(5).renderer as PresentationRenderer).presentation.mouseExited() }
      }
    }

    @Test
    fun `string label hover resolves a missing tooltip`(): Unit = timeoutRunBlocking {
      val hint = InlayHint(Position(0, 5), Either.forLeft(": String")).apply { data = "string" }
      checkHoverResolve(hint, 0, "hello/*<# : String #>*/ world", "resolvedStringTooltip.txt")
    }

    @Test
    fun `part hover resolves despite another part having a tooltip`(): Unit = timeoutRunBlocking {
      val hint = inlayHint("first", "second", data = "parts")
      hint.label.right[0].setTooltip("First part")
      checkHoverResolve(hint, 1, "hello/*<# [first second] #>*/ world", "resolvedPartTooltip.txt")
    }

    @Test
    fun `ellipsis hover resolves a missing whole tooltip`(): Unit = timeoutRunBlocking {
      val hint = inlayHint("shown", "x".repeat(100), data = "ellipsis")
      hint.label.right[1].setTooltip("Hidden part")
      checkHoverResolve(hint, 1, "hello/*<# [shown ...] #>*/ world", "resolvedEllipsisTooltip.txt")
    }

    private suspend fun checkHoverResolve(hint: InlayHint, partIndex: Int, expected: String, fileName: String) {
      val serverSession = inlayHintSession(hint, fileName)
      checkInlaysRetrying("hello world", expected)
      val capabilities = serverSession.receivedRequests(serverSession.INITIALIZE).single().capabilities
      assertEquals(setOf("label.location", "tooltip", "label.tooltip"),
                   capabilities.textDocument.inlayHint.resolveSupport.properties.toSet())
      val resolve = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE, { it.position == hint.position }) {
        assertEquals(hint.label, it.label)
        assertEquals("\"${hint.data}\"", it.data.toString())
        it.apply { setTooltip(MarkupContent(MarkupKind.MARKDOWN, "**Resolved tooltip**")) }
      }

      interactWithLabel(partIndex, modifiers = 0, click = false, exit = false)
      resolve.await()
      awaitLspJobs()

      assertNoResolveRequest(serverSession) {
        interactWithLabel(partIndex, modifiers = 0, click = false)
        interactWithLabel(partIndex, modifiers = 0, click = false)
      }
      withContext(Dispatchers.EDT) { assertEquals(0, codeInsightFixture.editor.caretModel.offset) }
    }

    @Test
    fun `hover and navigation share a pending resolve across parts`(): Unit = timeoutRunBlocking {
      checkSharedResolve(hoverFirst = true)
    }

    @Test
    fun `hover reuses a resolve started by navigation`(): Unit = timeoutRunBlocking {
      checkSharedResolve(hoverFirst = false)
    }

    @Test
    fun `existing location navigates while tooltip resolve is pending`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
      val virtualFile = withContext(Dispatchers.EDT) {
        codeInsightFixture.configureByText("locationWhileTooltipResolves.txt", "hello world").virtualFile
      }
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
        listOf(inlayHint("target").apply {
          label.right.single().location = Location(fileUri, Range(Position(0, 6), Position(0, 6)))
        })
      }
      checkInlaysRetrying("hello world", "hello/*<# [target] #>*/ world")
      val answer = CompletableDeferred<Unit>()
      val resolve = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) {
        answer.await()
        it.apply { setTooltip("Resolved tooltip") }
      }

      try {
        interactWithLabel(0, modifiers = 0, click = false, exit = false)
        resolve.await()
        interactWithLabel(0)
        checkNavigation(6)
        assertEquals(1, serverSession.receivedRequests(serverSession.INLAY_HINT_RESOLVE).size)
      }
      finally {
        answer.complete(Unit)
      }
      awaitLspJobs()
    }

    private suspend fun checkSharedResolve(hoverFirst: Boolean) {
      val hint = inlayHint("first", "second")
      hint.label.right[1].setTooltip("Existing tooltip")
      val serverSession = inlayHintSession(hint, "sharedResolve$hoverFirst.txt")
      checkInlaysRetrying("hello world", "hello/*<# [first second] #>*/ world")
      val fileUri = serverSession.fileUri(codeInsightFixture.file.virtualFile)
      val answer = CompletableDeferred<Unit>()
      val resolve = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { hint ->
        answer.await()
        hint.apply {
          label.right[0].setTooltip("First part")
          label.right[1].setTooltip(MarkupContent(MarkupKind.MARKDOWN, "**Second part**"))
          label.right[1].location = Location(fileUri, Range(Position(0, 8), Position(0, 8)))
        }
      }

      try {
        if (hoverFirst) interactWithLabel(0, modifiers = 0, click = false, exit = false)
        else interactWithLabel(1)
        resolve.await()

        assertNoResolveRequest(serverSession) {
          interactWithLabel(if (hoverFirst) 1 else 0, modifiers = 0, click = false, exit = false)
          if (hoverFirst) interactWithLabel(1)
          else withContext(Dispatchers.EDT) { (managedInlayAt(5).renderer as PresentationRenderer).presentation.mouseExited() }
          answer.complete(Unit)
          checkNavigation(8)
        }
        assertEquals(1, serverSession.receivedRequests(serverSession.INLAY_HINT_RESOLVE).size)
      }
      finally {
        answer.complete(Unit)
      }
    }

    @Test
    fun `leaving hover still caches a successful resolve without tooltips`(): Unit = timeoutRunBlocking {
      val serverSession = inlayHintSession(inlayHint("first", "second"), "resolvedWithoutTooltip.txt")
      checkInlaysRetrying("hello world", "hello/*<# [first second] #>*/ world")
      val answer = CompletableDeferred<Unit>()
      val resolve = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) {
        answer.await()
        it
      }
      try {
        interactWithLabel(0, modifiers = 0, click = false, exit = false)
        resolve.await()
        withContext(Dispatchers.EDT) { (managedInlayAt(5).renderer as PresentationRenderer).presentation.mouseExited() }
        answer.complete(Unit)
        awaitLspJobs()

        assertNoResolveRequest(serverSession) {
          interactWithLabel(0, modifiers = 0, click = false, exit = false)
          awaitLspJobs()
          interactWithLabel(1, modifiers = 0, click = false, exit = false)
          awaitLspJobs()
          interactWithLabel(1)
        }
        withContext(Dispatchers.EDT) { assertEquals(0, codeInsightFixture.editor.caretModel.offset) }
      }
      finally {
        answer.complete(Unit)
      }
    }

    @Test
    fun `fresh hints cancel the old resolve and use the new data`(): Unit = timeoutRunBlocking {
      val serverSession = inlayHintSession(inlayHint("target", data = "old"), "refreshPendingTooltip.txt")
      checkInlaysRetrying("hello world", "hello/*<# [target] #>*/ world")
      val inlay = withContext(Dispatchers.EDT) { managedInlayAt(5) }
      val pending = UnansweredRequest()
      val resolve = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { pending.hold() }
      interactWithLabel(0, modifiers = 0, click = false, exit = false)
      resolve.await()
      val fileUri = serverSession.fileUri(codeInsightFixture.file.virtualFile)
      val freshHints = serverSession.expectRequest(serverSession.INLAY_HINT, { it.textDocument.uri == fileUri }) {
        listOf(inlayHint("target", data = "new"))
      }
      serverSession.sendRequest(serverSession.INLAY_HINT_REFRESH) { }
      freshHints.await()
      awaitLspJobs()
      waitUntilAssertSucceeds { assertTrue(pending.isCancelled, "A fresh hint must cancel the old resolve") }
      withContext(Dispatchers.EDT) { assertSame(inlay, managedInlayAt(5)) }

      val newResolve = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { hint ->
        assertEquals("\"new\"", hint.data.toString())
        hint.apply {
          setTooltip("Fresh tooltip")
          label.right.single().location = Location(fileUri, Range(Position(0, 7), Position(0, 7)))
        }
      }
      interactWithLabel(0, modifiers = 0, click = false, exit = false)
      newResolve.await()
      awaitLspJobs()
      assertNoResolveRequest(serverSession) {
        interactWithLabel(0)
        checkNavigation(7)
      }
    }

    @Test
    fun `failed hover resolve can be retried`(): Unit = timeoutRunBlocking {
      val serverSession = inlayHintSession(fileName = "retryTooltip.txt")
      checkInlaysRetrying("hello world", "hello/*<# [target] #>*/ world")
      val failure = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) {
        throw ResponseErrorException(ResponseError(ResponseErrorCode.InternalError, "Resolve failed", null))
      }
      interactWithLabel(0, modifiers = 0, click = false, exit = false)
      failure.await()
      awaitLspJobs()

      val fileUri = serverSession.fileUri(codeInsightFixture.file.virtualFile)
      val retry = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { hint ->
        hint.apply {
          setTooltip("Resolved tooltip")
          label.right.single().location = Location(fileUri, Range(Position(0, 6), Position(0, 6)))
        }
      }
      interactWithLabel(0, modifiers = 0, click = false, exit = false)
      retry.await()
      awaitLspJobs()
      assertNoResolveRequest(serverSession) {
        interactWithLabel(0)
        checkNavigation(6)
      }
    }

    @Test
    fun `control click resolves the selected label part`(): Unit = timeoutRunBlocking {
      checkNavigationGesture(MouseEvent.BUTTON1)
    }

    @Test
    fun `middle click resolves the selected label part`(): Unit = timeoutRunBlocking {
      checkNavigationGesture(MouseEvent.BUTTON2)
    }

    private suspend fun checkNavigationGesture(button: Int) {
      val serverSession = inlayHintSession(inlayHint("first", "second").apply { setTooltip("Existing tooltip") })
      assertNoResolveRequest(serverSession) {
        checkInlaysRetrying("hello world", "hello/*<# [first second] #>*/ world")
        interactWithLabel(1, click = false)
        interactWithLabel(1, modifiers = 0)
      }

      val fileUri = serverSession.fileUri(codeInsightFixture.file.virtualFile)
      val answer = CompletableDeferred<Unit>()
      val request = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { hint ->
        answer.await()
        assertEquals(Position(0, 5), hint.position)
        assertEquals(listOf("first", "second"), hint.label.right.map { it.value })
        InlayHint(hint.position, Either.forRight(listOf(
          InlayHintLabelPart("first").apply { location = Location(fileUri, Range(Position(0, 2), Position(0, 2))) },
          InlayHintLabelPart("second").apply { location = Location(fileUri, Range(Position(0, 8), Position(0, 8))) },
        )))
      }

      interactWithLabel(1, button = button)
      request.await()
      withContext(Dispatchers.EDT) {
        assertEquals(0, codeInsightFixture.editor.caretModel.offset)
      }
      answer.complete(Unit)
      checkNavigation(8)

      assertNoResolveRequest(serverSession) {
        interactWithLabel(0, button = button)
        checkNavigation(2)
      }
    }

    @Test
    fun `refreshed hint resolves a moved target in another file`(): Unit = timeoutRunBlocking {
      val targetFile = withContext(Dispatchers.EDT) {
        codeInsightFixture.addFileToProject("target.txt", "before\ntarget\n").virtualFile
      }
      val serverSession = inlayHintSession(inlayHint("target", data = mapOf("version" to 0)))
      checkInlaysRetrying("hello world", "hello/*<# [target] #>*/ world")
      val sourceUri = serverSession.fileUri(codeInsightFixture.file.virtualFile)
      val targetUri = serverSession.fileUri(targetFile)
      serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { hint ->
        hint.apply { label.right.single().location = Location(targetUri, Range(Position(1, 0), Position(1, 0))) }
      }
      interactWithLabel(0)
      checkNavigation(7, targetFile)
      awaitLspJobs()
      val targetDocument = withContext(Dispatchers.EDT) {
        FileEditorManager.getInstance(project).selectedTextEditor!!.document
      }

      for (version in 1..2) {
        val targetHints = serverSession.expectRequest(
          serverSession.INLAY_HINT,
          { it.textDocument.uri == targetUri },
        ) { emptyList() }
        writeCommandAction(project, "") { targetDocument.insertString(0, "\n") }
        val freshHints = serverSession.expectRequest(
          serverSession.INLAY_HINT,
          { it.textDocument.uri == sourceUri },
        ) {
          listOf(InlayHint(Position(0, 5), Either.forRight(listOf(InlayHintLabelPart("target")))).apply {
            data = mapOf("version" to version)
          })
        }
        assertNoResolveRequest(serverSession) {
          serverSession.sendRequest(serverSession.INLAY_HINT_REFRESH) { }
          freshHints.await()
          targetHints.await()
        }

        val resolve = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { hint ->
          assertEquals("""{"version":$version}""", hint.data?.toString())
          hint.apply {
            label.right.single().location = Location(targetUri, Range(Position(version + 1, 0), Position(version + 1, 0)))
          }
        }
        interactWithLabel(0)
        awaitLspJobs()
        assertTrue(resolve.isCompleted, "The click should resolve the hint from the fresh response")
        checkNavigation(7 + version, targetFile)
      }
    }

    @Test
    fun `reused unresolved hint resolves the fresh response`(): Unit = timeoutRunBlocking {
      val serverSession = inlayHintSession(inlayHint("target", data = mapOf("version" to 0)))
      checkInlaysRetrying("hello world", "hello/*<# [target] #>*/ world")
      val fileUri = serverSession.fileUri(codeInsightFixture.file.virtualFile)
      val inlay = withContext(Dispatchers.EDT) { managedInlayAt(5) }
      for (version in 1..2) {
        val freshHints = serverSession.expectRequest(
          serverSession.INLAY_HINT,
          { it.textDocument.uri == fileUri },
        ) {
          listOf(InlayHint(Position(0, 5), Either.forRight(listOf(InlayHintLabelPart("target")))).apply {
            data = mapOf("version" to version)
          })
        }
        assertNoResolveRequest(serverSession) {
          serverSession.sendRequest(serverSession.INLAY_HINT_REFRESH) { }
          freshHints.await()
        }
        withContext(Dispatchers.EDT) {
          assertSame(inlay, managedInlayAt(5), "The editor should reuse the inlay after each refresh")
        }
      }

      val resolve = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { hint ->
        assertEquals("""{"version":2}""", hint.data?.toString())
        hint.apply { label.right.single().location = Location(fileUri, Range(Position(0, 6), Position(0, 6))) }
      }
      interactWithLabel(0)
      awaitLspJobs()
      assertTrue(resolve.isCompleted, "The click should resolve the hint from the latest response")
      checkNavigation(6)
    }

    @Test
    fun `reused hint resolves at its updated position after an edit`(): Unit = timeoutRunBlocking {
      val serverSession = inlayHintSession()
      checkInlaysRetrying("hello world", "hello/*<# [target] #>*/ world")
      val fileUri = serverSession.fileUri(codeInsightFixture.file.virtualFile)
      val inlay = withContext(Dispatchers.EDT) { managedInlayAt(5) }
      val freshHints = serverSession.expectRequest(
        serverSession.INLAY_HINT,
        { it.textDocument.uri == fileUri },
      ) {
        listOf(InlayHint(Position(0, 6), Either.forRight(listOf(InlayHintLabelPart("target")))))
      }
      writeCommandAction(project, "") { codeInsightFixture.editor.document.insertString(0, "x") }
      checkInlaysRetrying("xhello world", "xhello/*<# [target] #>*/ world")
      freshHints.await()
      awaitLspJobs()
      withContext(Dispatchers.EDT) {
        assertSame(inlay, managedInlayAt(6))
      }

      val resolve = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { hint ->
        assertEquals(Position(0, 6), hint.position)
        hint.apply { label.right.single().location = Location(fileUri, Range(Position(0, 7), Position(0, 7))) }
      }
      interactWithLabel(0, inlayOffset = 6)
      awaitLspJobs()
      assertTrue(resolve.isCompleted, "The resolve request should use the position from the fresh response")
      checkNavigation(7)
    }

    @Test
    fun `editing a file keeps cached navigation until fresh hints arrive`(): Unit = timeoutRunBlocking {
      checkNavigationBeforeFreshHints(HintChange.EDIT)
    }

    @Test
    fun `server refresh keeps cached navigation until fresh hints arrive`(): Unit = timeoutRunBlocking {
      checkNavigationBeforeFreshHints(HintChange.SERVER_REFRESH)
    }

    private suspend fun checkNavigationBeforeFreshHints(change: HintChange) {
      val serverSession = inlayHintSession()
      checkInlaysRetrying("hello world", "hello/*<# [target] #>*/ world")
      val file = codeInsightFixture.file.virtualFile
      val fileUri = serverSession.fileUri(file)
      serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { hint ->
        hint.apply { label.right.single().location = Location(fileUri, Range(Position(0, 6), Position(0, 6))) }
      }
      interactWithLabel(0)
      checkNavigation(6)
      awaitLspJobs()
      withContext(Dispatchers.EDT) { codeInsightFixture.editor.caretModel.moveToOffset(0) }

      val answer = CompletableDeferred<Unit>()
      val freshRequest = serverSession.expectRequest(
        serverSession.INLAY_HINT,
        { it.textDocument.uri == fileUri },
      ) {
        answer.await()
        listOf(InlayHint(Position(0, 5), Either.forRight(listOf(InlayHintLabelPart("target")))).apply {
          data = "fresh"
        })
      }
      val extraRequest = serverSession.expectRequest(serverSession.INLAY_HINT) { emptyList() }
      try {
        assertNoResolveRequest(serverSession) {
          try {
            applyChange(serverSession, change)
            freshRequest.await()
            interactWithLabel(0)
            checkNavigation(6)
          }
          finally {
            answer.complete(Unit)
          }
        }
        assertFalse(extraRequest.isCompleted, "The click must not request another list of hints")
      }
      finally {
        extraRequest.cancel()
      }

      awaitLspJobs()
      serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { hint ->
        assertEquals("\"fresh\"", hint.data?.toString())
        hint.apply { label.right.single().location = Location(fileUri, Range(Position(0, 7), Position(0, 7))) }
      }
      interactWithLabel(0)
      checkNavigation(7)
    }

    @Test
    fun `successful resolve without a location is cached`(): Unit = timeoutRunBlocking {
      checkMissingResolvedLocation(keepSelectedPart = true)
    }

    @Test
    fun `fresh hint clears a cached resolve without a location`(): Unit = timeoutRunBlocking {
      val serverSession = inlayHintSession(fileName = "resolveAfterMissingLocation.txt")
      checkInlaysRetrying("hello world", "hello/*<# [target] #>*/ world")
      val fileUri = serverSession.fileUri(codeInsightFixture.file.virtualFile)
      val inlay = withContext(Dispatchers.EDT) { managedInlayAt(5) }
      val initialResolve = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { it }

      interactWithLabel(0)
      initialResolve.await()
      awaitLspJobs()

      val freshHints = serverSession.expectRequest(
        serverSession.INLAY_HINT,
        { it.textDocument.uri == fileUri },
      ) {
        listOf(inlayHint("target", data = "fresh"))
      }
      assertNoResolveRequest(serverSession) {
        interactWithLabel(0)
        awaitLspJobs()
        serverSession.sendRequest(serverSession.INLAY_HINT_REFRESH) { }
        freshHints.await()
      }
      withContext(Dispatchers.EDT) {
        assertSame(inlay, managedInlayAt(5), "The fresh hint should reuse the existing inlay")
        assertEquals(0, codeInsightFixture.editor.caretModel.offset)
      }

      val resolve = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { hint ->
        assertEquals("\"fresh\"", hint.data?.toString())
        hint.apply { label.right.single().location = Location(fileUri, Range(Position(0, 6), Position(0, 6))) }
      }
      try {
        interactWithLabel(0)
        awaitLspJobs()
        assertTrue(resolve.isCompleted, "The fresh hint must resolve despite the previous result without a location")
        checkNavigation(6)
      }
      finally {
        resolve.cancel()
      }
    }

    @Test
    fun `resolved hint without the selected part does not navigate`(): Unit = timeoutRunBlocking {
      checkMissingResolvedLocation(keepSelectedPart = false)
    }

    private suspend fun checkMissingResolvedLocation(keepSelectedPart: Boolean) {
      val serverSession = inlayHintSession(inlayHint("first", "second"))
      checkInlaysRetrying("hello world", "hello/*<# [first second] #>*/ world")
      val inlay = withContext(Dispatchers.EDT) { managedInlayAt(5) }
      val resolve = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { hint ->
        val parts = if (keepSelectedPart) hint.label.right else listOf(hint.label.right.first())
        InlayHint(hint.position, Either.forRight(parts))
      }

      interactWithLabel(1)
      resolve.await()
      awaitLspJobs()
      assertNoResolveRequest(serverSession) { interactWithLabel(1) }
      withContext(Dispatchers.EDT) {
        assertSame(inlay, managedInlayAt(5))
        assertEquals("[first second]", inlay.renderer.toString())
        assertEquals(0, codeInsightFixture.editor.caretModel.offset)
      }
    }

    @Test
    fun `failed resolve leaves the hint visible and allows another click`(): Unit = timeoutRunBlocking {
      val serverSession = inlayHintSession()
      checkInlaysRetrying("hello world", "hello/*<# [target] #>*/ world")
      val inlay = withContext(Dispatchers.EDT) { managedInlayAt(5) }
      val failedResolve = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) {
        throw ResponseErrorException(ResponseError(ResponseErrorCode.InternalError, "Resolve failed", null))
      }

      interactWithLabel(0)
      failedResolve.await()
      awaitLspJobs()
      withContext(Dispatchers.EDT) {
        assertSame(inlay, managedInlayAt(5))
        assertEquals("[target]", inlay.renderer.toString())
        assertEquals(0, codeInsightFixture.editor.caretModel.offset)
      }

      val fileUri = serverSession.fileUri(codeInsightFixture.file.virtualFile)
      serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { hint ->
        InlayHint(hint.position, Either.forRight(listOf(InlayHintLabelPart("target").apply {
          location = Location(fileUri, Range(Position(0, 6), Position(0, 6)))
        })))
      }
      interactWithLabel(0)
      checkNavigation(6)
    }

    @Test
    fun `label without a location does not resolve when unsupported`(): Unit = timeoutRunBlocking {
      supportsResolve = false
      val serverSession = inlayHintSession()
      assertNoResolveRequest(serverSession) {
        checkInlaysRetrying("hello world", "hello/*<# [target] #>*/ world")
        interactWithLabel(0)
        interactWithLabel(0, button = MouseEvent.BUTTON2)
      }
    }

    @Test
    fun `string label does not resolve on click`(): Unit = timeoutRunBlocking {
      val hint = InlayHint(Position(0, 5), Either.forLeft(": String")).apply { setTooltip("Existing tooltip") }
      val serverSession = inlayHintSession(hint)
      assertNoResolveRequest(serverSession) {
        checkInlaysRetrying("hello world", "hello/*<# : String #>*/ world")
        interactWithLabel(0)
        interactWithLabel(0, button = MouseEvent.BUTTON2)
      }
    }

    @Test
    fun `truncation ellipsis does not resolve on click`(): Unit = timeoutRunBlocking {
      val serverSession = inlayHintSession(inlayHint("shown", "x".repeat(100)).apply { setTooltip("Existing tooltip") })
      assertNoResolveRequest(serverSession) {
        checkInlaysRetrying("hello world", "hello/*<# [shown ...] #>*/ world")
        interactWithLabel(1)
        interactWithLabel(1, button = MouseEvent.BUTTON2)
      }
    }

    private suspend fun inlayHintSession(hint: InlayHint = inlayHint("target"), fileName: String = "test.txt"): FakeLspServerSession {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
      val virtualFile = withContext(Dispatchers.EDT) {
        codeInsightFixture.configureByText(fileName, "hello world").virtualFile
      }
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectRequest(
        serverSession.INLAY_HINT,
        { it.textDocument.uri == serverSession.fileUri(virtualFile) },
      ) {
        listOf(hint)
      }
      return serverSession
    }

    private suspend fun applyChange(serverSession: FakeLspServerSession, change: HintChange) {
      when (change) {
        HintChange.EDIT -> {
          writeCommandAction(project, "") { codeInsightFixture.editor.document.insertString(11, "!") }
          // The highlighting pass after the edit requests fresh hints.
          checkInlaysRetrying("hello world!", "hello/*<# [target] #>*/ world!")
        }
        HintChange.SERVER_REFRESH -> serverSession.sendRequest(serverSession.INLAY_HINT_REFRESH) { }
      }
    }
  }

  @Nested
  inner class DynamicRegistration {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

    @Test
    fun `dynamic registration enables resolve for an existing hint`(): Unit = timeoutRunBlocking {
      (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
      val virtualFile = withContext(Dispatchers.EDT) {
        codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
      }
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectRequest(
        serverSession.INLAY_HINT,
        { it.textDocument.uri == serverSession.fileUri(virtualFile) },
      ) {
        listOf(inlayHint("target"))
      }
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("inlay-hint-resolve", "textDocument/inlayHint", InlayHintRegistrationOptions().apply {
          resolveProvider = false
        })))
      }
      assertNoResolveRequest(serverSession) {
        checkInlaysRetrying("hello world", "hello/*<# [target] #>*/ world")
        interactWithLabel(0)
      }
      serverSession.sendRequest(serverSession.UNREGISTER_CAPABILITY) {
        UnregistrationParams(listOf(Unregistration("inlay-hint-resolve", "textDocument/inlayHint")))
      }
      val fileUri = serverSession.fileUri(codeInsightFixture.file.virtualFile)
      val refresh = serverSession.expectRequest(
        serverSession.INLAY_HINT,
        { it.textDocument.uri == fileUri },
      ) {
        listOf(inlayHint("target"))
      }
      serverSession.sendRequest(serverSession.REGISTER_CAPABILITY) {
        RegistrationParams(listOf(Registration("inlay-hint-resolve", "textDocument/inlayHint", InlayHintRegistrationOptions().apply {
          resolveProvider = true
        })))
      }
      refresh.await()
      awaitLspJobs()

      val resolve = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { hint ->
        hint.apply { label.right.single().location = Location(fileUri, Range(Position(0, 6), Position(0, 6))) }
      }
      try {
        interactWithLabel(0)
        awaitLspJobs()
        assertTrue(resolve.isCompleted, "The existing hint must use the new resolve capability")
        checkNavigation(6)
      }
      finally {
        resolve.cancel()
      }
    }
  }

  private fun inlayHint(vararg labelParts: String, data: Any? = null): InlayHint =
    InlayHint(Position(0, 5), Either.forRight(labelParts.map { InlayHintLabelPart(it) })).apply { this.data = data }

  private suspend fun interactWithLabel(
    partIndex: Int,
    button: Int = MouseEvent.BUTTON1,
    modifiers: Int = when {
      button == MouseEvent.BUTTON2 -> 0
      SystemInfoRt.isMac -> InputEvent.META_DOWN_MASK
      else -> InputEvent.CTRL_DOWN_MASK
    },
    click: Boolean = true,
    inlayOffset: Int = 5,
    exit: Boolean = true,
  ) {
    withContext(Dispatchers.EDT) {
      val inlay = managedInlayAt(inlayOffset)
      val presentation = (inlay.renderer as PresentationRenderer).presentation
      val point = labelPoint(presentation, partIndex)
      val bounds = checkNotNull(inlay.bounds)
      val component = codeInsightFixture.editor.contentComponent
      val x = bounds.x + point.x
      val y = bounds.y + point.y
      presentation.mouseMoved(MouseEvent(component, MouseEvent.MOUSE_MOVED, 0, modifiers, x, y, 0, false), point)
      if (click) {
        val event = MouseEvent(component, MouseEvent.MOUSE_CLICKED, 0, modifiers, x, y, 1, false, button)
        presentation.mouseClicked(event, point)
      }
      if (exit) presentation.mouseExited()
    }
  }

  private fun labelPoint(root: InlayPresentation, partIndex: Int): Point {
    var presentation = root
    val point = Point()
    while (presentation is StaticDelegatePresentation) {
      when (presentation) {
        is InsetPresentation -> point.translate(presentation.left, presentation.top)
        is DynamicInsetPresentation -> point.translate(presentation.valueProvider.left, presentation.valueProvider.top)
      }
      presentation = presentation.presentation
    }
    if (presentation is SequencePresentation) {
      point.translate(presentation.presentations.take(partIndex).sumOf { it.width } + presentation.presentations[partIndex].width / 2,
                      presentation.height / 2)
    }
    else {
      point.translate(presentation.width / 2, presentation.height / 2)
    }
    return point
  }

  private suspend fun checkNavigation(offset: Int, targetFile: VirtualFile = codeInsightFixture.file.virtualFile) {
    waitUntilAssertSucceeds(message = "The click should navigate to the label part's location") {
      withContext(Dispatchers.EDT) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        assertEquals(targetFile.url, editor?.virtualFile?.url)
        assertEquals(offset, editor?.caretModel?.offset)
      }
    }
  }

  /**
   * Checks for unexpected resolve requests after [action] and its asynchronous work finish.
   * Await required requests individually; this helper cancels its temporary expectation.
   */
  private suspend fun assertNoResolveRequest(serverSession: FakeLspServerSession, action: suspend () -> Unit) {
    val request = serverSession.expectRequest(serverSession.INLAY_HINT_RESOLVE) { it }
    try {
      action()
      awaitLspJobs()
      project.awaitPendingEditorOpen()
      assertFalse(request.isCompleted, "No resolve request should be sent")
    }
    finally {
      request.cancel()
    }
  }

  private suspend fun awaitLspJobs() {
    val scopeJob = LspCoroutineScopeService.getInstance(project).cs.coroutineContext.job
    while (true) {
      val jobs = scopeJob.children.toList()
      if (jobs.isEmpty()) return
      jobs.joinAll()
    }
  }

  private suspend fun checkInlaysRetrying(sourceText: String, expected: String) {
    waitUntilAssertSucceeds(message = "Inlays don't match expected") {
      codeInsightFixture.doHighlighting()
      assertEquals(expected.trim(), dumpInlays(sourceText).trim())
    }
  }

  private fun managedInlayAt(offset: Int): Inlay<*> =
    codeInsightFixture.editor.inlayModel
      .getInlineElementsInRange(offset, offset, PresentationRenderer::class.java)
      .single()

  @Suppress("SameParameterValue")
  private fun managedInlaysAt(offset: Int): List<Inlay<*>> =
    codeInsightFixture.editor.inlayModel
      .getInlineElementsInRange(offset, offset, PresentationRenderer::class.java)

  private fun dumpInlays(sourceText: String): String {
    return InlayDumpUtil.dumpInlays(
      sourceText,
      codeInsightFixture.editor,
      filter = null,
      renderer = { renderer: com.intellij.openapi.editor.EditorCustomElementRenderer, _: Inlay<*> -> renderer.toString() }
    )
  }
}

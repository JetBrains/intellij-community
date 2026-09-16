package com.intellij.platform.lsp

import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.common.FakeLspServerSupportProvider
import com.intellij.platform.lsp.common.ServerSession
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.impl.features.highlighting.LspHighlightingApplier
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/**
 * Covers the reactive apply path of [LspHighlightingApplier]: no daemon runs here,
 * so the markup changes only through [LspHighlightingApplier.scheduleHighlightingRefresh]
 * and its debounced variant.
 */
@TestApplication
internal class LspHighlightingApplierTest {
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
    configureServerCapabilities = {
      semanticTokensProvider = SemanticTokensWithRegistrationOptions().apply {
        full = Either.forLeft(true)
        legend = SemanticTokensLegend(listOf("keyword"), emptyList())
      }
    },
  )

  @Test
  fun `local edit re-applies semantic token highlighters at adjusted offsets`(): Unit = timeoutRunBlocking {
    val session = openFileWithKeywordToken()
    val pendingPull = session.neverCompletingPull()
    try {
      writeCommandAction(project, "") {
        session.document.insertString(0, "xx")
      }
      waitUntilAssertSucceeds(message = "the token must re-apply at the edit-adjusted offsets") {
        assertEquals(listOf(TextRange(2, 7)), semanticTokenRanges(session.document))
      }
    }
    finally {
      pendingPull.cancel()
    }
  }

  @Test
  fun `edit burst coalesces into one collect`(): Unit = timeoutRunBlocking {
    val session = openFileWithKeywordToken()
    val pendingPull = session.neverCompletingPull()
    val applier = LspHighlightingApplier.getInstance(project)
    val collectCount = AtomicInteger()
    LspHighlightingApplier.editDebounceOverride = 500.milliseconds
    applier.afterCollectHook = { collectCount.incrementAndGet() }
    try {
      repeat(3) {
        writeCommandAction(project, "") {
          session.document.insertString(0, "x")
        }
      }
      waitUntilAssertSucceeds(message = "the coalesced refresh must apply the final offsets") {
        assertEquals(listOf(TextRange(3, 8)), semanticTokenRanges(session.document))
      }
      delay(500.milliseconds) // one full extra debounce window of grace
      assertEquals(1, collectCount.get(), "the edit burst must coalesce into exactly one collect")
    }
    finally {
      LspHighlightingApplier.editDebounceOverride = null
      applier.afterCollectHook = null
      pendingPull.cancel()
    }
  }

  @Test
  fun `stale ranges collected before an edit are not applied after it`(): Unit = timeoutRunBlocking {
    val session = openFileWithKeywordToken()
    val pendingPull = session.neverCompletingPull()
    val applier = LspHighlightingApplier.getInstance(project)
    val addedRanges = ConcurrentLinkedQueue<TextRange>()
    val listenerDisposable = Disposer.newDisposable("LspHighlightingApplierTest")
    val editDone = AtomicBoolean()
    try {
      readAction {
        val markupModel = DocumentMarkupModel.forDocument(session.document, project, true) as MarkupModelEx
        markupModel.addMarkupModelListener(listenerDisposable, object : MarkupModelListener {
          override fun afterAdded(highlighter: RangeHighlighterEx) {
            if (HighlightInfo.fromRangeHighlighter(highlighter)?.severity == HighlightSeverity.TEXT_ATTRIBUTES) {
              addedRanges.add(highlighter.textRange)
            }
          }
        })
      }
      // One shot: edit between the collect and the EDT apply; the replacement refresh runs the hook again as a no-op.
      applier.afterCollectHook = hook@{
        if (!editDone.compareAndSet(false, true)) return@hook
        writeCommandAction(project, "") {
          session.document.insertString(0, "xx")
        }
      }
      applier.scheduleHighlightingRefresh(session.virtualFile)
      waitUntilAssertSucceeds(message = "the replacement refresh must apply the edit-adjusted offsets") {
        assertEquals(listOf(TextRange(2, 7)), semanticTokenRanges(session.document))
      }
      assertFalse(
        addedRanges.contains(TextRange(0, 5)),
        "ranges collected before the edit must never be applied after it: $addedRanges",
      )
    }
    finally {
      applier.afterCollectHook = null
      Disposer.dispose(listenerDisposable)
      pendingPull.cancel()
    }
  }

  private class TokenSession(
    val virtualFile: VirtualFile,
    val document: Document,
    val serverSession: ServerSession,
    val uri: String,
  )

  /**
   * Opens a file with one "keyword" token over (0,5), answers the first pull, and waits until the
   * applier brings the token to the markup.
   */
  private suspend fun CoroutineScope.openFileWithKeywordToken(): TokenSession {
    // No daemon runs in this test: instantiate the pass registrar, so it assigns LspHighlightingApplier.GROUP_ID.
    TextEditorHighlightingPassRegistrar.getInstance(project)
    val virtualFile = codeInsightFixture.configureByText("test.txt", "hello world").virtualFile
    val document = codeInsightFixture.editor.document
    val serverSession = configureServerSession(project, virtualFile)
    val uri = serverSession.fileUri(virtualFile)
    val client = LspClientManagerImpl.getInstanceImpl(project).getClients(FakeLspServerSupportProvider::class.java).first()

    serverSession.expectRequest(serverSession.SEMANTIC_TOKENS_FULL, { it.textDocument.uri == uri }) {
      SemanticTokens(listOf(0, 0, 5, 0, 0))
    }
    withContext(Dispatchers.IO) { readAction { client.getSemanticTokens(virtualFile) } }
    waitUntilAssertSucceeds(message = "the initial semantic token must reach the markup") {
      assertEquals(listOf(TextRange(0, 5)), semanticTokenRanges(document))
    }
    return TokenSession(virtualFile, document, serverSession, uri)
  }

  /** From here on, every apply must come from the pending-edit math: the next pull never completes. */
  private fun TokenSession.neverCompletingPull(): Deferred<Unit> =
    serverSession.expectRequestAsync(serverSession.SEMANTIC_TOKENS_FULL, { it.textDocument.uri == uri }) {
      CompletableFuture()
    }

  private suspend fun semanticTokenRanges(document: Document): List<TextRange> = readAction {
    val markupModel = DocumentMarkupModel.forDocument(document, project, false) ?: return@readAction emptyList()
    markupModel.allHighlighters
      .filter { HighlightInfo.fromRangeHighlighter(it)?.severity == HighlightSeverity.TEXT_ATTRIBUTES }
      .map { it.textRange }
      .sortedBy { it.startOffset }
  }
}

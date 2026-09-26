// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.unit

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspPullResult
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
internal class LspHighlightingCacheTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private suspend fun createFile(name: String, text: String): VirtualFile = withContext(Dispatchers.EDT) {
    codeInsightFixture.configureByText(name, text).virtualFile
  }

  /** A pull cache with a controllable request: each send parks on a gate until the test releases it. */
  private class TestCache(project: Project) : LspHighlightingCache<String>(project) {
    override val quiescenceDelay: Duration get() = Duration.ZERO

    val sendCalls = AtomicInteger()
    val cancellations = AtomicInteger()
    val pendingRequests = LinkedBlockingQueue<CompletableDeferred<Unit>>()

    override fun isSupportedForFile(file: VirtualFile): Boolean = true

    override suspend fun sendRequest(file: VirtualFile): LspPullResult<String> {
      sendCalls.incrementAndGet()
      val gate = CompletableDeferred<Unit>()
      pendingRequests.add(gate)
      try {
        gate.await()
      }
      catch (e: CancellationException) {
        cancellations.incrementAndGet()
        throw e
      }
      return LspPullResult.Full(listOf(Range(Position(0, 0), Position(0, 1)) to "value"))
    }

    override suspend fun onResponseReceived(file: VirtualFile) {}
  }

  private fun releasePendingRequests(cache: TestCache) {
    generateSequence { cache.pendingRequests.poll() }.forEach { it.complete(Unit) }
  }

  @Test
  fun `daemon restart without a document change keeps the running request`() = timeoutRunBlocking {
    val cache = TestCache(project)
    val otherFile = codeInsightFixture.addFileToProject("other.txt", "other").virtualFile
    val file = createFile("a.txt", "hello")

    readAction { cache.getHighlightings(file) } // the first pull
    waitUntilAssertSucceeds { assertEquals(1, cache.sendCalls.get()) }
    val runningRequest = cache.pendingRequests.poll()!!

    // A change in another file restarts the daemon; this document's stamp stays the same.
    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) {
        FileDocumentManager.getInstance().getDocument(otherFile)!!.insertString(0, "x")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }
    }

    readAction { cache.getHighlightings(file) } // the restarted daemon pass
    delay(300.milliseconds) // a wrong implementation cancels and re-sends within this window
    assertEquals(1, cache.sendCalls.get(), "the restarted pass must keep the running request, not re-send")
    assertEquals(0, cache.cancellations.get(), "the restarted pass must not cancel the running request")

    runningRequest.complete(Unit)
    waitUntilAssertSucceeds {
      assertEquals(1, readAction { cache.getHighlightings(file) }.size)
    }
    assertEquals(1, cache.sendCalls.get(), "an applied response for an unchanged document must not re-pull")
    releasePendingRequests(cache)
  }

  @Test
  fun `document change supersedes the running request`() = timeoutRunBlocking {
    val cache = TestCache(project)
    val file = createFile("a.txt", "hello")

    readAction { cache.getHighlightings(file) }
    waitUntilAssertSucceeds { assertEquals(1, cache.sendCalls.get()) }

    withContext(Dispatchers.EDT) {
      WriteCommandAction.runWriteCommandAction(project) {
        codeInsightFixture.editor.document.insertString(0, "X")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
      }
    }

    readAction { cache.getHighlightings(file) }
    waitUntilAssertSucceeds {
      assertEquals(2, cache.sendCalls.get(), "a document change must re-send")
      assertEquals(1, cache.cancellations.get(), "a document change must cancel the superseded request")
    }
    releasePendingRequests(cache)
  }
}

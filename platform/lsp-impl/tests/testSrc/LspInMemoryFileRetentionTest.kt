// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.idea.TestFor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.impl.features.highlighting.LspHighlightingApplier
import com.intellij.platform.lsp.impl.features.inlayCommon.LspInlayApplier
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.util.ref.GCWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

/**
 * The daemon also runs for the in-memory files of diff viewers and dialogs, and the LSP client gets no event when
 * such a file goes away. Per-file state must not keep the file reachable, or the dialog it came from leaks with it.
 */
@TestApplication
@TestFor(issues = ["IJPL-256855"])
internal class LspInMemoryFileRetentionTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    private const val COLLECT_TIMEOUT_MS = 10_000
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

  @Test
  fun `highlighting refresh does not retain an in-memory file`(): Unit = timeoutRunBlocking {
    // No daemon runs in this test: instantiate the pass registrar, so it assigns LspHighlightingApplier.GROUP_ID.
    TextEditorHighlightingPassRegistrar.getInstance(project)
    val applier = LspHighlightingApplier.getInstance(project)
    useInMemoryFile { file, _ ->
      // what LspHighlightingPass does for every editor
      applier.reportErrorsToWolf(file, emptyList(), applier.currentGeneration(file))
      applier.scheduleHighlightingRefresh(file)
    }.awaitCollected()
  }

  @Test
  fun `inlay refresh does not retain an in-memory file`(): Unit = timeoutRunBlocking {
    val applier = LspInlayApplier.getInstance(project)
    useInMemoryFile { file, _ ->
      applier.scheduleRefresh(file)
    }.awaitCollected()
  }

  @Test
  fun `reading the document version does not retain an in-memory file`(): Unit = timeoutRunBlocking {
    val file = codeInsightFixture.configureByText("test.txt", "hello").virtualFile
    fakeLspIntegration.serverSession(file)
    val client = fakeLspIntegration.client
    useInMemoryFile { _, document ->
      client.getDocumentVersion(document)
    }.awaitCollected()
  }

  /**
   * Passes an in-memory file and its document to [use], the way a dialog creates them,
   * and returns a watcher for both. The caller keeps no reference to either.
   */
  private suspend fun useInMemoryFile(use: (VirtualFile, Document) -> Unit): GCWatcher {
    val file = LightVirtualFile("dialog.txt", PlainTextFileType.INSTANCE, "hello world")
    val document = readAction { FileDocumentManager.getInstance().getDocument(file)!! }
    use(file, document)
    return GCWatcher.tracking(file, document)
  }

  /**
   * Waits on another thread. Blocking right here would keep the frame that resumed [useInMemoryFile] on the stack,
   * and the continuation it holds stores the file and the document in its fields.
   */
  private suspend fun GCWatcher.awaitCollected() {
    withContext(Dispatchers.IO) { ensureCollectedWithinTimeout(COLLECT_TIMEOUT_MS) }
  }
}

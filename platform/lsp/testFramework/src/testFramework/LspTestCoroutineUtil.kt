// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.testFramework

import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.ComparisonFailure

/**
 * @see com.intellij.platform.lsp.tests.waitUntilFileOpenedByLspServer
 */
suspend fun awaitFileOpenedByLspServer(project: Project, file: VirtualFile) {
  val topLevelFile = (file as? VirtualFileWindow)?.delegate ?: file
  withTimeout(DEFAULT_TEST_TIMEOUT) {
    LspClientManager.getInstance(project).eventsFlow().first { event ->
      when (event) {
        is LspClientManagerEvent.FileOpened -> event.file == topLevelFile
        is LspClientManagerEvent.ServerShutdown -> throw AssertionError("LSP server initialization failed")
        else -> false
      }
    }
  }
}

/**
 * Await the next diagnostics, up until [DEFAULT_TEST_TIMEOUT].
 * Caller may specify shorter [withTimeout] up the stack as needed,
 * but especially since [com.intellij.testFramework.common.timeoutRunBlocking] doesn't time out in debug at all,
 * here we have default limit, so that the function is not endless.

 * @see com.intellij.platform.lsp.tests.waitForDiagnosticsFromLspServer
 */
suspend fun awaitDiagnosticsFromLspServer(project: Project, file: VirtualFile) {
  val topLevelFile = (file as? VirtualFileWindow)?.delegate ?: file
  withTimeout(DEFAULT_TEST_TIMEOUT) {
    LspClientManager.getInstance(project).eventsFlow().first { event ->
      when (event) {
        is LspClientManagerEvent.DiagnosticsReceived -> event.file == topLevelFile
        is LspClientManagerEvent.ServerShutdown -> throw AssertionError("LSP server initialization failed")
        else -> false
      }
    }
  }
}

/**
 * @see com.intellij.platform.lsp.tests.checkLspHighlighting
 */
suspend fun CodeInsightTestFixture.checkHighlightingRetrying(initialCheck: Boolean = false) {
  val document = editor.document.let { (it as? DocumentWindow)?.delegate ?: it }
  val data = ExpectedHighlightingData(document, true, true, false)
  data.init() // removes <error>/<warning>/etc. markers
  checkHighlightingRetrying(data, initialCheck)
}

/**
 * LSP servers may send `textDocument/publishDiagnostics` notifications several times for the given file.
 * For example, first: zero problems; second: basic problems (which are quick to calculate);
 * and only the third notification gives all problems for the file.
 * So this function makes up to three attempts to call `fixture.collectAndCheckHighlighting`,
 * waiting for a new `diagnosticsReceived` notification from the client after each unlucky attempt.
 *
 * @see com.intellij.platform.lsp.tests.checkLspHighlightingForData
 */
suspend fun CodeInsightTestFixture.checkHighlightingRetrying(data: ExpectedHighlightingData, initialCheck: Boolean = false) {
  val fixture = this as CodeInsightTestFixtureImpl
  val topLevelFile = (file.virtualFile as? VirtualFileWindow)?.delegate ?: file.virtualFile

  var currentAttempt = if (initialCheck) -1 else 0
  val maxAttempts = 3
  var lastFailure: ComparisonFailure? = null

  // Create a buffered channel outside the loop to avoid losing events between iterations
  val diagnosticsChannel = Channel<Unit>(Channel.UNLIMITED)
  val disposable = Disposer.newDisposable()

  try {
    LspClientManager.getInstance(project).addListener(object : LspClientManagerListener {
      override fun serverStateChanged(lspClient: LspClient) {
        if (lspClient.state in arrayOf(LspServerState.ShutdownNormally, LspServerState.ShutdownUnexpectedly)) {
          diagnosticsChannel.close(AssertionError("LSP server initialization failed"))
        }
      }

      override fun diagnosticsReceived(lspClient: LspClient, file: VirtualFile) {
        if (file == topLevelFile) {
          diagnosticsChannel.trySend(Unit)
        }
      }
    }, disposable)

    while (true) {
      if (currentAttempt != -1) {
        try {
          withTimeout(DEFAULT_TEST_TIMEOUT) {
            diagnosticsChannel.receive()
          }
        }
        catch (e: TimeoutCancellationException) {
          // the last known failure is still valid because the lack of new diagnostics is also valid
          throw lastFailure ?: e
        }
      }

      try {
        fixture.collectAndCheckHighlighting(data)
        return
      }
      catch (cf: ComparisonFailure) {
        lastFailure = cf
      }

      currentAttempt++
      if (currentAttempt == maxAttempts) {
        throw lastFailure
      }
    }
  }
  finally {
    diagnosticsChannel.close()
    Disposer.dispose(disposable)
  }
}

/**
 * [LspClientManager] events emitted by [eventsFlow].
 */
private sealed interface LspClientManagerEvent {
  val lspClient: LspClient

  data class ServerShutdown(override val lspClient: LspClient) : LspClientManagerEvent
  data class FileOpened(override val lspClient: LspClient, val file: VirtualFile) : LspClientManagerEvent
  data class DiagnosticsReceived(override val lspClient: LspClient, val file: VirtualFile) : LspClientManagerEvent
}

/**
 * Subscribes to [LspClientManager] events as a [Flow].
 * Similar to [com.intellij.util.messages.impl.subscribeAsFlow] for `MessageBus`.
 *
 * TODO improve and move to production module
 *
 * @param sendEventsForExistingClients if true, immediately emits events for already-opened files
 */
private fun LspClientManager.eventsFlow(sendEventsForExistingClients: Boolean = true): Flow<LspClientManagerEvent> = callbackFlow {
  val disposable = Disposer.newDisposable()
  addListener(object : LspClientManagerListener {
    override fun serverStateChanged(lspClient: LspClient) {
      if (lspClient.state in arrayOf(LspServerState.ShutdownNormally, LspServerState.ShutdownUnexpectedly)) {
        trySend(LspClientManagerEvent.ServerShutdown(lspClient))
      }
    }

    override fun fileOpened(lspClient: LspClient, file: VirtualFile) {
      trySend(LspClientManagerEvent.FileOpened(lspClient, file))
    }

    override fun diagnosticsReceived(lspClient: LspClient, file: VirtualFile) {
      trySend(LspClientManagerEvent.DiagnosticsReceived(lspClient, file))
    }
  }, disposable, sendEventsForExistingClients)
  awaitClose { Disposer.dispose(disposable) }
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.testFramework

import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.junit.Assert
import org.junit.ComparisonFailure
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * @see awaitFileOpenedByLspServer
 */
@RequiresBlockingContext(ReplaceWith("awaitFileOpenedByLspServer(project, file)",
                                     "com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer"))
@RequiresEdt
fun waitUntilFileOpenedByLspServer(project: Project, file: VirtualFile) {
  val topLevelFile = (file as? VirtualFileWindow)?.delegate ?: file
  val disposable = Disposer.newDisposable()
  try {
    val fileOpened = AtomicBoolean()
    val serverShutdown = AtomicBoolean()
    LspClientManager.getInstance(project).addListener(object : LspClientManagerListener {
      override fun serverStateChanged(lspClient: LspClient) {
        if (lspClient.state in arrayOf(LspServerState.ShutdownNormally, LspServerState.ShutdownUnexpectedly)) {
          serverShutdown.set(true)
        }
      }

      override fun fileOpened(lspClient: LspClient, file: VirtualFile) {
        if (file == topLevelFile) fileOpened.set(true)
      }
    }, disposable, sendEventsForExistingClients = true)

    PlatformTestUtil.waitWithEventsDispatching("LSP server not initialized in 10 seconds",
                                               {
                                                 ProgressManager.checkCanceled()
                                                 fileOpened.get() || serverShutdown.get()
                                               },
                                               10)
    Assert.assertFalse("LSP server initialization failed", serverShutdown.get())
  }
  finally {
    Disposer.dispose(disposable)
  }
}

/**
 * Please note that in some cases it isn't enough to call the method once, see [doCheckExpectedHighlightingData]
 *
 * @see awaitDiagnosticsFromLspServer
 */
@RequiresBlockingContext(ReplaceWith("awaitDiagnosticsFromLspServer(project, file)",
                                     "com.intellij.platform.lsp.testFramework.awaitDiagnosticsFromLspServer"))
@JvmOverloads
@RequiresEdt
fun waitForDiagnosticsFromLspServer(project: Project, file: VirtualFile, timeout: Int = 30) {
  withDiagnosticsReceivedCounter(project, file) { diagnosticsReceivedCounter ->
    doWaitForDiagnosticsFromLspServer(diagnosticsReceivedCounter, timeout)
  }
}

@RequiresEdt
private fun doWaitForDiagnosticsFromLspServer(
  diagnosticsReceivedCounter: DiagnosticsReceivedCounter,
  timeout: Int,
  attemptNumber: Int = 1,
) {
  PlatformTestUtil.waitWithEventsDispatching(
    "Diagnostics from server for file ${diagnosticsReceivedCounter.file.name} not received in $timeout seconds",
    {
      ProgressManager.checkCanceled()
      diagnosticsReceivedCounter.diagnosticsReceivedCount >= attemptNumber || diagnosticsReceivedCounter.serverShutdown
    },
    timeout,
  )

  Assert.assertFalse("LSP server initialization failed", diagnosticsReceivedCounter.serverShutdown)
}

/**
 * Removes `<error>`/`<warning>` markup from the current file, waits for the `textDocument/publishDiagnostics` notification from the
 * LSP server and checks that the errors/warnings highlighting for the current file match the expected result.
 *
 * @see checkHighlightingRetrying
 */
@RequiresBlockingContext(ReplaceWith("checkHighlightingRetrying()",
                                     "com.intellij.platform.lsp.testFramework.checkHighlightingRetrying"))
@RequiresEdt
fun CodeInsightTestFixture.checkLspHighlighting() {
  val document = editor.document.let { (it as? DocumentWindow)?.delegate ?: it }
  val data = ExpectedHighlightingData(document, true, true, false)
  data.init() // removes <error>/<warning>/etc. markers
  checkLspHighlightingForData(data)
}

/**
 * @see checkHighlightingRetrying
 */
@RequiresBlockingContext(ReplaceWith("checkHighlightingRetrying(data)",
                                     "com.intellij.platform.lsp.testFramework.checkHighlightingRetrying"))
@RequiresEdt
fun CodeInsightTestFixture.checkLspHighlightingForData(data: ExpectedHighlightingData) {
  withDiagnosticsReceivedCounter(project, file.virtualFile) { diagnosticsReceivedCounter ->
    doWaitForDiagnosticsFromLspServer(diagnosticsReceivedCounter, timeout = 30, attemptNumber = 1)
    doCheckExpectedHighlightingData(this as CodeInsightTestFixtureImpl, data, diagnosticsReceivedCounter, attemptNumber = 1)
  }
}

private fun <T> withDiagnosticsReceivedCounter(project: Project, file: VirtualFile, block: (DiagnosticsReceivedCounter) -> T): T {
  val topLevelFile = (file as? VirtualFileWindow)?.delegate ?: file
  val disposable = Disposer.newDisposable()
  try {
    val diagnosticsReceivedCounter = DiagnosticsReceivedCounter(topLevelFile)
    LspClientManager.getInstance(project).addListener(
      listener = diagnosticsReceivedCounter,
      parentDisposable = disposable,
      sendEventsForExistingClients = true
    )
    return block(diagnosticsReceivedCounter)
  }
  finally {
    Disposer.dispose(disposable)
  }
}

/**
 * LSP servers may send `textDocument/publishDiagnostics` notifications several times for the given file.
 * For example, first: zero problems; second: basic problems (which are quick to calculate);
 * and only the third notification gives all problems for the file.
 * So this function makes up to three attempts to call `fixture.collectAndCheckHighlighting`,
 * waiting for a new `diagnosticsReceived` notification from the server after each unlucky attempt.
 */
@RequiresEdt
private fun doCheckExpectedHighlightingData(
  fixture: CodeInsightTestFixtureImpl,
  data: ExpectedHighlightingData,
  diagnosticsReceivedCounter: DiagnosticsReceivedCounter,
  attemptNumber: Int,
) {
  val maxAttempts = 3

  try {
    fixture.collectAndCheckHighlighting(data)
  }
  catch (cf: ComparisonFailure) {
    val nextAttemptNumber = attemptNumber + 1
    if (nextAttemptNumber > maxAttempts) {
      throw cf
    }

    try {
      doWaitForDiagnosticsFromLspServer(diagnosticsReceivedCounter, timeout = 5, attemptNumber = nextAttemptNumber)
    }
    catch (_: AssertionError) {
      /**
       * Timed out. The server has probably already sent all the diagnostics and is not going to send any updates.
       * Throw the most recent ComparisonFailure: it is still valid because the server hasn't sent a new `diagnosticsReceived`
       * notification after that.
       */
      throw cf
    }

    doCheckExpectedHighlightingData(fixture, data, diagnosticsReceivedCounter, nextAttemptNumber)
  }
}

private class DiagnosticsReceivedCounter(val file: VirtualFile) : LspClientManagerListener {
  @Volatile
  var serverShutdown: Boolean = false
    private set

  private val _diagnosticsReceivedCount = AtomicInteger(0)
  val diagnosticsReceivedCount: Int get() = _diagnosticsReceivedCount.get()

  override fun serverStateChanged(lspClient: LspClient) {
    if (lspClient.state in arrayOf(LspServerState.ShutdownNormally, LspServerState.ShutdownUnexpectedly)) {
      serverShutdown = true
    }
  }

  override fun diagnosticsReceived(lspClient: LspClient, file: VirtualFile) {
    if (file == this.file) {
      _diagnosticsReceivedCount.incrementAndGet()
    }
  }
}
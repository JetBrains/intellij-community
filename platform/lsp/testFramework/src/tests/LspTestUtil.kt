// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.tests

import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerManagerListener
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
 * @see com.intellij.platform.lsp.testFramework.awaitFileOpenedByLspServer
 */
@RequiresBlockingContext
@RequiresEdt
fun waitUntilFileOpenedByLspServer(project: Project, file: VirtualFile) {
  val topLevelFile = (file as? VirtualFileWindow)?.delegate ?: file
  val disposable = Disposer.newDisposable()
  try {
    val fileOpened = AtomicBoolean()
    val serverShutdown = AtomicBoolean()
    LspServerManager.getInstance(project).addLspServerManagerListener(object : LspServerManagerListener {
      override fun serverStateChanged(lspServer: LspServer) {
        if (lspServer.state in arrayOf(LspServerState.ShutdownNormally, LspServerState.ShutdownUnexpectedly)) {
          serverShutdown.set(true)
        }
      }

      override fun fileOpened(lspServer: LspServer, file: VirtualFile) {
        if (file == topLevelFile) fileOpened.set(true)
      }
    }, disposable, sendEventsForExistingServers = true)

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
 * @see com.intellij.platform.lsp.testFramework.awaitDiagnosticsFromLspServer
 */
@RequiresBlockingContext
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
 * @see com.intellij.platform.lsp.testFramework.checkHighlightingRetrying
 */
@RequiresBlockingContext
@RequiresEdt
fun CodeInsightTestFixture.checkLspHighlighting() {
  val document = editor.document.let { (it as? DocumentWindow)?.delegate ?: it }
  val data = ExpectedHighlightingData(document, true, true, false)
  data.init() // removes <error>/<warning>/etc. markers
  checkLspHighlightingForData(data)
}

/**
 * @see com.intellij.platform.lsp.testFramework.checkHighlightingRetrying
 */
@RequiresBlockingContext
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
    LspServerManager.getInstance(project).addLspServerManagerListener(
      listener = diagnosticsReceivedCounter,
      parentDisposable = disposable,
      sendEventsForExistingServers = true,
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

private class DiagnosticsReceivedCounter(val file: VirtualFile) : LspServerManagerListener {
  @Volatile
  var serverShutdown: Boolean = false
    private set

  private val _diagnosticsReceivedCount = AtomicInteger(0)
  val diagnosticsReceivedCount: Int get() = _diagnosticsReceivedCount.get()

  override fun serverStateChanged(lspServer: LspServer) {
    if (lspServer.state in arrayOf(LspServerState.ShutdownNormally, LspServerState.ShutdownUnexpectedly)) {
      serverShutdown = true
    }
  }

  override fun diagnosticsReceived(lspServer: LspServer, file: VirtualFile) {
    if (file == this.file) {
      _diagnosticsReceivedCount.incrementAndGet()
    }
  }
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import org.hildan.chrome.devtools.ChromeDP
import org.hildan.chrome.devtools.sessions.BrowserSession
import org.hildan.chrome.devtools.sessions.use
import org.jetbrains.amper.processes.ProcessOutputListener
import org.jetbrains.amper.processes.runProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Owns the headless browser process and its DevTools socket: launches [browserCommand] (fully
 * assembled by the caller — the harness carries no browser knowledge), waits for the DevTools
 * endpoint, hands a connected [BrowserSession] to [block], and guarantees both the socket and
 * the process are gone when this function returns (Amper's `runProcess` kills the process on
 * cancellation).
 *
 * All browser stdout/stderr goes to [browserOutput] only; nothing else observes it.
 */
@OptIn(ExperimentalCoroutinesApi::class)  // select's onTimeout
suspend fun <T> withHeadlessBrowser(
  browserCommand: List<String>,
  browserOutput: InfrastructureLog,
  setupTimeout: Duration,
  block: suspend CoroutineScope.(BrowserSession) -> T,
): T = coroutineScope {
  val devtoolsUrl = CompletableDeferred<String>()
  val browserProcess = async {
    runProcess(
      command = browserCommand,
      outputListener = DevToolsUrlListener(browserOutput, devtoolsUrl),
    )
  }
  try {
    val url = select {
      devtoolsUrl.onAwait { it }
      browserProcess.onAwait { result -> error("browser exited before exposing its DevTools endpoint: $result") }
      onTimeout(setupTimeout) {
        error("browser did not expose its DevTools endpoint within $setupTimeout")
      }
    }
    ChromeDP.connect(url).use { browser ->
      coroutineScope {
        // When the browser process dies mid-run, the closing DevTools socket normally ends the
        // run gracefully with partial results; this backstop fails the run only when that
        // signal never arrives.
        val exitWatch = launch {
          val result = browserProcess.await()
          delay(BROWSER_EXIT_GRACE)
          error("browser process exited during the run without ending the DevTools session: $result")
        }
        try {
          block(browser)
        }
        finally {
          exitWatch.cancel()
        }
      }
    }
  }
  finally {
    withContext(NonCancellable) {
      browserProcess.cancelAndJoin()
    }
  }
}

private val BROWSER_EXIT_GRACE = 5.seconds

private class DevToolsUrlListener(
  private val browserOutput: InfrastructureLog,
  private val devtoolsUrl: CompletableDeferred<String>,
) : ProcessOutputListener {
  override fun onStdoutLine(line: String, pid: Long) = record(line)
  override fun onStderrLine(line: String, pid: Long) = record(line)

  private fun record(line: String) {
    browserOutput.appendLine(line)
    devtoolsListeningPattern.find(line)?.let { match -> devtoolsUrl.complete(match.groupValues[1]) }
  }

  companion object {
    // Chromium prints "DevTools listening on ws://..." once the endpoint is up — on stderr in
    // every build seen so far, but the banner is matched on both streams to be safe.
    private val devtoolsListeningPattern = Regex("DevTools listening on (ws://\\S+)")
  }
}

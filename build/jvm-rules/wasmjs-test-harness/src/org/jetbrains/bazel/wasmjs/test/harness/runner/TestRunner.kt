// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.hildan.chrome.devtools.domains.runtime.RemoteObject
import org.hildan.chrome.devtools.domains.runtime.events.RuntimeEvent
import org.hildan.chrome.devtools.protocol.ExperimentalChromeApi
import org.hildan.chrome.devtools.sessions.goto
import org.hildan.chrome.devtools.sessions.newPage
import org.hildan.chrome.devtools.sessions.use
import java.net.URI
import java.net.URLEncoder
import java.nio.file.Path
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs the browser test session end to end: static server and headless browser start
 * concurrently, the page loads the generated [indexHtml] (with the test filters as query
 * parameters), the console stream feeds the service-message processor, and structured
 * concurrency guarantees that the browser, the DevTools socket, and the server are all gone
 * on return.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalChromeApi::class)
internal suspend fun runTests(
  browserCommand: List<String>,
  staticContentDir: Path,
  indexHtml: String,
  testFilters: List<String>,
  infrastructureLog: InfrastructureLog,
  bazelDeadline: ComparableTimeMark?,
  browserSetupTimeout: Duration,
  testCompletionGracePeriod: Duration,
): TestRunOutcome = withStaticServer(staticContentDir, indexHtml, infrastructureLog) { server ->
  val baseUri = async { server.serve() }
  withHeadlessBrowser(browserCommand, infrastructureLog, browserSetupTimeout) { browser ->
    browser.newPage().use { page ->
      page.runtime.enable()

      val testRunState = TestRunState()

      // start Browser console listening, and make sure we are listening before navigating to the test page by using [startCollection] helper
      val consoleFeed = page.runtime.events().startCollection { event ->
        when (event) {
          is RuntimeEvent.ConsoleAPICalled -> { // process browser console logs
            val line = event.args.formatConsoleLine()
            testRunState.consume(line)
          }
          is RuntimeEvent.ExceptionThrown -> { // process browser exceptions
            val reason = "${event.exceptionDetails.text} ${event.exceptionDetails.exception?.description.orEmpty()}"
            testRunState.addSyntheticFailure(failure = SyntheticTestFailure.UNCAUGHT_EXCEPTION, details = reason)
            testRunState.interrupt("uncaught exception in the test page: $reason") // TODO: maybe we should not stop on first exception?
          }
          is RuntimeEvent.ExceptionRevoked, // too late to handle that, pageFailure already thrown
          is RuntimeEvent.ExecutionContextCreated,
          is RuntimeEvent.ExecutionContextDestroyed,
          RuntimeEvent.ExecutionContextsCleared,
          is RuntimeEvent.BindingCalled,
          is RuntimeEvent.InspectRequested,
            -> Unit
        }
      }

      // Navigation runs alongside the loop below rather than before it: a page whose load event never
      // fires must still be bounded, and the Bazel deadline is only observed by the select.
      val navigation = async { page.goto(testPageUri(baseUri.await(), testFilters).toString()) }
      while (testRunState.outcome.isActive) {
        // await for tests to complete successfully or fail
        select {
          consoleFeed.onJoin { testRunState.interrupt(STREAM_ENDED_REASON) }
          onTimeout(TEST_COMPLETION_POLLING_FREQUENCY) {
            when {
              !testRunState.hasSeenTestEvents() -> Unit // do not check for idleness until the first test suite has started
              testRunState.isIdleFor(testCompletionGracePeriod) -> testRunState.complete()
              else -> Unit
            }
          }
          when (bazelDeadline) { // TODO: it would be nice not to push that Bazel timeout down that much but keep it in the Main.kt, right now it just simplifies reporting partial tests reports that's why it's done that way
            null -> Unit
            else -> onTimeout(-bazelDeadline.elapsedNow()) {
              testRunState.interrupt(
                when {
                  navigation.isCompleted -> DEADLINE_REASON
                  else -> NAVIGATION_DEADLINE_REASON
                },
              )
            }
          }
        }
      }

      // A page that never finished loading leaves this still running, and it is a child of the
      // enclosing scope: without the cancel, returning here would hang on it.
      navigation.cancelAndJoin()
      consoleFeed.cancelAndJoin()
      require(!testRunState.outcome.isActive) { "testRunState must not be active at that point" }
      testRunState.outcome.await()
    }
  }
}

private const val STREAM_ENDED_REASON =
  "the browser console stream ended before the run completed (browser process or DevTools connection died)"

private const val DEADLINE_REASON = "the harness deadline derived from TEST_TIMEOUT was reached"

private const val NAVIGATION_DEADLINE_REASON =
  "the test page did not finish loading before the harness deadline derived from TEST_TIMEOUT was reached"

private fun List<RemoteObject>.formatConsoleLine(): String = joinToString(" ") { argument ->
  when (val value = argument.value) {
    is JsonPrimitive -> value.contentOrNull ?: value.toString()
    null -> argument.description ?: "null"
    else -> value.toString()
  }
}

// Internal for its unit tests: the filter encoding must round-trip through URLSearchParams.
internal fun testPageUri(base: URI, testFilters: List<String>): URI {
  val query = testFilters.joinToString("&") { filter ->
    "include=${URLEncoder.encode(filter, Charsets.UTF_8)}"
  }.takeIf { it.isNotBlank() }
  val indexHtml = when (query) {
    null -> "index.html"
    else -> "index.html?$query"
  }
  return base.resolve(indexHtml)
}

/**
 * An asynchronous [collect] with a guarantee that when that function returns the flow's collection has started.
 */
context(scope: CoroutineScope)
private fun <T> Flow<T>.startCollection(block: suspend (T) -> Unit): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
  collect(block)
}

private val TEST_COMPLETION_POLLING_FREQUENCY = 30.milliseconds
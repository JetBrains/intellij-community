// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageParserCallback
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes
import jetbrains.buildServer.messages.serviceMessages.ServiceMessagesParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.ParseException

/**
 * The immutable result of one browser test run, rendered when the run closes: the mutable
 * [TestRunState] stays behind it, so nothing the page says after the close can change what is
 * reported.
 */
internal sealed interface TestRunOutcome {
  /** The suites as reported, final. */
  val suites: List<TestSuiteResult>

  /** Console output that belonged to no test; echoed to stdout, never part of the XML report. */
  val orphanOutput: List<String>

  data class Completed(
    override val suites: List<TestSuiteResult>,
    override val orphanOutput: List<String>,
    /**
     * True when the run finished green but executed nothing (empty module, or `--test_filter`
     * matched nothing); [suites] then carries the synthetic no-tests-executed case.
     */
    val executedNoTests: Boolean,
  ) : TestRunOutcome

  /**
   * [orphanOutput] carries the console lines the page printed before the run broke (its own
   * `console.error`s during a failed load, typically): they are the best diagnostic the page
   * left behind and must not vanish with the discarded run state.
   */
  data class InfrastructureFailure(
    override val suites: List<TestSuiteResult>,
    override val orphanOutput: List<String> = emptyList(),
    val reason: String,
  ) : TestRunOutcome
}

internal const val SYNTHETIC_FAILURE_CLASS_NAME = "TEST_RUNNER_ERROR"

internal const val NO_TEST_EXECUTED_DETAILS = "empty module or --test_filter matched nothing"

private fun syntheticFailureCase(failure: SyntheticTestFailure, details: String, output: List<String> = emptyList()): TestCaseResult =
  TestCaseResult(
    className = SYNTHETIC_FAILURE_CLASS_NAME,
    name = failure.caseName,
    durationMillis = 0,
    status = TestStatus.Errored(message = failure.message, details = details),
    output = output,
  )

internal enum class SyntheticTestFailure(val caseName: String, val message: String) {
  INFRASTRUCTURE_FAILURE(
    caseName = "infrastructure-failure",
    message = "wasmjs_test infrastructure failure",
  ),
  UNCAUGHT_EXCEPTION(
    caseName = "uncaught-exception",
    message = "uncaught exception in the test page",
  ),
  NO_TEST_EXECUTED(
    caseName = "no-tests-executed",
    message = "wasmjs_test executed no test",
  ),
}

/**
 * The results of one browser test run: fed line by line from the browser console stream while the
 * run is on — TeamCity service messages interleaved with regular output — then closed exactly once,
 * which renders the immutable [TestRunOutcome]. The run closes when the page prints
 * [TESTS_FINISHED_MARKER] outside a test, or when [interrupt] cuts it short. It records only what
 * a report needs; it knows nothing about XML, exit codes, or the browser.
 *
 * The first close wins and renders the report; anything arriving later is folded into this state
 * and simply arrives too late to be reported rather than blowing up the console feed. A failure no
 * test reported ([addSyntheticFailure]) must therefore land before the close — the run loop records
 * an uncaught page exception before it interrupts, and an empty run is recognised by the close
 * itself, which renders the synthetic no-tests-executed case.
 */
internal class TestRunState {
  private val lock = Semaphore(1)
  val outcome: CompletableDeferred<TestRunOutcome> = CompletableDeferred()

  private val parser = ServiceMessagesParser()
  private val suiteStack = mutableListOf<String>()
  private val finishedTests = mutableListOf<TestCaseResult>()
  private val orphanLines = mutableListOf<String>()
  private var openTest: OpenTest? = null
  private var sawTestEvents = false
  private var interruptionReason: String? = null

  /**
   * Consumes one Browser console log line to fill up test run data. [TESTS_FINISHED_MARKER] ends
   * the run, unless a test is open: then the line is output of that test.
   */
  suspend fun consume(line: String): Unit = lock.withPermit {
    when {
      line == TESTS_FINISHED_MARKER && openTest == null -> {
        if (!outcome.isCompleted) outcome.complete(renderAsCompletedOutcome())
      }
      else -> parser.parseLineToEvents(line).forEach { event -> consumeEvent(event) }
    }
  }

  /**
   * Records a failure no test reported — an uncaught page exception, typically. Must land before
   * the run closes: the close renders the report, and this state is not read afterwards.
   */
  suspend fun addSyntheticFailure(failure: SyntheticTestFailure, details: String, output: List<String> = emptyList()): Unit =
    lock.withPermit {
      finishedTests += syntheticFailureCase(failure, details = details, output = output)
    }

  /**
   * Ends a run that was cut short — a dead console stream, an uncaught page exception, the harness
   * deadline. Partial results are still results, so this reports them (with [reason] carried by the
   * cases that never completed); a run interrupted before any test event has nothing to report and
   * is an infrastructure failure instead.
   *
   * First interruption wins, subsequent one will be ignored in the report.
   */
  suspend fun interrupt(reason: String): Unit = lock.withPermit {
    when {
      outcome.isCompleted -> Unit
      else -> {
        interruptionReason = reason
        val testOutcome = when {
          sawTestEvents -> renderAsCompletedOutcome()
          else -> renderAsInfrastructureFailureOutcome(reason)
        }
        outcome.complete(testOutcome)
      }
    }
  }

  private fun renderAsInfrastructureFailureOutcome(reason: String): TestRunOutcome.InfrastructureFailure {
    val orphanLines = orphanLines.toList()
    val suites = listOf(TestSuiteResult(
      name = SYNTHETIC_FAILURE_CLASS_NAME,
      tests = listOf(syntheticFailureCase(SyntheticTestFailure.INFRASTRUCTURE_FAILURE, details = reason, orphanLines)),
    ))
    return TestRunOutcome.InfrastructureFailure(
      reason = reason,
      suites = suites,
      orphanOutput = orphanLines,
    )
  }

  /**
   * Renders the report of a closing run. A test that is still open — the run ended before its
   * `testFinished` — is reported as errored with the interruption reason, and still-open suites
   * become an errored case too (tests in them never ran; a mere log note would let a run
   * interrupted between tests pass as green), so an interrupted run is never mistaken for a
   * complete one. A green-so-far run that executed nothing is only recognisable here, at the end:
   * it gets the synthetic no-tests-executed case (a run whose interruption already rendered
   * errored cases keeps those as the explanation instead).
   */
  private fun renderAsCompletedOutcome(): TestRunOutcome.Completed {
    val interruptedCases = buildList {
      val openTest = openTest
      if (openTest != null) {
        add(TestCaseResult(
          className = currentClassName(),
          name = openTest.name,
          durationMillis = 0,
          status = TestStatus.Errored(
            message = "test did not complete",
            details = interruptionReason ?: "<no interruption reason>",
          ),
          output = openTest.output,
        ))
      }

      // Even a stack of only the unnamed root suite counts: without this case a run interrupted
      // right after the root suite opened would render nothing and be misread as "no tests
      // executed", losing the interruption reason.
      if (suiteStack.isNotEmpty()) {
        val unfinishedSuites = suiteStack.filter { it.isNotEmpty() }
        add(TestCaseResult(
          className = SYNTHETIC_FAILURE_CLASS_NAME,
          name = "unfinished-test-suites",
          durationMillis = 0,
          status = TestStatus.Errored(
            message = "test suite did not complete: ${unfinishedSuites.joinToString(".").ifEmpty { "<root>" }}",
            details = interruptionReason ?: "<no interruption reason>",
          ),
          output = emptyList(),
        ))
      }
    }

    // The interruption cases are the explanation of an empty run: adding no-tests-executed on top
    // of them would misattribute the failure, and it is checked first when picking the exit code.
    val executedNoTests = interruptedCases.isEmpty() && finishedTests.none { test ->
      when (test.status) { // as a `when` to make sure new status are a compilation failure
        is TestStatus.Errored,
        is TestStatus.Failed,
        TestStatus.Passed,
          -> true
        is TestStatus.Ignored -> false
      }
    }

    val suites = buildList {
      addAll(finishedTests)

      if (executedNoTests) {
        add(syntheticFailureCase(SyntheticTestFailure.NO_TEST_EXECUTED, details = NO_TEST_EXECUTED_DETAILS))
      }

      addAll(interruptedCases)
    }.groupBy { it.className }.map { (className, tests) -> TestSuiteResult(name = className, tests = tests) }

    return TestRunOutcome.Completed(
      executedNoTests = executedNoTests,
      suites = suites,
      orphanOutput = orphanLines.toList(),
    )
  }

  private fun consumeEvent(event: ConsoleEvent) {
    when (event) {
      is ConsoleEvent.Text -> when (val test = openTest) {
        null -> orphanLines += event.text
        else -> openTest = test.copy(output = test.output + event.text)
      }
      is ConsoleEvent.Message -> consumeServiceMessage(event.message)
    }
  }

  /**
   * Handles the messages of kotlin-web-helpers' TeamCity reporter. Mocha runs one test at a time
   * and closes its suites in order, so the messages arrive balanced and in order.
   */
  private fun consumeServiceMessage(message: ServiceMessage) {
    val attributes = message.attributes
    val testName = attributes["name"].orEmpty()
    when (message.messageName) {
      ServiceMessageTypes.TEST_SUITE_STARTED -> {
        suiteStack += testName
        sawTestEvents = true
      }
      ServiceMessageTypes.TEST_SUITE_FINISHED -> suiteStack.removeLastOrNull()
      ServiceMessageTypes.TEST_STARTED -> {
        openTest = OpenTest(name = testName)
        sawTestEvents = true
      }
      ServiceMessageTypes.TEST_FAILED -> when (val test = openTest?.takeIf { it.name == testName }) {
        // Mocha also reports failures outside the open test: an exception after the test passed,
        // or a failing hook, which carries the title of the hook. Each becomes an errored case.
        // A failing beforeEach hook ends the open test without testFinished, so it closes it here.
        null -> {
          finishedTests += TestCaseResult(
            className = currentClassName(),
            name = testName.ifEmpty { "unknown-test" },
            durationMillis = 0,
            status = TestStatus.Errored(
              message = "testFailed did not match a started test: ${attributes["message"].orEmpty()}",
              details = attributes["details"].orEmpty(),
            ),
            output = openTest?.output.orEmpty(),
          )
          openTest = null
          sawTestEvents = true
        }
        else -> openTest = test.copy(failure = TestStatus.Failed(
          message = attributes["message"].orEmpty(),
          details = attributes["details"].orEmpty(),
        ))
      }
      // The reporter sends a skipped test as a testIgnored message alone, without testStarted.
      ServiceMessageTypes.TEST_IGNORED -> finishedTests += TestCaseResult(
        className = currentClassName(),
        name = testName,
        durationMillis = 0,
        status = TestStatus.Ignored(reason = attributes["message"].orEmpty()),
        output = emptyList(),
      )
      ServiceMessageTypes.TEST_FINISHED -> openTest?.let { test ->
        finishedTests += TestCaseResult(
          className = currentClassName(),
          name = test.name,
          durationMillis = attributes["duration"]?.toLongOrNull() ?: 0,
          status = test.failure ?: TestStatus.Passed,
          output = test.output,
        )
        openTest = null
      }
      else -> orphanLines += message.asString()
    }
  }

  private fun currentClassName(): String = suiteStack.filter { it.isNotEmpty() }.joinToString(".").ifEmpty {
    "FAILED_TO_BUILD_CURRENT_CLASS_NAME"
  }
}

data class TestSuiteResult(
  val name: String,
  val tests: List<TestCaseResult>,
)

internal val List<TestSuiteResult>.testCount: Int
  get() = sumOf { it.tests.size }
internal val List<TestSuiteResult>.failedCount: Int
  get() = sumOf { suite -> suite.tests.count { it.status is TestStatus.Failed } }
internal val List<TestSuiteResult>.erroredCount: Int
  get() = sumOf { suite -> suite.tests.count { it.status is TestStatus.Errored } }
internal val List<TestSuiteResult>.ignoredCount: Int
  get() = sumOf { suite -> suite.tests.count { it.status is TestStatus.Ignored } }
internal val List<TestSuiteResult>.executedCount: Int
  get() = testCount - ignoredCount

data class TestCaseResult(
  val className: String,
  val name: String,
  val durationMillis: Long,
  val status: TestStatus,
  val output: List<String>,
)

sealed interface TestStatus {
  data object Passed : TestStatus
  data class Failed(val message: String, val details: String) : TestStatus

  /** The test did not produce a verdict — it started and never finished, or never ran at all. */
  data class Errored(val message: String, val details: String) : TestStatus
  data class Ignored(val reason: String) : TestStatus
}

private sealed interface ConsoleEvent {
  data class Message(val message: ServiceMessage) : ConsoleEvent
  data class Text(val text: String) : ConsoleEvent
}

private data class OpenTest(
  val name: String,
  val failure: TestStatus.Failed? = null,
  val output: List<String> = emptyList(),
)

private fun ServiceMessagesParser.parseLineToEvents(line: String): List<ConsoleEvent> {
  // The collecting list is the necessary bridge out of the callback-based parser API.
  val events = mutableListOf<ConsoleEvent>()
  parse(line, object : ServiceMessageParserCallback {
    override fun regularText(text: String) {
      events.add(ConsoleEvent.Text(text))
    }

    override fun serviceMessage(message: ServiceMessage) {
      events.add(ConsoleEvent.Message(message))
    }

    override fun parseException(parseException: ParseException, text: String) {
      events.add(ConsoleEvent.Text(text))
    }
  })
  return events
}

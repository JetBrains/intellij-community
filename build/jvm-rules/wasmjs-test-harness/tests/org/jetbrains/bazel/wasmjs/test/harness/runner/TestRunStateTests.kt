// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class TestRunStateTests {
  @Test
  fun `passing failing and ignored tests are recorded with suites`(): Unit = runBlocking {
    val result = feed(
      "##teamcity[testSuiteStarted name='sample.CalculatorTest' flowId='1']",
      "##teamcity[testStarted name='addition' flowId='1']",
      "##teamcity[testFinished name='addition' duration='12' flowId='1']",
      "##teamcity[testStarted name='division' flowId='1']",
      "##teamcity[testFailed name='division' message='expected 2, got 3' details='AssertionError|n  at division' flowId='1']",
      "##teamcity[testFinished name='division' duration='7' flowId='1']",
      "##teamcity[testIgnored name='modulo' message='not implemented' flowId='1']",
      "##teamcity[testSuiteFinished name='sample.CalculatorTest' flowId='1']",
    ).completed()

    assertEquals(1, result.suites.size)
    val suite = result.suites.single()
    assertEquals("sample.CalculatorTest", suite.name)
    assertEquals(3, result.suites.testCount)
    assertEquals(1, result.suites.failedCount)
    assertEquals(1, result.suites.ignoredCount)
    assertEquals(2, result.suites.executedCount)

    val byName = suite.tests.associateBy { it.name }
    assertEquals(TestStatus.Passed, byName.getValue("addition").status)
    assertEquals(12, byName.getValue("addition").durationMillis)
    assertEquals(
      TestStatus.Failed(message = "expected 2, got 3", details = "AssertionError\n  at division"),
      byName.getValue("division").status,
    )
    assertEquals(TestStatus.Ignored(reason = "not implemented"), byName.getValue("modulo").status)
  }

  @Test
  fun `nested suites join into the class name`(): Unit = runBlocking {
    val result = feed(
      "##teamcity[testSuiteStarted name='']",
      "##teamcity[testSuiteStarted name='sample']",
      "##teamcity[testSuiteStarted name='StringTest']",
      "##teamcity[testStarted name='concat']",
      "##teamcity[testFinished name='concat' duration='1']",
      "##teamcity[testSuiteFinished name='StringTest']",
      "##teamcity[testSuiteFinished name='sample']",
      "##teamcity[testSuiteFinished name='']",
    ).completed()

    assertEquals("sample.StringTest", result.suites.single().name)
  }

  @Test
  fun `non service message lines are orphan output`(): Unit = runBlocking {
    val result = feed(
      "some banner printed by the module",
      "##teamcity[testStarted name='t']",
      "##teamcity[testFinished name='t' duration='1']",
    ).completed()

    assertEquals(listOf("some banner printed by the module"), result.orphanOutput)
    assertEquals(1, result.suites.testCount)
  }

  @Test
  fun `output during a test is attached to the test`(): Unit = runBlocking {
    val result = feed(
      "##teamcity[testStarted name='t']",
      "println from the test",
      "##teamcity[testStdOut name='t' out='explicit stdout']",
      "##teamcity[testFinished name='t' duration='1']",
    ).completed()

    assertEquals(listOf("println from the test", "explicit stdout"), result.suites.single().tests.single().output)
    assertEquals(emptyList<String>(), result.orphanOutput)
  }

  @Test
  fun `a run with no output at all closes as no tests executed`(): Unit = runBlocking {
    val result = feed().completed()

    assertTrue(result.executedNoTests)
    val synthetic = result.suites.single().tests.single()
    assertEquals("no-tests-executed", synthetic.name)
    assertEquals(SYNTHETIC_FAILURE_CLASS_NAME, synthetic.className)
  }

  @Test
  fun `a run that only ignored tests closes as no tests executed and keeps the ignored cases`(): Unit = runBlocking {
    // Recognisable only at the close: nothing during the run says more tests are not coming.
    val result = feed(
      "##teamcity[testIgnored name='skipped' message='not implemented']",
    ).completed()

    assertTrue(result.executedNoTests)
    val names = result.suites.flatMap { it.tests }.map { it.name }
    // The real cases survive — an all-ignored module still reports what it skipped.
    assertTrue(names.contains("skipped"))
    assertTrue(names.contains("no-tests-executed"))
    assertEquals(1, result.suites.ignoredCount)
    assertEquals(1, result.suites.erroredCount)
  }

  @Test
  fun `a run that executed tests does not carry the no-tests case`(): Unit = runBlocking {
    val result = feed(
      "##teamcity[testStarted name='t']",
      "##teamcity[testFinished name='t' duration='1']",
    ).completed()

    assertFalse(result.executedNoTests)
    assertEquals(listOf("t"), result.suites.flatMap { it.tests }.map { it.name })
  }

  @Test
  fun `an interrupted run keeps its errored cases as the explanation instead of no-tests`(): Unit = runBlocking {
    // Zero tests executed, but the interruption already rendered an errored case carrying the
    // reason: adding no-tests-executed on top would misattribute the failure.
    val result = feed("##teamcity[testSuiteStarted name='']")
      .interrupted("the harness deadline derived from TEST_TIMEOUT was reached")

    assertFalse(result.executedNoTests)
    assertEquals(listOf("unfinished-test-suites"), result.suites.flatMap { it.tests }.map { it.name })
  }

  @Test
  fun `an interrupted open test is reported as errored with the reason`(): Unit = runBlocking {
    val result = feed(
      "##teamcity[testSuiteStarted name='sample.SlowTest']",
      "##teamcity[testStarted name='hangs']",
      "output before the interruption",
    ).interrupted("uncaught exception: boom")

    val test = result.suites.single { it.name == "sample.SlowTest" }.tests.single()
    assertEquals("hangs", test.name)
    assertEquals(
      TestStatus.Errored(message = "test did not complete", details = "uncaught exception: boom"),
      test.status,
    )
    assertEquals(listOf("output before the interruption"), test.output)
    val unfinishedSuites = result.suites.single { it.name == SYNTHETIC_FAILURE_CLASS_NAME }.tests.single()
    assertEquals("unfinished-test-suites", unfinishedSuites.name)
    assertTrue((unfinishedSuites.status as TestStatus.Errored).message.contains("sample.SlowTest"))
    assertEquals(2, result.suites.erroredCount)
  }

  @Test
  fun `an interruption between tests errors the run instead of dropping the remaining suites`(): Unit = runBlocking {
    val result = feed(
      "##teamcity[testSuiteStarted name='sample.SlowSuite']",
      "##teamcity[testStarted name='fast']",
      "##teamcity[testFinished name='fast' duration='1']",
    ).interrupted("the harness deadline derived from TEST_TIMEOUT was reached")

    assertEquals(1, result.suites.erroredCount)
    val errored = result.suites.flatMap { it.tests }.single { it.status is TestStatus.Errored }
    assertEquals("unfinished-test-suites", errored.name)
    assertEquals(
      "the harness deadline derived from TEST_TIMEOUT was reached",
      (errored.status as TestStatus.Errored).details,
    )
  }

  @Test
  fun `an interruption after all suites closed keeps the complete results green`(): Unit = runBlocking {
    val result = feed(
      "##teamcity[testSuiteStarted name='sample.T']",
      "##teamcity[testStarted name='t']",
      "##teamcity[testFinished name='t' duration='1']",
      "##teamcity[testSuiteFinished name='sample.T']",
    ).interrupted("the harness deadline derived from TEST_TIMEOUT was reached")

    assertEquals(1, result.suites.testCount)
    assertEquals(0, result.suites.failedCount)
    assertEquals(0, result.suites.erroredCount)
  }

  @Test
  fun `an interruption before any test event is an infrastructure failure`(): Unit = runBlocking {
    // Nothing ran, so there is nothing to report: the page, the module, or the browser is broken.
    assertTrue(feed("some banner", "##teamcity[somethingElse name='x']").interruptedWith("boom") is TestRunOutcome.InfrastructureFailure)
    assertTrue(feed("##teamcity[testSuiteStarted name='s']").interruptedWith("boom") is TestRunOutcome.Completed)
    assertTrue(feed("##teamcity[testStarted name='t']").interruptedWith("boom") is TestRunOutcome.Completed)
  }

  @Test
  fun `console output before a broken run survives into the infrastructure failure`(): Unit = runBlocking {
    // The page's own console lines during a failed load are the best diagnostic it left behind.
    val outcome = feed("wasm load failed: CompileError", "another page line").interruptedWith("boom")

    val failure = outcome as TestRunOutcome.InfrastructureFailure
    assertEquals(listOf("wasm load failed: CompileError", "another page line"), failure.orphanOutput)
    val reported = failure.suites.single().tests.single()
    assertEquals(listOf("wasm load failed: CompileError", "another page line"), reported.output)
    assertEquals("infrastructure-failure", reported.name)
  }

  @Test
  fun `a testStarted while another test is open flushes the open test as errored`(): Unit = runBlocking {
    // A lost testFinished must not make the earlier test vanish from the report.
    val result = feed(
      "##teamcity[testSuiteStarted name='sample.T']",
      "##teamcity[testStarted name='lost']",
      "output of the lost test",
      "##teamcity[testStarted name='next']",
      "##teamcity[testFinished name='next' duration='1']",
      "##teamcity[testSuiteFinished name='sample.T']",
    ).completed()

    assertEquals(2, result.suites.testCount)
    assertEquals(1, result.suites.erroredCount)
    val byName = result.suites.single().tests.associateBy { it.name }
    val lost = byName.getValue("lost")
    assertEquals(
      TestStatus.Errored(message = "test did not report testFinished before the next test started", details = "next test: next"),
      lost.status,
    )
    assertEquals(listOf("output of the lost test"), lost.output)
    assertEquals(TestStatus.Passed, byName.getValue("next").status)
  }

  @Test
  fun `a failure verdict survives a lost testFinished`(): Unit = runBlocking {
    val result = feed(
      "##teamcity[testStarted name='failed']",
      "##teamcity[testFailed name='failed' message='boom' details='stack']",
      "##teamcity[testStarted name='next']",
      "##teamcity[testFinished name='next' duration='1']",
    ).completed()

    assertEquals(1, result.suites.failedCount)
    assertEquals(0, result.suites.erroredCount)
    val failed = result.suites.flatMap { it.tests }.single { it.name == "failed" }
    assertEquals(TestStatus.Failed(message = "boom", details = "stack"), failed.status)
  }

  @Test
  fun `an interruption with only the root suite open reports an errored case with the reason`(): Unit = runBlocking {
    // kotlin-test opens an unnamed root suite first; a run cut short there must not render
    // nothing and be misread downstream as "no tests executed".
    val result = feed("##teamcity[testSuiteStarted name='']")
      .interrupted("the harness deadline derived from TEST_TIMEOUT was reached")

    assertEquals(1, result.suites.erroredCount)
    val errored = result.suites.single().tests.single()
    assertEquals("unfinished-test-suites", errored.name)
    assertEquals(SYNTHETIC_FAILURE_CLASS_NAME, errored.className)
    val status = errored.status as TestStatus.Errored
    assertTrue(status.message.contains("<root>"))
    assertEquals("the harness deadline derived from TEST_TIMEOUT was reached", status.details)
  }

  @Test
  fun `a testSuiteFinished naming no open suite changes nothing`(): Unit = runBlocking {
    val result = feed(
      "##teamcity[testSuiteStarted name='sample.T']",
      "##teamcity[testSuiteFinished name='other.Suite']",
      "##teamcity[testStarted name='t']",
      "##teamcity[testFinished name='t' duration='1']",
      "##teamcity[testSuiteFinished name='sample.T']",
    ).completed()

    assertEquals("sample.T", result.suites.single().name)
    assertTrue(result.orphanOutput.contains("unmatched testSuiteFinished for 'other.Suite'"))
  }

  @Test
  fun `finishing an outer suite closes the inner suites left open`(): Unit = runBlocking {
    // Recovery from a lost inner testSuiteFinished: the run must still become idle-able.
    val run = feed(
      "##teamcity[testSuiteStarted name='outer']",
      "##teamcity[testSuiteStarted name='inner']",
      "##teamcity[testStarted name='t']",
      "##teamcity[testFinished name='t' duration='1']",
      "##teamcity[testSuiteFinished name='outer']",
    )

    assertTrue(run.isIdleFor(Duration.ZERO))
    assertEquals("outer.inner", run.completed().suites.single().name)
  }

  @Test
  fun `a second testFailed on the same open test wins`(): Unit = runBlocking {
    // kotlin-test emits at most one verdict; if a degraded stream ever repeats one, last wins.
    val result = feed(
      "##teamcity[testStarted name='t']",
      "##teamcity[testFailed name='t' message='first' details='d1']",
      "##teamcity[testFailed name='t' message='second' details='d2']",
      "##teamcity[testFinished name='t' duration='1']",
    ).completed()

    assertEquals(TestStatus.Failed(message = "second", details = "d2"), result.suites.single().tests.single().status)
  }

  @Test
  fun `a testFailed that matches no started test is never dropped`(): Unit = runBlocking {
    val result = feed(
      "##teamcity[testStarted name='first']",
      "##teamcity[testFinished name='first' duration='1']",
      "##teamcity[testFailed name='second' message='boom' details='stack']",
    ).completed()

    assertEquals(1, result.suites.erroredCount)
    val errored = result.suites.flatMap { it.tests }.single { it.status is TestStatus.Errored }
    assertEquals("second", errored.name)
    assertTrue((errored.status as TestStatus.Errored).message.contains("boom"))
  }

  @Test
  fun `suite depth comes from parsed messages, not raw text`(): Unit = runBlocking {
    val open = feed(
      "##teamcity[testSuiteStarted name='sample.T']",
      "##teamcity[testStarted name='t']",
      "assertion output mentioning ##teamcity[testSuiteFinished literally",
    )
    assertFalse(open.isIdleFor(Duration.ZERO))

    val closed = feed(
      "##teamcity[testSuiteStarted name='sample.T']",
      "##teamcity[testSuiteFinished name='sample.T']",
    )
    assertTrue(closed.isIdleFor(Duration.ZERO))
  }

  @Test
  fun `a failure outside any test is reported as an errored case of its own`(): Unit = runBlocking {
    val run = feed(
      "##teamcity[testSuiteStarted name='sample.T']",
      "##teamcity[testStarted name='hangs']",
    )
    run.addSyntheticFailure(SyntheticTestFailure.UNCAUGHT_EXCEPTION, "TypeError: boom")

    val result = run.interrupted("uncaught exception in the test page: TypeError: boom")
    val names = result.suites.flatMap { it.tests }.map { it.name }
    assertTrue(names.contains("hangs"))
    assertTrue(names.contains("unfinished-test-suites"))
    assertTrue(names.contains("uncaught-exception"))
    assertEquals(3, result.suites.erroredCount)
  }

  @Test
  fun `a run is idle only when it went quiet with nothing in flight`(): Unit = runBlocking {
    // Silence is how the harness recognises the end of a run, so anything still open defers it --
    // including the unnamed root suite kotlin-test wraps a run in, which the report filters out.
    assertFalse(feed("##teamcity[testSuiteStarted name='']").isIdleFor(Duration.ZERO))
    assertFalse(feed("##teamcity[testStarted name='t']").isIdleFor(Duration.ZERO))
    assertTrue(feed("##teamcity[testStarted name='t']", "##teamcity[testFinished name='t' duration='1']").isIdleFor(Duration.ZERO))
    // And it is measured from the last console line, not from the moment of the question.
    assertFalse(feed("some banner").isIdleFor(1.hours))
  }

  @Test
  fun `the first close renders the report and anything later changes nothing`(): Unit = runBlocking {
    // The console feed is stopped before the run closes, but a line already in flight must not blow
    // up the feed, and neither it nor a late close or synthetic failure may change what is reported.
    val run = feed("##teamcity[testStarted name='t']", "##teamcity[testFinished name='t' duration='1']")
    run.complete()
    val outcome = run.outcome.await() as TestRunOutcome.Completed

    run.consume("a late banner")
    run.addSyntheticFailure(SyntheticTestFailure.UNCAUGHT_EXCEPTION, "too late")
    run.interrupt("late")
    run.complete()

    assertSame(outcome, run.outcome.await())
    assertEquals(1, outcome.suites.testCount)
    assertEquals(0, outcome.suites.erroredCount)
    assertEquals(emptyList<String>(), outcome.orphanOutput)
  }

  private suspend fun feed(vararg lines: String): TestRunState = TestRunState().apply {
    lines.forEach { line -> consume(line) }
  }

  private suspend fun TestRunState.completed(): TestRunOutcome.Completed {
    complete()
    return outcome.await() as TestRunOutcome.Completed
  }

  private suspend fun TestRunState.interrupted(reason: String): TestRunOutcome.Completed =
    interruptedWith(reason) as TestRunOutcome.Completed

  private suspend fun TestRunState.interruptedWith(reason: String): TestRunOutcome {
    interrupt(reason)
    return outcome.await()
  }
}

/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.execution;

import com.intellij.JavaTestUtil;
import com.intellij.execution.testframework.sm.runner.BaseSMTRunnerTestCase;
import com.intellij.execution.testframework.sm.runner.GeneralToSMTRunnerEventsConvertor;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.history.ImportedToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.openapi.util.Disposer;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AntImportTest extends BaseSMTRunnerTestCase {
  private GeneralToSMTRunnerEventsConvertor myEventsProcessor;
  private SMTestProxy.SMRootTestProxy myRootNode;

  @BeforeEach
  void setUp() {
    myRootNode = new SMTestProxy.SMRootTestProxy();
    myEventsProcessor = new GeneralToSMTRunnerEventsConvertor(getProject(), myRootNode, "SMTestFramework");

    myEventsProcessor.onStartTesting();
  }

  @AfterEach
  void tearDown() {
    myEventsProcessor.onFinishTesting();
    Disposer.dispose(myEventsProcessor);
    Disposer.dispose(myRootNode);
  }

  private SMTestProxy.SMRootTestProxy parseTestResult(@Language("XML") @NotNull String text) throws IOException {
    ImportedToGeneralTestEventsConverter.parseTestResults(() -> new StringReader(text), myEventsProcessor);
    return myRootNode;
  }

  private SMTestProxy.SMRootTestProxy parseTestResultFixture(@NotNull String fileName) throws IOException {
    return parseTestResult(Files.readString(Path.of(JavaTestUtil.getJavaTestDataPath(), "execution/junitReports", fileName)));
  }

  @Test
  public void testAntFormatTest() throws Exception {
    SMTestProxy.SMRootTestProxy rootNode = parseTestResult("""
    <?xml version="1.0" encoding="UTF-8" ?>
    <testsuites>
      <testsuite errors="0" failures="0" hostname="ignore" id="1" name="MyTest" package="a" skipped="0" tests="3" time="0.062" timestamp="2016-10-10T19:25:11">
        <testcase classname="a.MyTest" name="testA1" time="0.002" />
        <testcase classname="a.MyTest" name="testA2" time="0.0" />
        <testcase classname="a.MyTest" name="testA3" time="0.0" />
        <system-out><![CDATA[]]></system-out>
        <system-err><![CDATA[]]></system-err>
      </testsuite>
    </testsuites>
    """);
    List<? extends SMTestProxy> children = rootNode.getChildren();
    assertEquals(1, children.size());
    SMTestProxy suite = children.get(0);
    assertEquals("MyTest", suite.getName());
    List<? extends SMTestProxy> tests = suite.getChildren();
    assertEquals(3, tests.size());
    assertEquals("testA1", tests.get(0).getName());
  }

  @Test
  public void testIgnored() throws Exception {
    SMTestProxy.SMRootTestProxy rootNode = parseTestResult("""
    <?xml version="1.0" encoding="UTF-8" ?>
    <testsuites>
      <testsuite errors="0" failures="0" hostname="ignore" id="1" name="MyTest" package="a" skipped="0" tests="3" time="0.062" timestamp="2016-10-10T19:25:11">
        <testcase classname="a.MyTest" name="testA1" time="0.002">
          <ignored/>
        </testcase>
      </testsuite>
    </testsuites>
    """);
    SMTestProxy suite = rootNode.getChildren().get(0);
    SMTestProxy ignoredTest = suite.getChildren().get(0);
    assertTrue(ignoredTest.isIgnored());
  }

  @Test
  public void testSkipped() throws Exception {
    SMTestProxy.SMRootTestProxy rootNode = parseTestResult("""
    <?xml version="1.0" encoding="UTF-8" ?>
    <testsuites>
      <testsuite errors="0" failures="0" hostname="ignore" id="1" name="MyTest" package="a" skipped="0" tests="3" time="0.062" timestamp="2016-10-10T19:25:11">
        <testcase classname="a.MyTest" name="testA1" time="0.002">
          <skipped/>
        </testcase>
      </testsuite>
    </testsuites>
    """);
    SMTestProxy suite = rootNode.getChildren().get(0);
    SMTestProxy ignoredTest = suite.getChildren().get(0);
    assertTrue(ignoredTest.isIgnored());
  }

  @Test
  public void testMavenSingleSuiteReportPreservesFailure() throws Exception {
    SMTestProxy.SMRootTestProxy rootNode = parseTestResultFixture("maven-single-suite-failure.xml");

    SMTestProxy test = findTest(rootNode, "testPrivateExpectFakeOverride_incompatibleReturnType()");
    assertTrue(test.isDefect());
    assertContains(test.getErrorMessage(), "Actual data differs from file content");
    assertContains(test.getStacktrace(), "GlobalMetadataInfoHandler.compareAllMetaDataInfos");
    DiffHyperlink diffHyperlink = test.getDiffViewerProvider();
    assertNotNull(diffHyperlink);
    assertEquals("...ExpectClass(val [<!ACTUAL_WITHOUT_EXPECT!>x<!>]: Int)...", diffHyperlink.getLeft());
    assertEquals("...ExpectClass(val [x]: Int)...", diffHyperlink.getRight());
  }

  @Test
  public void testCommonJUnitReportPreservesOutputAndProperties() throws Exception {
    SMTestProxy.SMRootTestProxy rootNode = parseTestResultFixture("common-junit-report.xml");

    SMTestProxy failure = findTest(rootNode, "testCase5");
    assertTrue(failure.isDefect());
    assertEquals("Expected value did not match.", failure.getErrorMessage());
    assertContains(failure.getStacktrace(), "Tests.Registration.testCase5");

    SMTestProxy error = findTest(rootNode, "testCase6");
    assertTrue(error.isDefect());
    assertEquals("Division by zero.", error.getErrorMessage());

    SMTestProxy skipped = findTest(rootNode, "testCase4");
    assertTrue(skipped.isIgnored());

    SMTestProxy output = findTest(rootNode, "testCase7");
    MockPrinter outputPrinter = MockPrinter.fillPrinter(output);
    assertContains(outputPrinter.getStdOut(), "Data written to standard out.");
    assertContains(outputPrinter.getStdErr(), "Data written to standard error.");

    SMTestProxy properties = findTest(rootNode, "testCase8");
    MockPrinter propertiesPrinter = MockPrinter.fillPrinter(properties);
    assertContains(propertiesPrinter.getStdOut(), "Property priority=high");
    assertContains(propertiesPrinter.getStdOut(), "Property attachment=screenshots/dashboard.png");
    assertContains(propertiesPrinter.getStdOut(), "This text describes the purpose of this test case");
  }

  @Test
  public void testSurefireRerunAndFlakyDetailsBecomeAttempts() throws Exception {
    SMTestProxy.SMRootTestProxy rootNode = parseTestResultFixture("surefire-rerun-flaky-report.xml");

    SMTestProxy flaky = findTest(rootNode, "flakyPassesOnRerun");
    assertTrue(flaky.isSuite());
    assertTrue(flaky.isPassed());
    assertEquals(3, flaky.getChildren().size());
    SMTestProxy first = flaky.getChildren().getFirst();
    assertEquals("repetition 1 of 3", first.getName());
    assertEquals("first attempt failed", first.getErrorMessage());
    assertContains(first.getStacktrace(), "java.lang.AssertionError: first attempt failed");
    assertEquals("second attempt errored", flaky.getChildren().get(1).getErrorMessage());
    assertEquals(TestStateInfo.Magnitude.FAILED_INDEX, first.getMagnitudeInfo());
    assertEquals(TestStateInfo.Magnitude.ERROR_INDEX, flaky.getChildren().get(1).getMagnitudeInfo());
    SMTestProxy passed = flaky.getChildren().getLast();
    assertEquals("repetition 3 of 3", passed.getName());
    assertTrue(passed.isPassed());
    assertEquals(100L, passed.getDuration());
    assertEquals("java:test://RetryingTest/flakyPassesOnRerun", passed.getLocationUrl());
    assertEquals(passed.getLocationUrl(), flaky.getLocationUrl());
    assertEquals("first attempt output", MockPrinter.fillPrinter(first).getStdOut());
    assertFalse(first.getStacktrace().contains("first attempt output"));
    assertEquals("successful output", MockPrinter.fillPrinter(passed).getStdOut());

    SMTestProxy failed = findTest(rootNode, "failsAfterRerun");
    assertTrue(failed.isDefect());
    assertEquals(3, failed.getChildren().size());
    assertEquals("initial failure", failed.getChildren().getFirst().getErrorMessage());
    assertEquals(100L, failed.getChildren().getFirst().getDuration());
    assertEquals("initial output", MockPrinter.fillPrinter(failed.getChildren().getFirst()).getStdOut());
    assertEquals("rerun failed", failed.getChildren().get(1).getErrorMessage());
    assertContains(failed.getChildren().get(1).getStacktrace(), "java.lang.AssertionError: rerun failed");
    assertEquals("rerun errored", failed.getChildren().getLast().getErrorMessage());
    assertEquals(TestStateInfo.Magnitude.ERROR_INDEX, failed.getMagnitudeInfo());
    assertEquals("retry output", MockPrinter.fillPrinter(failed.getChildren().getLast()).getStdOut());
    assertFalse(failed.getChildren().getLast().getStacktrace().contains("retry output"));
  }

  @Test
  public void testGradleMergedRerunsPreserveAttemptsAndOutput() throws Exception {
    var root = parseTestResultFixture("gradle-merged-reruns.xml");

    SMTestProxy flaky = findTest(root, "passes on retry");
    assertTrue(flaky.isPassed());
    assertEquals(3, flaky.getChildren().size());
    SMTestProxy first = flaky.getChildren().getFirst();
    assertEquals(TestStateInfo.Magnitude.FAILED_INDEX, first.getMagnitudeInfo());
    assertEquals("first failure trace", first.getStacktrace().trim());
    assertEquals("failed attempt stdout", MockPrinter.fillPrinter(first).getStdOut());
    assertContains(MockPrinter.fillPrinter(first).getStdErr(), "failed attempt stderr");
    assertEquals("second failure", flaky.getChildren().get(1).getErrorMessage());
    assertEquals("final success stdout", MockPrinter.fillPrinter(flaky.getChildren().getLast()).getStdOut());
    assertEquals(40L, flaky.getDuration());

    SMTestProxy failed = findTest(root, "fails on every retry");
    assertEquals(2, failed.getChildren().size());
    assertEquals("repetition 1 of 2", failed.getChildren().getFirst().getName());
    assertEquals("initial attempt stdout", MockPrinter.fillPrinter(failed.getChildren().getFirst()).getStdOut());
    assertEquals("last failure trace", failed.getChildren().getLast().getStacktrace().trim());
    assertContains(MockPrinter.fillPrinter(failed.getChildren().getLast()).getStdErr(), "last attempt stderr");
    assertEquals(TestStateInfo.Magnitude.FAILED_INDEX, failed.getMagnitudeInfo());
    assertEquals(30L, failed.getDuration());

    SMTestProxy ordinary = findTest(root, "passes once");
    assertFalse(ordinary.isSuite());
    assertTrue(ordinary.isPassed());
    assertEquals(3, root.getChildren().getFirst().getChildren().size());
  }

  @Test
  public void testNamespacedDiagnosticsPreserveDirectText() throws Exception {
    var root = parseTestResult("""
      <j:testsuites xmlns:j="urn:example:junit">
        <j:testsuite name="Tests">
          <j:testcase name="flaky">
            <j:flakyError type="Exception"><![CDATA[direct error trace]]></j:flakyError>
          </j:testcase>
          <j:testcase name="ordinary">
            <j:unknownDiagnostic>unknown details</j:unknownDiagnostic>
          </j:testcase>
          <j:testcase name="skipped"><j:skipped message="disabled"/></j:testcase>
        </j:testsuite>
      </j:testsuites>
      """);

    SMTestProxy flaky = findTest(root, "flaky");
    assertTrue(flaky.isPassed());
    assertEquals(2, flaky.getChildren().size());
    SMTestProxy first = flaky.getChildren().getFirst();
    assertEquals(TestStateInfo.Magnitude.ERROR_INDEX, first.getMagnitudeInfo());
    assertEquals("Exception", first.getErrorMessage());
    assertEquals("direct error trace", first.getStacktrace());
    SMTestProxy ordinary = findTest(root, "ordinary");
    assertFalse(ordinary.isSuite());
    assertTrue(ordinary.isPassed());
    assertTrue(findTest(root, "skipped").isIgnored());
  }

  @Test
  public void testNestedSuitesPreserveHierarchyAndResults() throws Exception {
    SMTestProxy.SMRootTestProxy rootNode = parseTestResultFixture("nested-junit-report.xml");

    assertEquals(1, rootNode.getChildren().size());
    SMTestProxy allTests = rootNode.getChildren().getFirst();
    assertEquals("idea.import.AllChecks", allTests.getName());
    assertEquals(2, allTests.getChildren().size());

    SMTestProxy fastChecks = allTests.getChildren().getFirst();
    assertEquals("idea.import.FastChecks", fastChecks.getName());
    assertEquals(1, fastChecks.getChildren().size());
    SMTestProxy arithmeticChecks = fastChecks.getChildren().getFirst();
    assertEquals("idea.import.ArithmeticChecks", arithmeticChecks.getName());
    assertEquals(4, arithmeticChecks.getChildren().size());
    assertTrue(findTest(rootNode, "reportsUnexpectedProduct").isDefect());
    assertTrue(findTest(rootNode, "skipsDivisionByZero").isIgnored());
    assertTrue(findTest(rootNode, "reportsMissingOperand").isDefect());
    MockPrinter fastChecksPrinter = MockPrinter.fillPrinter(fastChecks);
    assertContains(fastChecksPrinter.getStdOut(), "Arithmetic checks produced diagnostic output");
    assertContains(fastChecksPrinter.getStdOut(), "Fast checks finished");
    assertContains(fastChecksPrinter.getStdErr(), "Arithmetic checks produced diagnostic warnings");

    SMTestProxy serviceChecks = allTests.getChildren().get(1);
    assertEquals("idea.import.ServiceChecks", serviceChecks.getName());
    assertEquals(3, serviceChecks.getChildren().size());
  }

  @Test
  public void testFlakyAttemptsPreserveComparisonAndFinalResult() throws Exception {
    var root = parseTestResult("""
      <testsuite name="Tests">
        <testcase name="flaky" classname="Tests" time="0.25">
          <flakyFailure message="expected:&lt;expected&gt; but was:&lt;actual&gt;">
            <stackTrace>comparison trace</stackTrace>
            <system-err>attempt stderr</system-err>
          </flakyFailure>
          <system-out>passed stdout</system-out>
        </testcase>
        <testcase name="next"/>
      </testsuite>
      """);

    SMTestProxy flaky = findTest(root, "flaky");
    SMTestProxy failed = flaky.getChildren().getFirst();
    assertTrue(failed.isDefect());
    DiffHyperlink diff = failed.getDiffViewerProvider();
    assertNotNull(diff);
    assertEquals("expected", diff.getLeft());
    assertEquals("actual", diff.getRight());
    assertEquals("comparison trace", failed.getStacktrace().trim());
    assertContains(MockPrinter.fillPrinter(failed).getStdErr(), "attempt stderr");
    assertFalse(MockPrinter.fillPrinter(failed).getStdOut().contains("passed stdout"));
    assertTrue(flaky.isPassed());
    assertEquals(250L, flaky.getDuration());
    assertTrue(root.getChildren().getFirst().isPassed());
    assertTrue(findTest(root, "next").isPassed());
  }

  @Test
  public void testMultiplePrimaryProblemsRemainOneAttempt() throws Exception {
    var root = parseTestResult("""
      <testsuite name="Tests">
        <testcase name="multiple">
          <failure message="first">first trace</failure>
          <error message="second" type="Exception">second trace</error>
          <rerunFailure message="retry">retry trace</rerunFailure>
        </testcase>
      </testsuite>
      """);

    SMTestProxy test = findTest(root, "multiple");
    assertEquals(2, test.getChildren().size());
    SMTestProxy first = test.getChildren().getFirst();
    assertEquals("first", first.getErrorMessage());
    assertContains(first.getStacktrace(), "second (Exception)");
    assertFalse(first.getStacktrace().contains("retry trace"));
    assertEquals("retry", test.getChildren().getLast().getErrorMessage());
    assertTrue(test.isDefect());
  }

  @Test
  @SuppressWarnings("UnnecessaryUnicodeEscape")
  public void testProblemStringsArePreserved() throws Exception {
    SMTestProxy.SMRootTestProxy rootNode = parseTestResultFixture("unusual-strings-junit-report.xml");

    SMTestProxy failure = findTest(rootNode, "rendersCafeComparison");
    assertEquals("\uFEFFExpected \u2018caf\u00E9\u2019 but was \u2018cafe\u2019", failure.getErrorMessage());
    assertContains(failure.getStacktrace(), "Terminal-style markers: [31mDIFFERENT[0m");
    MockPrinter failurePrinter = MockPrinter.fillPrinter(failure);
    assertContains(failurePrinter.getStdOut(), "[32mencoded stdout[0m");
    assertContains(failurePrinter.getStdErr(), "decoder warning [33mCHECK INPUT[0m");

    SMTestProxy error = findTest(rootNode, "readsSymbol\uD834\uDD1EWithoutLoss");
    assertEquals("java.nio.charset.MalformedInputException: Input length = 1", error.getErrorMessage());

    SMTestProxy flaky = findTest(rootNode, "recoversAfterRetry   ");
    assertEquals("recoversAfterRetry   ", flaky.getName());
    MockPrinter flakyPrinter = MockPrinter.fillPrinter(flaky);
    assertContains(flakyPrinter.getStdOut(), "retry output [32mRECOVERED[0m");
    assertTrue(flaky.isPassed());
    assertEquals("   ", flaky.getChildren().getFirst().getErrorMessage());
    assertContains(flaky.getChildren().getFirst().getStacktrace(), "first attempt compared decomposed text");
    assertContains(flaky.getChildren().get(1).getStacktrace(), "second attempt rejected the byte sequence");

    SMTestProxy retryChecks = rootNode.getChildren().get(1);
    assertEquals("dev.acme.RetryTextChecks", retryChecks.getName());
    assertEquals(2, retryChecks.getChildren().size());
    SMTestProxy retried = findTest(rootNode, "continuesAfterReplacement\uFFFD");
    assertTrue(retried.isDefect());
    assertEquals(2, retried.getChildren().size());
    assertContains(retried.getChildren().getFirst().getStacktrace(), "first run saw a replacement character");
    assertContains(retried.getChildren().getLast().getStacktrace(), "second run reset the decoder");
    assertTrue(findTest(rootNode, "stillRunsAfterOddName").isPassed());
  }

  private static @NotNull SMTestProxy findTest(@NotNull SMTestProxy.SMRootTestProxy rootNode, @NotNull String testName) {
    SMTestProxy test = (SMTestProxy)findTestByName(testName, rootNode);
    assertNotNull(test);
    return test;
  }

  private static void assertContains(@Nullable String actual, @NotNull String expected) {
    assertNotNull(actual);
    assertTrue(actual.contains(expected), actual);
  }
}

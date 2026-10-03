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
import com.intellij.execution.testframework.sm.runner.ui.MockPrinter;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.openapi.util.Disposer;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class AntImportTest extends BaseSMTRunnerTestCase {
  private GeneralToSMTRunnerEventsConvertor myEventsProcessor;
  private SMTestProxy.SMRootTestProxy myRootNode;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRootNode = new SMTestProxy.SMRootTestProxy();
    myEventsProcessor = new GeneralToSMTRunnerEventsConvertor(getProject(), myRootNode, "SMTestFramework");

    myEventsProcessor.onStartTesting();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myEventsProcessor.onFinishTesting();
      Disposer.dispose(myEventsProcessor);
      Disposer.dispose(myRootNode);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myRootNode = null;
      myEventsProcessor = null;
      super.tearDown();
    }
  }

  private SMTestProxy.SMRootTestProxy parseTestResult(@Language("XML") @NotNull String text) throws IOException {
    ImportedToGeneralTestEventsConverter.parseTestResults(() -> new StringReader(text), myEventsProcessor);
    return myRootNode;
  }

  private SMTestProxy.SMRootTestProxy parseTestResultFixture(@NotNull String fileName) throws IOException {
    return parseTestResult(Files.readString(Path.of(JavaTestUtil.getJavaTestDataPath(), "execution/junitReports", fileName)));
  }

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

  public void testSurefireRerunAndFlakyDetailsAreExposedAreFlattened() throws Exception {
    SMTestProxy.SMRootTestProxy rootNode = parseTestResultFixture("surefire-rerun-flaky-report.xml");

    // Note: SMTestProxy does not model individual Surefire attempts:

    // * flaky tests are emitted as stderr, because these test eventually passed
    SMTestProxy flaky = findTest(rootNode, "flakyPassesOnRerun");
    assertTrue(flaky.isPassed());
    MockPrinter flakyPrinter = MockPrinter.fillPrinter(flaky);
    assertContains(flakyPrinter.getStdErr(), "Flaky failure");
    assertContains(flakyPrinter.getStdErr(), "java.lang.AssertionError: first attempt failed");
    assertContains(flakyPrinter.getStdErr(), "Flaky error");
    assertContains(flakyPrinter.getStdErr(), "java.lang.IllegalStateException: second attempt errored");

    // * re-ran tests are appended to the primary failure's stack trace.
    SMTestProxy failed = findTest(rootNode, "failsAfterRerun");
    assertTrue(failed.isDefect());
    assertEquals("initial failure", failed.getErrorMessage());
    assertContains(failed.getStacktrace(), "java.lang.AssertionError: initial failure");
    assertContains(failed.getStacktrace(), "Rerun failure");
    assertContains(failed.getStacktrace(), "java.lang.AssertionError: rerun failed");
    assertContains(failed.getStacktrace(), "Rerun error");
    assertContains(failed.getStacktrace(), "java.lang.IllegalStateException: rerun errored");
  }

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
    assertContains(flakyPrinter.getStdErr(), "first attempt compared decomposed text");
    assertContains(flakyPrinter.getStdErr(), "second attempt rejected the byte sequence");

    SMTestProxy retryChecks = rootNode.getChildren().get(1);
    assertEquals("dev.acme.RetryTextChecks", retryChecks.getName());
    assertEquals(2, retryChecks.getChildren().size());
    SMTestProxy retried = findTest(rootNode, "continuesAfterReplacement\uFFFD");
    assertTrue(retried.isPassed());
    MockPrinter retriedPrinter = MockPrinter.fillPrinter(retried);
    assertContains(retriedPrinter.getStdErr(), "first run saw a replacement character");
    assertContains(retriedPrinter.getStdErr(), "second run reset the decoder");
    assertTrue(findTest(rootNode, "stillRunsAfterOddName").isPassed());
  }

  private static @NotNull SMTestProxy findTest(@NotNull SMTestProxy.SMRootTestProxy rootNode, @NotNull String testName) {
    SMTestProxy test = (SMTestProxy)findTestByName(testName, rootNode);
    assertNotNull(test);
    return test;
  }

  private static void assertContains(@Nullable String actual, @NotNull String expected) {
    assertNotNull(actual);
    assertTrue(actual, actual.contains(expected));
  }
}

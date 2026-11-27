// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.performance;

import com.intellij.formatting.FormatterTestUtils;
import com.intellij.java.psi.formatter.java.JavaFormatterFixture;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import com.intellij.util.ThrowableRunnable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterFixture;
import static com.intellij.java.psi.formatter.java.JavaFormatterFixtureKt.javaFormatterProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@PerformanceUnitTest
@StressTestApplication
public class JavaSmartReformatPerformanceTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaFormatterProjectFixture();
  private final TestFixture<JavaFormatterFixture> formatterFixture = javaFormatterFixture(projectFixture);
  private String testMethodName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    testMethodName = testInfo.getTestMethod().orElseThrow().getName();
  }

  @Test
  public void testSmartReformatPerformanceInLargeFile_AsUsedInPostponedFormatting() {
    JavaFormatterFixture formatter = formatterFixture.get();
    Project project = formatter.getProject();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();

    PsiFile file = formatter.createFile("A.java", "");
    Document document = ReadAction.computeBlocking(() -> documentManager.getDocument(file));
    assertNotNull(document);

    String initial = JavaFormatterFixture.loadFile("/performance/bigFile.java");
    ThrowableRunnable setup = () -> {
      Runnable command = () -> WriteAction.run(() -> document.replaceString(0, document.getTextLength(), initial));
      commandProcessor.executeCommand(project, command, null, null);
      documentManager.commitDocument(document);
    };

    ThrowableRunnable test = () -> {
      Runnable command =
        () -> WriteAction.run(() -> FormatterTestUtils.ACTIONS.get(FormatterTestUtils.Action.REFORMAT_WITH_CONTEXT).run(file, 6682, 6686));
      commandProcessor.executeCommand(project, command, null, null);
    };

    String launchName = "smart reformat on big file";
    EdtTestUtil.runInEdtAndWait(() -> Benchmark
      .newBenchmark(launchName, test)
      .setup(setup)
      .warmupIterations(50)
      .attempts(100)
      .start(getClass().getName() + "." + testMethodName + " - " + launchName));
    // attempt.min.ms varies below the measurement threshold
  }
}
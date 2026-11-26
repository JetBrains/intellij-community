// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@PerformanceUnitTest
@StressTestApplication
public class JoinLinesPerformanceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testHugeArray() {
    int count = 2000;
    String bytesOriginal = String.join(",\n", Collections.nCopies(count, "0"));
    String bytesResult = String.join(", ", Collections.nCopies(count, "0"));
    String text = """
      class X {
        <selection>byte[] data = {$bytes$}</selection>;
      }""";
    String inputText = text.replace("$bytes$", bytesOriginal);

    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      Benchmark.newBenchmark(myFixture.getTestName(false),
                             () -> EditorTestUtil.executeAction(myFixture.getEditor(), IdeActions.ACTION_EDITOR_JOIN_LINES, true))
        .setup(() -> myFixture.configureByText("X.java", inputText))
        .start(JoinLinesPerformanceTest.class.getName() + ".testHugeArray - " + myFixture.getTestName(false));
      String outputText = text.replace("$bytes$", bytesResult);
      myFixture.checkResult(outputText);
    });
  }
}

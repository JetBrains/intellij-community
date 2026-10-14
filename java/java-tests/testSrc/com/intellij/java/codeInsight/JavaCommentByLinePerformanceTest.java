// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.CommentByLineTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@PerformanceUnitTest
@StressTestApplication
public class JavaCommentByLinePerformanceTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @Test
  public void testUncommentLargeFilePerformance() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    StringBuilder source = new StringBuilder("class C {\n");
    for (int i = 0; i < 5000; i++) {
      source.append("    int value").append(i).append(";\n");
    }
    source.append("}");
    myFixture.configureByText("UncommentLargeFilePerformance.java", source.toString());
    myFixture.performEditorAction(IdeActions.ACTION_SELECT_ALL);
    Benchmark.newBenchmark("Uncommenting large file", () -> CommentByLineTestUtil.performAction(myFixture))
      .setup(() -> CommentByLineTestUtil.performAction(myFixture))
      .start();
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.EnterActionTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PerformanceUnitTest;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.tools.ide.metrics.benchmark.Benchmark;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@PerformanceUnitTest
@StressTestApplication
public class JavaEnterActionPerformanceTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath());
  }

  @Test
  public void testPerformance() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/codeInsight/enterAction/Performance.java");
    Benchmark.newBenchmark("enter in " + myFixture.getFile(), () -> {
      EnterActionTestUtil.performAction(myFixture);
      EdtTestUtil.runInEdtAndWait(() -> {
        EditorTestUtil.executeAction(myFixture.getEditor(), IdeActions.ACTION_EDITOR_DELETE_LINE);
        EditorTestUtil.executeAction(myFixture.getEditor(), IdeActions.ACTION_EDITOR_MOVE_CARET_UP);
      });
    }).start();
  }


  @Test
  public void testEnterPerformanceAfterDeepTree() {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", ("class Foo {\n" +
                                         "  {\n" +
                                         "    u." +
                                         StringUtil.repeat("\n      a('b').c(new Some()).", 500)) + "<caret>\n" +
                                        "      x(); } }");
    Benchmark.newBenchmark("enter", () -> EnterActionTestUtil.performAction(myFixture)).start();
  }
}
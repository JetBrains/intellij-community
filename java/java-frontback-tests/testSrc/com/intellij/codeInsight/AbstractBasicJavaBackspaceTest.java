// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.PathJavaTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.refactoring.InplaceIntroduceTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

public abstract class AbstractBasicJavaBackspaceTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testName;

  @BeforeEach
  void setUpBackspaceTest(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(PathJavaTestUtil.getCommunityJavaTestDataPath());
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
  }

  @Test
  public void testBracket1() { doTest(); }

  @Test
  public void testBracket2() { doTest(); }

  @Test
  public void testBracket3() { doTest(); }

  @Test
  public void testBracket4() { doTest(); }

  @Test
  public void testIdea186011() { doTest(); }

  @Test
  public void testQuote1() { doTest(); }

  @Test
  public void testQuote2() { doTest(); }

  @Test
  public void testQuoteAndCommentAfter() { doTest(); }

  protected void doTest() {
    @NonNls String path = "/codeInsight/backspace/";

    CodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByFile(path + testName + ".java");
    InplaceIntroduceTestUtil.runInCommand(fixture.getProject(), () -> {
      EditorTestUtil.executeAction(fixture.getEditor(), IdeActions.ACTION_EDITOR_BACKSPACE, true);
      fixture.checkResultByFile(path + testName + "_after.java");
    });
  }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.PathJavaTestUtil;
import com.intellij.codeInsight.defaultAction.DefaultActionTestUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

public abstract class AbstractBasicJavaLBraceTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testName;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(PathJavaTestUtil.getCommunityJavaTestDataPath());
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
  }

  @Test
  public void testArrayInitializer() { doTest(); }
  @Test
  public void testArrayInitializerNoSpace() { doTest(); }
  @Test
  public void testInsideCodeBlock() { doTest(); }
  @Test
  public void testOutsideCodeBlock() { doTest(); }
  @Test
  public void testArrayInitializerBegins() { doTest(); }
  @Test
  public void testArrayInitializer1_5Style() { doTest(); }

  protected void doTest() {
    configureByFile(getTestPath() + ".java");
    performAction('{');
    checkResultByFile(getTestPath() + "_after.java");
  }

  private String getTestPath() {
    return "codeInsight/defaultAction/lbrace/" + testName;
  }

  private void configureByFile(String filePath) {
    codeInsightFixture.get().configureByFile(filePath);
  }

  private void performAction(char c) {
    DefaultActionTestUtil.performAction(codeInsightFixture.get(), c);
  }

  private void checkResultByFile(String filePath) {
    codeInsightFixture.get().checkResultByFile(filePath);
  }
}

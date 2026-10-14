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

public abstract class AbstractBasicJavaRParenthTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private String testName;
  private static final String BASE_PATH = "codeInsight/defaultAction/rparenth/";


  @BeforeEach
  void setUp(TestInfo testInfo) {
    codeInsightFixture.get().setTestDataPath(PathJavaTestUtil.getCommunityJavaTestDataPath());
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
  }

  @Test
  public void test1() { doTest(); }

  @Test
  public void test2() { doTest(); }

  @Test
  public void test3() { doTest(); }

  @Test
  public void test4() { doTest(); }

  @Test
  public void test5() { doTest(); }

  private void doTest() {
    configureByFile(BASE_PATH + testName + ".java");
    performAction(')');
    checkResultByFile(BASE_PATH + testName + "_after.java");
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

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.generation.surroundWith.JavaWithIfElseExpressionSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithRunnableSurrounder;
import com.intellij.codeInsight.generation.surroundWith.JavaWithTryCatchSurrounder;
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class JavaSurroundWithTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testTryCatch1() {
    doTest(new JavaWithTryCatchSurrounder());
  }

  @Test
  public void testRunnable() {
    doTest(new JavaWithRunnableSurrounder());
  }

  @Test
  public void testBlockedByPSI() {
    doTest(new JavaWithIfElseExpressionSurrounder());
  }

  private void doTest(Surrounder handler) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String baseName = getBaseName();
    myFixture.configureByFile(baseName + "." + "java");
    myFixture.runInCommand(() -> {
      SurroundWithHandler.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), handler);
      myFixture.checkResultByFile(baseName + "_after." + "java");
    });
  }

  private String getBaseName() {
    return "/codeInsight/surroundWith/" + codeInsightFixture.get().getTestName(false);
  }
}


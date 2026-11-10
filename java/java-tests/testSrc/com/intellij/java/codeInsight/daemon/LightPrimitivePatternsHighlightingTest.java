// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class LightPrimitivePatternsHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_23);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingPatternsWithPrimitives");
  }

  @Test
  public void testRecordPrimitiveInstanceOfPattern() {
    doTest();
  }

  @Test
  public void testSimplePrimitiveInstanceOf() {
    doTest();
  }

  @Test
  public void testSimplePrimitiveInstanceOfPattern() {
    doTest();
  }

  @Test
  public void testSwitchConstantPrimitiveAllowed() { doTest(); }

  @Test
  public void testSwitchConstantPrimitiveSimilar() { doTest(); }

  @Test
  public void testSwitchPrimitivePatternList() { doTest(); }

  @Test
  public void testSwitchPrimitivePatternApplicable() { doTest(); }

  @Test
  public void testSwitchPrimitivePatternDominated() { doTest(); }

  @Test
  public void testSwitchPrimitiveCompleteness() { doTest(); }

  @Test
  public void testSwitchRecordPrimitive() { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  @Test
  public void testSwitchRecordPrimitiveJava25Preview() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_25_PREVIEW, this::doTest);
  }

  @Test
  public void testSwitchRecordPrimitiveJava25() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_25, this::doTest);
  }

  @Test
  public void testSwitchConstantExpression() { doTest(); }

  @Test
  public void testPrimitiveSwitchValueDomination() { doTest(); }

  @Test
  public void testSimplePrimitiveInstanceOfWithJava28() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_28_PREVIEW, this::doTest);
  }
}
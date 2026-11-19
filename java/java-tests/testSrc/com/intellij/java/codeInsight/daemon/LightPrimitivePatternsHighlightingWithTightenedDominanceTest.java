// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

//partially use testdata from LightPrimitivePatternsHighlightingTest
@TestApplication
public class LightPrimitivePatternsHighlightingWithTightenedDominanceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_26);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(getProjectFixture());

  /**
   * Returns the project of the tests. A subclass runs the tests of this class again in its own project.
   */
  protected TestFixture<LightJavaProject> getProjectFixture() {
    return projectFixture;
  }

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingPatternsWithPrimitives");
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }


  //nothing should be changed from 21
  @Test
  public void testRecordPrimitiveInstanceOfPattern() {
    doTest();
  }

  //nothing should be changed from 21
  @Test
  public void testSimplePrimitiveInstanceOf() {
    doTest();
  }

  //nothing should be changed from 21
  @Test
  public void testSimplePrimitiveInstanceOfPattern() {
    doTest();
  }

  //nothing should be changed from 21
  @Test
  public void testSwitchConstantPrimitiveAllowed() { doTest(); }

  //nothing should be changed from 21
  @Test
  public void testSwitchConstantPrimitiveSimilar() { doTest(); }

  //nothing should be changed from 21
  @Test
  public void testSwitchPrimitivePatternApplicable() { doTest(); }

  //nothing should be changed from 21
  @Test
  public void testSwitchPrimitiveCompleteness() { doTest(); }

  //nothing should be changed from 21
  @Test
  public void testSwitchRecordPrimitive() { doTest(); }

  //nothing should be changed from 21
  @Test
  public void testSwitchDominanceIn21Java() { doTest(); }

  @Test
  public void testSwitchConstantExpressionTightenedDominance() { doTest(); }

  @Test
  public void testPrimitiveSwitchValueDominationTightened() { doTest(); }

  @Test
  public void testSwitchPrimitivePatternDominatedTightened() { doTest(); }

  @Test
  public void testSwitchPrimitivePatternListTightened() { doTest(); }

  @Test
  public void testMixFloatingPointWithIntegralLiterals() { doTest(); }

  @Test
  public void testBoxedFloatLiterals() { doTest(); }
}
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

//partially use testdata from LightPrimitivePatternsHighlightingTest
public class LightPrimitivePatternsHighlightingWithTightenedDominanceTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingPatternsWithPrimitives";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    IdeaTestUtil.setProjectLanguageLevel(getProject(), JavaFeature.PATTERNS_WITH_TIGHTENED_DOMINANCE.getMinimumLevel());
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_X;
  }


  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }


  //nothing should be changed from 21
  public void testRecordPrimitiveInstanceOfPattern() {
    doTest();
  }

  //nothing should be changed from 21
  public void testSimplePrimitiveInstanceOf() {
    doTest();
  }

  //nothing should be changed from 21
  public void testSimplePrimitiveInstanceOfPattern() {
    doTest();
  }

  //nothing should be changed from 21
  public void testSwitchConstantPrimitiveAllowed() { doTest(); }

  //nothing should be changed from 21
  public void testSwitchConstantPrimitiveSimilar() { doTest(); }

  //nothing should be changed from 21
  public void testSwitchPrimitivePatternApplicable() { doTest(); }

  //nothing should be changed from 21
  public void testSwitchPrimitiveCompleteness() { doTest(); }

  //nothing should be changed from 21
  public void testSwitchRecordPrimitive() { doTest(); }

  //nothing should be changed from 21
  public void testSwitchDominanceIn21Java() { doTest(); }

  public void testSwitchConstantExpressionTightenedDominance() { doTest(); }

  public void testPrimitiveSwitchValueDominationTightened() { doTest(); }

  public void testSwitchPrimitivePatternDominatedTightened() { doTest(); }

  public void testSwitchPrimitivePatternListTightened() { doTest(); }
}
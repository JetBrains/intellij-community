// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightPrimitivePatternsHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingPatternsWithPrimitives";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_23;
  }

  public void testRecordPrimitiveInstanceOfPattern() {
    doTest();
  }

  public void testSimplePrimitiveInstanceOf() {
    doTest();
  }

  public void testSimplePrimitiveInstanceOfPattern() {
    doTest();
  }

  public void testSwitchConstantPrimitiveAllowed() { doTest(); }

  public void testSwitchConstantPrimitiveSimilar() { doTest(); }

  public void testSwitchPrimitivePatternList() { doTest(); }

  public void testSwitchPrimitivePatternApplicable() { doTest(); }

  public void testSwitchPrimitivePatternDominated() { doTest(); }

  public void testSwitchPrimitiveCompleteness() { doTest(); }

  public void testSwitchRecordPrimitive() { doTest(); }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  public void testSwitchRecordPrimitiveJava25Preview() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_25_PREVIEW, this::doTest);
  }

  public void testSwitchRecordPrimitiveJava25() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_25, this::doTest);
  }
}
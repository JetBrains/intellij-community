// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightPatternsHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingPatterns";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }

  public void testInstanceOfBasics() {
    doTest();
  }
  public void testInstanceOfAnnotations() {
    doTest();
  }
  public void testInstanceOfNameConflicts() {
    doTest();
  }
  public void testInstanceOfControlFlow() {
    doTest();
  }
  public void testInstanceOfInSwitch() {
    doTest();
  }

  public void testInstanceOfInSwitchJava18() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_18, this::doTest);
  }

  public void testReassignPatternVariable() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_16, this::doTest);
  }

  public void testUnusedPatternVariable() {
    myFixture.enableInspections(new UnusedDeclarationInspection());
    doTest();
  }

  public void testDeconstructionInstanceOf() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testInstanceOfNonReified() {
    doTest();
  }

  public void testInstanceOfSubtype() {
    doTest();
  }

  public void testInstanceOfSubtypeJava19() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testInstanceOfPatternMatching() {
    doTest();
  }

  public void testRecordPatternsInForEachJava19() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testRecordPatternsInForEachJava20() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_20_PREVIEW, this::doTest);
  }

  public void testDeconstructionInstanceOf20() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_20_PREVIEW, this::doTest);
  }

  public void testForEachPatternExhaustiveness() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_20_PREVIEW, this::doTest);
  }
  
  public void testBoundTypeParameter() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_20_PREVIEW, this::doTest);
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}
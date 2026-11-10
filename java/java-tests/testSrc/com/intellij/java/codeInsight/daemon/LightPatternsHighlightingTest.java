// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class LightPatternsHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingPatterns");
  }

  @Test
  public void testInstanceOfBasics() {
    doTest();
  }
  @Test
  public void testInstanceOfAnnotations() {
    doTest();
  }
  @Test
  public void testInstanceOfNameConflicts() {
    doTest();
  }
  @Test
  public void testInstanceOfDeconstructionNameConflicts() {
    doTest();
  }
  @Test
  public void testInstanceOfControlFlow() {
    doTest();
  }
  @Test
  public void testInstanceOfInSwitch() {
    doTest();
  }

  @Test
  public void testInstanceOfInSwitchJava18() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_18, this::doTest);
  }

  @Test
  public void testReassignPatternVariable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_16, this::doTest);
  }

  @Test
  public void testUnusedPatternVariable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new UnusedDeclarationInspection());
    doTest();
  }

  @Test
  public void testDeconstructionInstanceOf() {
    doTest();
  }

  @Test
  public void testInstanceOfNonReified() {
    doTest();
  }

  @Test
  public void testInstanceOfSubtype() {
    doTest();
  }

  @Test
  public void testRecordPatternsInForEachJava19() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_19, this::doTest);
  }

  @Test
  public void testRecordPatternsInForEachJava21() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, () -> {
      doTest();
      List<IntentionAction> intentions = myFixture.getAllQuickFixes();
      assertThat(intentions).isNullOrEmpty();
    });
  }

  @Test
  public void testDeconstructionInstanceOf21() {
    doTest();
  }

  @Test
  public void testForEachPatternExhaustiveness() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_X, this::doTest);
  }
  
  @Test
  public void testBoundTypeParameter() {
    doTest();
  }

  @Test
  public void testNotAnnotationsInDeconstructionType() {
    doTest();
  }
  
  @Test
  public void testUnnamedPatterns() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, this::doTest);
  }

  @Test
  public void testUnnamedPatternsJava22() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_22, this::doTest);
  }

  @Test
  public void testUnnamedPatternsUnavailable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_20, this::doTest);
  }

  @Test
  public void testInstanceofPrimitivesNotAllowed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_22, this::doTest);
  }

  @Test
  public void testRecordPrimitiveInstanceOfPatternNotAllowed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_22, this::doTest);
  }

  @Test
  public void testBrokenRecordNumber() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_22, this::doTest);
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}
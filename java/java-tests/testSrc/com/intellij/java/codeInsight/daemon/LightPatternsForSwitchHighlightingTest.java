// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlightingComputer;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class LightPatternsForSwitchHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingPatternsInSwitch");
    IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), LanguageLevel.JDK_21);
  }

  @Test
  public void testPatternsInSwitchIn16Java() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_16, this::doTest);
  }

  @Test
  public void testPatternsInSwitchIn11Java() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_11, this::doTest);
  }

  @Test
  public void testPatternsInSwitchIn21Java() {
    doTest();
  }

  @Test
  public void testMismatchedDeconstructionIn21Java() {
    doTest();
  }

  @Test
  public void testIllegalFallthroughIn20Java() {
    doTest();
  }
  
  @Test
  public void testIllegalFallthroughIn21Java() {
    doTest();
  }

  @Test
  public void testUnconditionalDestructuringAndDefaultIn21Java() {
    doTest();
  }

  @Test
  public void testSwitchExhaustivenessIn21Java() {
    doTest();
  }
  @Test
  public void testSwitchExhaustivenessIn21JavaInfiniteRecursion() {
    doTest();
  }

  @Test
  public void testSwitchExhaustivenessForDirectClassesIn21Java() {
    doTest();
  }

  @Test
  public void testSwitchExhaustivenessWithConcreteSealedClassesIn21Java() {
    doTest();
  }

  @Test
  public void testSwitchExhaustivenessForEnumsWithSealedClassesIn21Java() {
    doTest();
  }

  @Test
  public void testSwitchExhaustivenessWithSealedIntersection(){
    doTest();
  }

  @Test
  public void testSwitchExhaustivenessWithGenericsIn21Java() {
    doTest();
  }

  @Test
  public void testSwitchExhaustivenessWithRecursiveGenerics() {
    doTest();
  }
  
  @Test
  public void testSwitchSeveralPatternsUnnamed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, this::doTest);
  }

  @Test
  public void testSwitchDominanceIn21Java() {
    doTest();
  }

  @Test
  public void testPatternMatchingInSwitchJava21() {
    doTest();
  }

  @Test
  public void testPatternMatchingWithGuard() {
    doTest();
  }

  @Test
  public void testPatternMatchingScope() {
    doTest();
  }

  @Test
  public void testPatternMatchingInSwitchWithIfPatternMatching() {
    doTest();
  }

  @Test
  public void testReachability() {
    doTest();
  }

  @Test
  public void testReachabilityEnhancedPrimitives() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(), () -> doTest());
  }

  @Test
  public void testReachabilityStatement() {
    doTest();
  }

  @Test
  public void testEffectivelyFinal21() {
    doTest();
  }

  @Test
  public void testSameVariableNameInPatternMatchingInSwitch() {
    doTest();
  }

  @Test
  public void testFallthroughPatternMatchingSwitch() {
    doTest();
  }

  @Test
  public void testWhenExpressions() {
    doTest();
  }

  @Test
  public void testSwitchExprHasResult() {
    doTest();
  }

  @Test
  public void testHighlighterForPatternVariableInSwitch() {
    testIdentifierHighlighter(3);
  }

  @Test
  public void testHighlighterForPatternVariableInIf() {
    testIdentifierHighlighter(2);
  }

  @Test
  public void testHighlighterForPatternVariableInIfElse() {
    testIdentifierHighlighter(4);
  }

  @Test
  public void testHighlighterForPatternVariableInLocalVariable() {
    testIdentifierHighlighter(2);
  }

  @Test
  public void testGuardWithInstanceOfPatternMatching() {
    doTest();
  }

  @Test
  public void testMultipleReferencesToPatternVariable() {
    doTest();
  }

  @Test
  public void testBreakAndOtherStopWords() {
    doTest();
  }

  @Test
  public void testFallthroughDefault() {
    doTest();
  }

  @Test
  public void testUnusedPatternVariable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new UnusedDeclarationInspection());
    doTest();
    assertNotNull(myFixture.getAvailableIntention("Rename 's' to '_'"));
  }

  @Test
  public void testMalformedReferenceExpression() {
    doTest();
  }

  @Test
  public void testBrokenSealedHierarchy() {
    doTest();
  }

  @Test
  public void testRecordPatternsAndWhenGuardsInJava18() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_18, this::doTest);
  }

  @Test
  public void testRecordPatternsAndWhenGuardsInJava21() {
    doTest();
  }

  @Test
  public void testWhenExpressionIsFalse() {
    doTest();
  }
  @Test
  public void testNullSelectorType() {
    doTest();
  }

  @Test
  public void testSealedWithLocalAndAnonymousClasses() {
    doTest();
  }

  @Test
  public void testDoubleInnerPermitClass() {
    doTest();
  }

  @Test
  public void testSwitchWithPrimitivesNotAllowed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_22, this::doTest);
  }

  @Test
  public void testSwitchRecordPrimitiveIsNotAllowed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_22, this::doTest);
  }

  @Test
  public void testUnconditionalForSelectTypeAndDominated() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, this::doTest);
  }

  @Test
  public void testExhaustivenessWithIntersectionType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_22, this::doTest);
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  private void testIdentifierHighlighter(int expectedUsages) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      PsiElement element = myFixture.getElementAtCaret();
      assertThat(IdentifierHighlightingComputer.getUsages(element, file, true)).hasSize(expectedUsages);
    });
  }
}

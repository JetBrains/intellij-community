// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlightingComputer;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightPatternsForSwitchHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingPatternsInSwitch";
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_21);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  public void testPatternsInSwitchIn16Java() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_16, this::doTest);
  }

  public void testPatternsInSwitchIn11Java() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_11, this::doTest);
  }

  public void testPatternsInSwitchIn21Java() {
    doTest();
  }

  public void testMismatchedDeconstructionIn21Java() {
    doTest();
  }

  public void testIllegalFallthroughIn20Java() {
    doTest();
  }
  
  public void testIllegalFallthroughIn21Java() {
    doTest();
  }

  public void testUnconditionalDestructuringAndDefaultIn21Java() {
    doTest();
  }

  public void testSwitchExhaustivenessIn21Java() {
    doTest();
  }
  public void testSwitchExhaustivenessIn21JavaInfiniteRecursion() {
    doTest();
  }

  public void testSwitchExhaustivenessForDirectClassesIn21Java() {
    doTest();
  }

  public void testSwitchExhaustivenessWithConcreteSealedClassesIn21Java() {
    doTest();
  }

  public void testSwitchExhaustivenessForEnumsWithSealedClassesIn21Java() {
    doTest();
  }

  public void testSwitchExhaustivenessWithSealedIntersection(){
    doTest();
  }

  public void testSwitchExhaustivenessWithGenericsIn21Java() {
    doTest();
  }
  
  public void testSwitchSeveralPatternsUnnamed() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, this::doTest);
  }

  public void testSwitchDominanceIn21Java() {
    doTest();
  }

  public void testPatternMatchingInSwitchJava21() {
    doTest();
  }

  public void testPatternMatchingWithGuard() {
    doTest();
  }

  public void testPatternMatchingScope() {
    doTest();
  }

  public void testPatternMatchingInSwitchWithIfPatternMatching() {
    doTest();
  }

  public void testReachability() {
    doTest();
  }
  public void testReachabilityStatement() {
    doTest();
  }

  public void testEffectivelyFinal21() {
    doTest();
  }


  public void testSameVariableNameInPatternMatchingInSwitch() {
    doTest();
  }

  public void testFallthroughPatternMatchingSwitch() {
    doTest();
  }

  public void testWhenExpressions() {
    doTest();
  }

  public void testSwitchExprHasResult() {
    doTest();
  }

  public void testHighlighterForPatternVariableInSwitch() {
    testIdentifierHighlighter(3);
  }

  public void testHighlighterForPatternVariableInIf() {
    testIdentifierHighlighter(2);
  }

  public void testHighlighterForPatternVariableInIfElse() {
    testIdentifierHighlighter(4);
  }

  public void testHighlighterForPatternVariableInLocalVariable() {
    testIdentifierHighlighter(2);
  }

  public void testGuardWithInstanceOfPatternMatching() {
    doTest();
  }

  public void testMultipleReferencesToPatternVariable() {
    doTest();
  }

  public void testBreakAndOtherStopWords() {
    doTest();
  }

  public void testFallthroughDefault() {
    doTest();
  }

  public void testUnusedPatternVariable() {
    myFixture.enableInspections(new UnusedDeclarationInspection());
    doTest();
    assertNotNull(myFixture.getAvailableIntention("Rename 's' to '_'"));
  }

  public void testMalformedReferenceExpression() {
    doTest();
  }

  public void testBrokenSealedHierarchy() {
    doTest();
  }

  public void testRecordPatternsAndWhenGuardsInJava18() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_18, this::doTest);
  }

  public void testRecordPatternsAndWhenGuardsInJava21() {
    doTest();
  }

  public void testWhenExpressionIsFalse() {
    doTest();
  }
  public void testNullSelectorType() {
    doTest();
  }

  public void testSealedWithLocalAndAnonymousClasses() {
    doTest();
  }

  public void testDoubleInnerPermitClass() {
    doTest();
  }

  public void testSwitchWithPrimitivesNotAllowed() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_22, this::doTest);
  }

  public void testSwitchRecordPrimitiveIsNotAllowed() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_22, this::doTest);
  }

  public void testUnconditionalForSelectTypeAndDominated() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, this::doTest);
  }

  public void testExhaustivenessWithIntersectionType() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_22, this::doTest);
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  private void testIdentifierHighlighter(int expectedUsages) {
    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");
    PsiElement element = myFixture.getElementAtCaret();
    assertSize(expectedUsages, IdentifierHighlightingComputer.getUsages(element, file, true));
  }
}

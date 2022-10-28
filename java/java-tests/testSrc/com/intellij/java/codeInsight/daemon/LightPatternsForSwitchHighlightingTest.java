// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass;
import com.intellij.codeInsight.intention.IntentionAction;
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

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }

  public void testPatternsInSwitchIn16Java() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_16, this::doTest);
  }

  public void testPatternsInSwitchIn11Java() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_11, this::doTest);
  }

  public void testPatternsInSwitchIn19Java() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testMismatchedDeconstructionIn19Java() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testIllegalFallthroughIn19Java() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testUnconditionalDestructuringAndDefaultIn19Java() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testSwitchExhaustivenessIn19Java() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testSwitchExhaustivenessWithSealedIntersection(){
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testSwitchExhaustivenessWithGenericsIn19Java() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testSwitchDominanceIn19Java() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testPatternMatchingInSwitch() {
    doTest();
    IntentionAction action = myFixture.getAvailableIntention("Remove 'default' branch");
    assertNotNull(action);
  }

  public void testPatternMatchingInSwitchJava18() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_18_PREVIEW, this::doTest);
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

  public void testEffectivelyFinal() {
    doTest();
  }

  public void testEffectivelyFinalWhen() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testSameVariableNameInPatternMatchingInSwitch() {
    doTest();
  }

  public void testFallthroughPatternMatchingSwitch() {
    doTest();
  }

  public void testGuardedPatterns() {
    doTest();
  }

  public void testWhenExpressions() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
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
    assertNotNull(myFixture.getAvailableIntention("Rename 's' to 'ignored'"));
  }

  public void testMalformedReferenceExpression() {
    doTest();
  }

  public void testBrokenSealedHierarchy() {
    doTest();
  }

  public void testRecordPatternsAndWhenGuardsInJava18() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_18_PREVIEW, this::doTest);
  }

  public void testRecordPatternsAndWhenGuardsInJava19() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testWhenExpressionIsFalse() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }

  private void testIdentifierHighlighter(int expectedUsages) {
    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");
    PsiElement element = myFixture.getElementAtCaret();
    assertSize(expectedUsages, IdentifierHighlighterPass.getUsages(element, file, true));
  }
}

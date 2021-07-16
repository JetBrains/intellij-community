// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass;
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

  public void testPatternsInSwitchInOldJava() {
      IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_16, this::doTest);
  }

  public void testPatternMatchingInSwitch() {
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

  public void testSameVariableNameInPatternMatchingInSwitch() {
    doTest();
  }

  public void testIdentifierHighlighterForPatternVariable() {
    PsiFile file = myFixture.configureByFile(getTestName(false) + ".java");
    PsiElement element = myFixture.getElementAtCaret();
    assertSize(2, IdentifierHighlighterPass.getUsages(element, file, true));
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}

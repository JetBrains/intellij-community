// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
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
    return JAVA_14;
  }

  public void testInstanceOfBasics() {
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
  public void testReassignPatternVariable() {
    doTest();
  }
  public void testUnusedPatternVariable() {
    myFixture.enableInspections(new UnusedDeclarationInspection());
    doTest();
  }
  public void testInstanceOfNonReified() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}
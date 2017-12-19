/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.SuspiciousChainOperationsInspection;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;


public class SuspiciousChainOperationsInspectionTest extends LightCodeInsightFixtureTestCase {
  public void testSuspiciousChainOperations() { doTest(); }

  private void doTest() {
    myFixture.enableInspections(new SuspiciousChainOperationsInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath()+"/inspection/oddBinaryOperation";
  }
}
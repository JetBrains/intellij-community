// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.IdempotentLoopBodyInspection;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;


public class IdempotentLoopBodyInspectionTest extends LightCodeInsightFixtureTestCase {
  public void testIdempotentLoopBody() { doTest(); }

  private void doTest() {
    myFixture.enableInspections(new IdempotentLoopBodyInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath()+"/inspection/idempotentLoopBody";
  }
}
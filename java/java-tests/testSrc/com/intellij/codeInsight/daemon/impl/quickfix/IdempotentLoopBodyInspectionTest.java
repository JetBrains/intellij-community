// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.IdempotentLoopBodyInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;


public class IdempotentLoopBodyInspectionTest extends LightJavaCodeInsightFixtureTestCase {
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
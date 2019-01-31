// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.varScopeCanBeNarrowed.ParameterCanBeLocalInspection;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class ParameterCanBeLocalTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/parameterCanBeLocal";
  }

  private void doTest() {
    myFixture.enableInspections(new ParameterCanBeLocalInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testFor() {
    doTest();
  }

  public void testIf() {
    doTest();
  }

  public void testReadOnly() {
    doTest();
  }

  public void testSimple() {
    doTest();
  }

}
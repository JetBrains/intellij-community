// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class SillyAssignmentTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/sillyAssignment";
  }

  private void doTest() {
    myFixture.enableInspections(new SillyAssignmentInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testSuppressed() {
    doTest();
  }

  public void testMultiple() {
    doTest();
  }

}
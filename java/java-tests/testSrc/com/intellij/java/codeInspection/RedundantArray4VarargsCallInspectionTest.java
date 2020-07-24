// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * @author anna
 */
public class RedundantArray4VarargsCallInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new RedundantArrayForVarargsCallInspection());
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantArrayForVarargs/quickFix";
  }

  public void testPreserveComments() { doTest(); }
  public void testRemoveTailingCommas() { doTest(); }
  public void testParentheses() { doTest(); }
  public void testInsertSeparatingComma() { doTest(); }

  private void doTest() {
    String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    myFixture.launchAction(myFixture.findSingleIntention(JavaBundle.message("inspection.redundant.array.creation.quickfix")));
    myFixture.checkResultByFile(name + "_after.java");
  }
}
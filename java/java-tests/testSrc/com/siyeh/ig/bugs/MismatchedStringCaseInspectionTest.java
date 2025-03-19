// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class MismatchedStringCaseInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH +
           "com/siyeh/igtest/bugs/case_mismatch";
  }

  private void doTest() {
    myFixture.enableInspections(new MismatchedStringCaseInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testMismatchedStringCase() {
    doTest();
  }
  public void testMismatchedStringCaseSwitch() {
    doTest();
  }
}
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class MismatchedStringCaseInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/bugs/case_mismatch";
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
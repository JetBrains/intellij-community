// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class NonStrictComparisonCanBeEqualityInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/controlflow/non_strict_comparison";
  }

  private void doTest() {
    myFixture.enableInspections(new NonStrictComparisonCanBeEqualityInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testSimple() {
    doTest();
  }

}

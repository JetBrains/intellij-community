// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class IfStatementWithIdenticalBranchesInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/controlflow/if_statement_with_identical_branches";
  }

  private void doTest() {
    myFixture.enableInspections(new IfStatementWithIdenticalBranchesInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testIfStatementWithIdenticalBranches() {
    doTest();
  }
}

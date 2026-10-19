// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class UnnecessaryLabelOnContinueInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/controlflow/unnecessary_label_on_continue";
  }

  private void doTest() {
    myFixture.enableInspections(new UnnecessaryLabelOnContinueStatementInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testUnnecessaryLabel() {
    doTest();
  }

}

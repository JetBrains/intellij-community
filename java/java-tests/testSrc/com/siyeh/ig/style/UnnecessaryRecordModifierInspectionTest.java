// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class UnnecessaryRecordModifierInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/style/unnecessary_record_modifier";
  }
  
  private void doTest() {
    myFixture.enableInspections(new UnnecessaryModifierInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testUnnecessaryModifier() {
    doTest();
  }
}

// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class NonFinalFieldInEnumInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/classlayout/non_final_field_in_enum";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest(boolean fixOnly) {
    NonFinalFieldInEnumInspection inspection = new NonFinalFieldInEnumInspection();
    inspection.onlyWarnWhenQuickFix = fixOnly;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testNonFinalFieldInEnum() {
    doTest(false);
  }

  public void testNonFinalFieldInEnumFixOnly() {
    doTest(true);
  }
}
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class AssertWithSideEffectsInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH +
           "com/siyeh/igtest/bugs/assert_with_side_effects";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    // uses SQL
    return JAVA_1_7_ANNOTATED;
  }

  private void doTest() {
    myFixture.enableInspections(new AssertWithSideEffectsInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testAssertWithSideEffects() {
    doTest();
  }
}
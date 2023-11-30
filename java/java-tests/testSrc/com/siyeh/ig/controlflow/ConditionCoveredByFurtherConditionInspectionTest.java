// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.ExpectedHighlightingData;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ConditionCoveredByFurtherConditionInspectionTest extends LightJavaInspectionTestCase {

  public void testConditionCoveredByFurtherCondition() {
    ExpectedHighlightingData.expectedDuplicatedHighlighting(this::doTest);
  }
  public void testMultiCatch() {
    doTest();
  }
  public void testExpressionSwitch() {
    doTest();
  }
  public void testStringLength() {
    doTest();
  }
  public void testInInjection() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ConditionCoveredByFurtherConditionInspection();
  }
}

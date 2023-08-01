// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.text.StringUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ConstantOnWrongSideOfComparisonInspectionTest extends LightJavaInspectionTestCase {

  public void testConstantOnLHS() {
    doTest();
  }

  public void testConstantOnRHS() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    ConstantOnWrongSideOfComparisonInspection inspection = new ConstantOnWrongSideOfComparisonInspection();
    final boolean constantShouldGoLeft = StringUtil.containsIgnoreCase(getTestName(false), "rhs");
    inspection.myConstantShouldGoLeft = constantShouldGoLeft;
    inspection.myIgnoreNull = constantShouldGoLeft;
    return inspection;
  }
}
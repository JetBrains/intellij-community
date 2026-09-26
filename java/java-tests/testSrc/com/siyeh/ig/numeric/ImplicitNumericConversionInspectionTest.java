// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ImplicitNumericConversionInspectionTest extends LightJavaInspectionTestCase {

  public void testImplicitNumericConversion() {
    doTest();
  }

  public void testIgnoreCharConversion() {
    final ImplicitNumericConversionInspection inspection = new ImplicitNumericConversionInspection();
    inspection.ignoreCharConversions = true;
    myFixture.enableInspections(inspection);
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("implicit.numeric.conversion.make.explicit.quickfix"));
  }

  public void testIgnoreWidening() {
    final ImplicitNumericConversionInspection inspection = new ImplicitNumericConversionInspection();
    inspection.ignoreWideningConversions = true;
    inspection.ignoreConstantConversions = true;
    myFixture.enableInspections(inspection);
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("implicit.numeric.conversion.make.explicit.quickfix"));
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ImplicitNumericConversionInspection();
  }
}
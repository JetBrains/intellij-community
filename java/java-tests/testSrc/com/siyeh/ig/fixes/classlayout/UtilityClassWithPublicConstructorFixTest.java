// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.classlayout;

import com.intellij.codeInspection.InspectionsBundle;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.classlayout.UtilityClassWithPublicConstructorInspection;

public class UtilityClassWithPublicConstructorFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final UtilityClassWithPublicConstructorInspection inspection = new UtilityClassWithPublicConstructorInspection();
    myFixture.enableInspections(inspection);
    myRelativePath = "classlayout/utility_class_with_public_constructor";
    myDefaultHint =
      InspectionsBundle.message("fix.all.inspection.problems.in.file",
                                InspectionGadgetsBundle.message("utility.class.with.public.constructor.display.name"));
  }

  public void testClassWithInheritor() {
    doTest();
  }
  public void testPublicClass() {
    doTest();
  }
}

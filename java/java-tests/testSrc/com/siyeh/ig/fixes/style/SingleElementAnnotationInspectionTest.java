// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.annotation.SingleElementAnnotationInspection;

public class SingleElementAnnotationInspectionTest extends IGQuickFixesTestCase {

  public void testOneAttr() {
    doTest();
  }

  public void testMultiAttr() {
    doTest();
  }

  public void testAlreadyHasName() {
    assertQuickfixNotAvailable();
  }

  public void testAnnotationAttr() {
    doTest();
  }

  public void testArrayAttr() {
    doTest();
  }

  public void testArrayItemAttr() {
    doTest();
  }

  public void testIncompatibleType() {
    assertQuickfixNotAvailable();
  }

  public void testIncompatibleArrayItemType() {
    assertQuickfixNotAvailable();
  }

  public void testNoValueAttr() {
    assertQuickfixNotAvailable();
  }

  public void testNameAlreadyUsed() {
    assertQuickfixNotAvailable();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SingleElementAnnotationInspection());
    myDefaultHint = InspectionGadgetsBundle.message("single.element.annotation.quickfix");
    myRelativePath = "style/expand_annotation";
  }
}

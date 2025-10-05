// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.migration;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.migration.MethodCanBeVariableArityMethodInspection;

/**
 * @author Bas Leijdekkers
 */
public class ConvertToVarargsMethodFixTest extends IGQuickFixesTestCase {

  public void testDeepArray() { doTest(); }
  public void testComment() { doTest(); }
  public void testFinal() { doTest(); }
  public void testCStyle() { doTest(); }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new MethodCanBeVariableArityMethodInspection());
    myRelativePath = "migration/convert_to_varargs_method";
    myDefaultHint = InspectionGadgetsBundle.message("convert.to.variable.arity.method.quickfix");
  }
}

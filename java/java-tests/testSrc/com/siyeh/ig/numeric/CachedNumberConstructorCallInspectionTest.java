// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class CachedNumberConstructorCallInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    final CachedNumberConstructorCallInspection inspection = new CachedNumberConstructorCallInspection();
    inspection.reportOnlyWhenDeprecated = false;
    return inspection;
  }

  public void testSimple() { doStatementTest("new /*Number constructor call with primitive argument*/Integer/**/(1);"); }
  public void testStringArgument() { doStatementTest("new /*Number constructor call with primitive argument*/Byte/**/(\"1\");"); }
  public void testNoWarn() { doStatementTest("Long.valueOf(1L);"); }
  public void testNoAssertionError() { doStatementTest("Integer i = new /*!Cannot inherit from final class 'java.lang.Integer'*/Integer/*!*/(new String/*!'(' or '[' expected*//*!',' or ')' expected*/{/*!*//*!*/}/*!';' expected*//*!Unexpected token*/)/*!*//*!*/;"); }
}

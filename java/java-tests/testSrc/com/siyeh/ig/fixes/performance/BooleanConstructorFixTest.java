/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.fixes.performance;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.BooleanConstructorInspection;

/**
 * @author Bas Leijdekkers
 */
public class BooleanConstructorFixTest extends IGQuickFixesTestCase {

  @Override
  protected BaseInspection getInspection() {
    return new BooleanConstructorInspection();
  }

  public void testSimple() {
    doExpressionTest(InspectionGadgetsBundle.message("boolean.constructor.simplify.quickfix"),
                     "new Boolean/**/(true)", "Boolean.TRUE");
  }

  public void testSimple2() {
    doMemberTest(InspectionGadgetsBundle.message("boolean.constructor.simplify.quickfix"),
                 "void m(boolean b) { Boolean c = new /**/Boolean(b); }",
                 "void m(boolean b) { Boolean c = Boolean.valueOf(b); }");
  }
}

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

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.BooleanConstructorInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class BooleanConstructorFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new BooleanConstructorInspection()));

  @Test
  public void testSimple() {
    ig.get().doExpressionTest(InspectionGadgetsBundle.message("boolean.constructor.simplify.quickfix"),
                     "new Boolean/**/(true)", "Boolean.TRUE");
  }

  @Test
  public void testSimple2() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("boolean.constructor.simplify.quickfix"),
                 "void m(boolean b) { Boolean c = new /**/Boolean(b); }",
                 "void m(boolean b) { Boolean c = Boolean.valueOf(b); }");
  }
}

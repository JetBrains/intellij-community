/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.siyeh.ig.fixes;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.ConditionCoveredByFurtherConditionInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class RemoveRedundantPolyadicOperandFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ConditionCoveredByFurtherConditionInspection()), "pointlessnullcheck");

  @Test
  public void testScenario1() {
    ig.get().doTest(InspectionGadgetsBundle.message("remove.redundant.polyadic.operand.fix.name", "arg != null"));
  }

  @Test
  public void testPolyadicExpression() {
    ig.get().doTest(InspectionGadgetsBundle.message("remove.redundant.polyadic.operand.fix.name", "arg != null"));
  }

  @Test
  public void testPolyadicExpression2() {
    ig.get().doTest(InspectionGadgetsBundle.message("remove.redundant.polyadic.operand.fix.name", "arg != null"));
  }
}

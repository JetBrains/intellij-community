/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.ToArrayCallWithZeroLengthArrayArgumentInspection;
import com.siyeh.ig.performance.ToArrayCallWithZeroLengthArrayArgumentInspection.PreferEmptyArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class ToArrayCallWithZeroLengthArrayArgumentFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(), "performance/to_array_call_with_zero_length_array_argument");

  private final ToArrayCallWithZeroLengthArrayArgumentInspection myInspection = new ToArrayCallWithZeroLengthArrayArgumentInspection();

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    myFixture.enableInspections(myInspection);
  }

  @Test
  public void testIntroduceVariable() {
    doFixTest(PreferEmptyArray.NEVER, InspectionGadgetsBundle.message("to.array.call.style.quickfix.make.presized"));
  }

  @Test
  public void testPresizedToZero() {
    doFixTest(PreferEmptyArray.ALWAYS, InspectionGadgetsBundle.message("to.array.call.style.quickfix.make.zero"));
  }

  @Test
  public void testPresizedToZeroMap() {
    doFixTest(PreferEmptyArray.ALWAYS, InspectionGadgetsBundle.message("to.array.call.style.quickfix.make.zero"));
  }

  private void doFixTest(PreferEmptyArray mode, String message) {
    myInspection.myMode = mode;
    ig.get().doTest(ig.get().getTestName(false), message);
  }

}
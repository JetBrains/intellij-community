/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInspection;

import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.function.Consumer;

public abstract class DataFlowInspectionTestCase extends LightJavaCodeInsightFixtureTestCase {
  protected void doTest() {
    doTestWith(i -> {
      i.SUGGEST_NULLABLE_ANNOTATIONS = true;
      i.REPORT_CONSTANT_REFERENCE_VALUES = false;
    });
  }

  protected void doTestWith(Consumer<? super DataFlowInspection> inspectionMutator) {
    DataFlowInspection inspection = new DataFlowInspection();
    inspectionMutator.accept(inspection);
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void assertIntentionAvailable(String intentionName) {
    assertTrue(myFixture.getAvailableIntentions().stream().anyMatch(action -> action.getText().equals(intentionName)));
  }
}
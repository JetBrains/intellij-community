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

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.duplicateThrows.DuplicateThrowsInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class DuplicateThrowsInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/duplicateThrows";
  }

  private void doTest() {
    myFixture.enableInspections(new DuplicateThrowsInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testDuplicateThrows() {
    doTest();
  }
}

/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.defaultFileTemplateUsage.DefaultFileTemplateUsageInspection;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class DefaultFileTemplateUsageInspectionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/defaultFileTemplateUsage";
  }

  public void testX() { doTest(); }
  public void testX2() { doTest(); }
  public void testX3() { doTest(); }
  public void testRange() { doTest(); }

  private void doTest() {
    myFixture.enableInspections(new DefaultFileTemplateUsageInspection());
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }
}

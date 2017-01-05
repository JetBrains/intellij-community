/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.testFramework.InspectionTestCase;

public class SameParameterValueLocalTest extends InspectionTestCase {
  private final LocalInspectionTool myTool = new SameParameterValueInspection().getSharedLocalInspectionTool();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private String getGlobalTestDir() {
    return "sameParameterValue/" + getTestName(true);
  }

  public void testEntryPoint() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testMethodWithSuper() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testVarargs() {
    doTest(getGlobalTestDir(), myTool);
  }
}

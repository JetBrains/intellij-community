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
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValueLocalInspection;
import com.intellij.testFramework.InspectionTestCase;

public class UnusedReturnValueLocalTest extends InspectionTestCase {
  private final UnusedReturnValue myGlobal = new UnusedReturnValue();
  private final UnusedReturnValueLocalInspection myTool = new UnusedReturnValueLocalInspection(myGlobal);

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("unusedReturnValue/" + getTestName(true), myTool);
  }


  public void testNonLiteral() throws Exception {
    doTest();
  }

  public void testHierarchy() throws Exception {
    doTest();
  }

  public void testMethodReference() throws Exception {
    doTest();
  }

  public void testSimpleSetter() throws Exception {
    try {
      myGlobal.IGNORE_BUILDER_PATTERN = true;
      doTest();
    }
    finally {
      myGlobal.IGNORE_BUILDER_PATTERN = false;
    }
  }
}

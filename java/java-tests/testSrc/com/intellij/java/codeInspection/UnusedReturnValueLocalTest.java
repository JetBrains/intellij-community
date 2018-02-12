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
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValueLocalInspection;
import com.intellij.testFramework.InspectionTestCase;

public class UnusedReturnValueLocalTest extends InspectionTestCase {
  private UnusedReturnValue myGlobal = new UnusedReturnValue();
  private UnusedReturnValueLocalInspection myTool = new UnusedReturnValueLocalInspection(myGlobal);

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    doTest("unusedReturnValue/" + getTestName(true), myTool);
  }


  @Override
  protected void tearDown() throws Exception {
    myGlobal = null;
    myTool = null;
    super.tearDown();
  }

  public void testNonLiteral() {
    doTest();
  }

  public void testHierarchy() {
    doTest();
  }

  public void testMethodReference() {
    doTest();
  }

  public void testSimpleSetter() {
    try {
      myGlobal.IGNORE_BUILDER_PATTERN = true;
      doTest();
    }
    finally {
      myGlobal.IGNORE_BUILDER_PATTERN = false;
    }
  }
}

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
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.psi.PsiModifier;
import com.intellij.testFramework.JavaInspectionTestCase;

public class SameParameterValueTest extends JavaInspectionTestCase {
  private SameParameterValueInspection myTool = new SameParameterValueInspection();

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private String getTestDir() {
    return "sameParameterValue/" + getTestName(true);
  }

  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }

  public void testEntryPoint() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testWithoutDeadCode() {
    String previous = myTool.highestModifier;
    myTool.highestModifier = PsiModifier.PUBLIC;
    try {
      doTest(getTestDir(), myTool, false, false);
    } finally {
      myTool.highestModifier = previous;
    }
  }

  public void testVarargs() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testSimpleVararg() {
    boolean previous = myTool.ignoreWhenRefactoringIsComplicated;
    try {
      myTool.ignoreWhenRefactoringIsComplicated = false;
      doTest(getTestDir(), myTool, false, true);
    } finally {
      myTool.ignoreWhenRefactoringIsComplicated = previous;
    }
  }
  
  public void testMethodWithSuper() {
    String previous = myTool.highestModifier;
    myTool.highestModifier = PsiModifier.PUBLIC;
    try {
      doTest(getTestDir(), myTool, false, true);
    } finally {
      myTool.highestModifier = previous;
    }
  }

  public void testNotReportedDueToHighVisibility() {
    doTest(getTestDir(), myTool, false, false);
  }

  public void testNegativeDouble() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testClassObject() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testUsageCount() {
    int previous = myTool.minimalUsageCount;
    try {
      myTool.minimalUsageCount = 5;
      doTest(getTestDir(), myTool, false, true);
    }
    finally {
      myTool.minimalUsageCount = previous;
    }
  }

  public void testField() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testOverrideGroovy() {
    doTest(getTestDir(), myTool, false, true);
  }

  public void testMethodReferences() {
    doTest(getTestDir(), myTool, false, true);
  }
}

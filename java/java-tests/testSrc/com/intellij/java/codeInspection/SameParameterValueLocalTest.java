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
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.psi.PsiModifier;
import com.intellij.testFramework.JavaInspectionTestCase;

public class SameParameterValueLocalTest extends JavaInspectionTestCase {
  private final SameParameterValueInspection myGlobalTool = new SameParameterValueInspection();
  private LocalInspectionTool myTool = myGlobalTool.getSharedLocalInspectionTool();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    //myGlobalTool.highestModifier = PsiModifier.PUBLIC;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private String getGlobalTestDir() {
    return "sameParameterValue/" + getTestName(true);
  }

  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }

  public void testEntryPoint() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testMethodWithSuper() {
    String previous = myGlobalTool.highestModifier;
    myGlobalTool.highestModifier = PsiModifier.PUBLIC;
    try {
      doTest(getGlobalTestDir(), myTool);
    } finally {
      myGlobalTool.highestModifier = previous;
    }
  }

  public void testNotReportedDueToHighVisibility() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testVarargs() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testNativeMethod() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testNegativeDouble() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testMethodReferenceInCallArguments() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testFixAvailable() { doTest(getGlobalTestDir(), myTool); }

  public void testFixNotAvailable() { doTest(getGlobalTestDir(), myTool); }

  public void testFixNotAvailableIsShown() {
    boolean previous = myGlobalTool.ignoreWhenRefactoringIsComplicated;
    try {
      myGlobalTool.ignoreWhenRefactoringIsComplicated = false;
      doTest(getGlobalTestDir(), myTool);
    } finally {
      myGlobalTool.ignoreWhenRefactoringIsComplicated = previous;
    }
  }

  public void testUsageCount() {
    int previous = myGlobalTool.minimalUsageCount;
    try {
      myGlobalTool.minimalUsageCount = 5;
      doTest(getGlobalTestDir(), myTool);
    }
    finally {
      myGlobalTool.minimalUsageCount = previous;
    }
  }

  public void testOverrideGroovy() {
    doTest(getGlobalTestDir(), myTool);
  }

  public void testMethodReferences() {
    doTest(getGlobalTestDir(), myTool);
  }
}

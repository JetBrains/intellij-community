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
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.testFramework.InspectionTestCase;

public class UnusedMethodParameterTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    UnusedDeclarationInspection declarationInspection = new UnusedDeclarationInspection();
    declarationInspection.getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    doTest("unusedMethodParameter/" + getTestName(true), declarationInspection);
  }

  public void testFieldInAnonymousClass() {
    doTest();
  }

  public void testUnusedParameter() {
    doTest();
  }

  public void testUsedForReading() {
    doTest();
  }

  public void testSuppressedParameter() {
    doTest();
  }

  public void testEntryPointUnusedParameter() {
    doTest("unusedMethodParameter/" + getTestName(true), new UnusedDeclarationInspection(), true, true);
  }

  public void testAppMainUnusedParams() {
    doTest();
  }
}

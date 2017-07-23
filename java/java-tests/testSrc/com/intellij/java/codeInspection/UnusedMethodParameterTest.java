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

  private void doTest() throws Exception {
    UnusedDeclarationInspection declarationInspection = new UnusedDeclarationInspection();
    declarationInspection.getSharedLocalInspectionTool().LOCAL_VARIABLE = false;
    doTest("unusedMethodParameter/" + getTestName(true), declarationInspection);
  }

  public void testFieldInAnonymousClass() throws Exception {
    doTest();
  }

  public void testUnusedParameter() throws Exception {
    doTest();
  }

  public void testUsedForReading() throws Exception {
    doTest();
  }

  public void testSuppressedParameter() throws Exception {
    doTest();
  }

  public void testEntryPointUnusedParameter() throws Exception {
    doTest("unusedMethodParameter/" + getTestName(true), new UnusedDeclarationInspection(), true, true);
  }

  public void testAppMainUnusedParams() throws Exception {
    doTest();
  }
}

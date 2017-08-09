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
import com.intellij.codeInspection.unneededThrows.RedundantThrowsDeclarationInspection;
import com.intellij.testFramework.InspectionTestCase;

public class RedundantThrowTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(boolean checkRange) throws Exception {
    final RedundantThrowsDeclarationInspection tool = new RedundantThrowsDeclarationInspection();
    doTest("redundantThrow/" + getTestName(false), tool, checkRange);
  }

  public void testSCR8322() throws Exception { doTest(); }

  public void testSCR6858() throws Exception { doTest(); }

  public void testSCR6858ByRange() throws Exception { doTest(true); }

  public void testSCR14543() throws Exception { doTest(); }

  public void testRemote() throws Exception { doTest(); }

  public void testEntryPoint() throws Exception {
    final RedundantThrowsDeclarationInspection tool = new RedundantThrowsDeclarationInspection();
    doTest("redundantThrow/" + getTestName(true), tool, false, true);
  }

  public void testInherited() throws Exception {
    doTest();
  }

  public void testImplicitSuper() throws Exception {
    doTest();
  }

  public void testSelfCall() throws Exception {
    doTest();
  }

  public void testThrownClausesInFunctionalExpressions() throws Exception {
    doTest();
  }
}

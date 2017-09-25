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
import com.intellij.codeInspection.emptyMethod.EmptyMethodInspection;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author max
 */
public class EmptyMethodTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean checkRange) {
    final EmptyMethodInspection tool = new EmptyMethodInspection();
    doTest("emptyMethod/" + getTestName(true), tool, checkRange);
  }

  public void testSuperCall() {
    doTest();
  }

  public void testSuperCallByRange() {
    doTest(true);
  }

  public void testExternalOverride() {
    doTest();
  }

  public void testSCR8321() {
    doTest();
  }

  public void testInAnonymous() {
    doTest(true);
  }

  public void testSuperFromAnotherPackageCall() {
    doTest();
  }

  public void testSuperWithoutSync() {
    doTest();
  }

  public void testEmptyMethodsHierarchy() {
    doTest();
  }
}

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
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author max
 */
public class DeprecationInspectionTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    doTest("deprecation/" + getTestName(true), new DeprecationInspection());
  }

  public void testDeprecatedMethod() {
    doTest();
  }

  public void testDeprecatedInImport() {
    doTest();
  }

  public void testDeprecatedInStaticImport() {
    doTest();
  }

  public void testDeprecatedInner() {
    doTest();
  }

  public void testDeprecatedField() {
    doTest();
  }

  public void testDeprecatedDefaultConstructorInSuper() {
    doTest();
  }

  public void testDeprecatedDefaultConstructorInSuperNotCalled() {
    doTest();
  }

  public void testDeprecatedDefaultConstructorTypeParameter() {
    doTest();
  }

  public void testDeprecationOnVariableWithAnonymousClass() {
    doTest();
  }

  public void testMethodsOfDeprecatedClass() {
    final DeprecationInspection tool = new DeprecationInspection();
    tool.IGNORE_METHODS_OF_DEPRECATED = false;
    doTest("deprecation/" + getTestName(true), tool);
  }

}

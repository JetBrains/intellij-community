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

  private void doTest() throws Exception {
    doTest("deprecation/" + getTestName(true), new DeprecationInspection());
  }

  public void testDeprecatedMethod() throws Exception{
    doTest();
  }

  public void testDeprecatedInImport() throws Exception{
    doTest();
  }

  public void testDeprecatedInStaticImport() throws Exception{
    doTest();
  }

  public void testDeprecatedInner() throws Exception {
    doTest();
  }

  public void testDeprecatedField() throws Exception{
    doTest();
  }

  public void testDeprecatedDefaultConstructorInSuper() throws Exception {
    doTest();
  }

  public void testDeprecatedDefaultConstructorInSuperNotCalled() throws Exception {
    doTest();
  }

  public void testDeprecatedDefaultConstructorTypeParameter() throws Exception {
    doTest();
  }

  public void testDeprecationOnVariableWithAnonymousClass() throws Exception {
    doTest();
  }

  public void testMethodsOfDeprecatedClass() throws Exception {
    final DeprecationInspection tool = new DeprecationInspection();
    tool.IGNORE_METHODS_OF_DEPRECATED = false;
    doTest("deprecation/" + getTestName(true), tool);
  }

}

/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;

/**
 * @author Konstantin Bulenkov
 */
public class JavaReflectionCompletionTest extends LightFixtureCompletionTestCase {

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/reflection/";
  }

  public void testField() throws Exception {
    doTest(1, "num", "num2");
  }

  public void testDeclaredField() throws Exception {
    doTest(1, "num", "num2", "num3");
  }

  public void testDeclaredMethod() throws Exception {
    doTest(1, "method", "method2", "method3");
  }

  public void testDeclaredMethod2() throws Exception {
    doTest(2, "method", "method2", "method3");
  }

  public void testMethod() throws Exception {
    doTest(1, "method", "method2");
  }

  public void testForNameDeclaredMethod() throws Exception {
    doTest(1, "method", "method2", "method3");
  }

  public void testForNameMethod() throws Exception {
    doTest(1, "method", "method2");
  }

  public void testForNameField() throws Exception {
    doTest(1, "num", "num2");
  }

  public void testForNameDeclaredField() throws Exception {
    doTest(2, "num", "num2", "num3");
  }

  public void testVarargMethod() throws Exception {
    doTest(0, "vararg", "vararg2");
  }

  public void testGenerics() throws Exception {
    myFixture.addFileToProject("a.properties", "foo=bar"); // check that property variants don't override reflection ones
    doTest(0, "foo");
  }

  private void doTest(int index, String... expected) {
    configureByFile(getTestName(false) + ".java");
    assertStringItems(expected);
    selectItem(getLookup().getItems().get(index));
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }
}

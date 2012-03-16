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
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * @author peter
 */
public class DataFlowInspectionFixtureTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  private void doTest() throws Throwable {
    final DataFlowInspection inspection = new DataFlowInspection();
    inspection.SUGGEST_NULLABLE_ANNOTATIONS = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testTryInAnonymous() throws Throwable { doTest(); }
  public void testNullableAnonymousMethod() throws Throwable { doTest(); }
  public void testNullableAnonymousParameter() throws Throwable { doTest(); }
  public void testNullableAnonymousVolatile() throws Throwable { doTest(); }
  public void testNullableAnonymousVolatileNotNull() throws Throwable { doTest(); }

  public void testFieldInAnonymous() throws Throwable { doTest(); }
  public void testNullableField() throws Throwable { doTest(); }
  public void testCanBeNullDoesntImplyIsNull() throws Throwable { doTest(); }

  public void testBigMethodNotComplex() throws Throwable { doTest(); }
}

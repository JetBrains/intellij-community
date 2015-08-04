/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class DataFlowInspection8Test extends LightCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/";
  }

  private void doTest() {
    final DataFlowInspection inspection = new DataFlowInspection();
    inspection.SUGGEST_NULLABLE_ANNOTATIONS = true;
    inspection.REPORT_CONSTANT_REFERENCE_VALUES = false;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  public void testAnnotatedTypeParameters() throws Throwable {
    setupCustomAnnotations();
    doTest();
  }

  public void testReturnNullInLambdaExpression() throws Exception {
    doTest();
  }

  public void testReturnNullInLambdaStatement() throws Exception {
    doTest();
  }

  public void testUnboxingBoxingInLambdaReturn() throws Exception {
    doTest();
  }

  public void testUnboxingInMethodReferences() throws Exception {
    doTest();
  }

  private void setupCustomAnnotations() {
    myFixture.addClass("package foo;\n\nimport java.lang.annotation.*;\n\n@Target({ElementType.TYPE_USE}) public @interface Nullable { }");
    myFixture.addClass("package foo;\n\nimport java.lang.annotation.*;\n\n@Target({ElementType.TYPE_USE}) public @interface NotNull { }");
    final NullableNotNullManager nnnManager = NullableNotNullManager.getInstance(getProject());
    nnnManager.setNotNulls("foo.NotNull");
    nnnManager.setNullables("foo.Nullable");
    Disposer.register(myTestRootDisposable, () -> {
      nnnManager.setNotNulls();
      nnnManager.setNullables();
    });
  }

  public void testMethodReferenceOnNullable() { doTest(); }
  public void testNullableVoidLambda() { doTest(); }

  public void testNullableForeachVariable() {
    setupCustomAnnotations();
    doTest();
  }

  public void testNullableArrayComponent() {
    setupCustomAnnotations();
    final DataFlowInspection inspection = new DataFlowInspection();
    inspection.IGNORE_ASSERT_STATEMENTS = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }


  public void testOptionalOfNullable() { doTest(); }

}

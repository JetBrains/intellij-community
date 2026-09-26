// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.nullable.NotNullParameterReceivesNullInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class NotNullParameterReceivesNullInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNullPassedToNotNullParameter() { doTest(); }

  public void testNullPassedToNotNullConstructorParameter() { doTest(); }

  public void testNullPassedAsPartNotNullAnnotatedOfVarArg() { doTest(); }

  public void testNullPassedToNullableParameter() { doTest(); }

  public void testParameterUnderDefaultNotNull() { doTest(); }

  public void testNullableCalledWithNullUnderNotNullByDefault() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    doTest();
  }

  private void doTest() {
    myFixture.enableInspections(new NotNullParameterReceivesNullInspection());
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/notNullParameterReceivesNull/";
  }
}

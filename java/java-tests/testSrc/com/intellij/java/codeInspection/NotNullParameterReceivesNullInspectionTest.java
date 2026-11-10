// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.nullable.NotNullParameterReceivesNullInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class NotNullParameterReceivesNullInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/notNullParameterReceivesNull/");
  }

  @Test
  public void testNullPassedToNotNullParameter() { doTest(); }

  @Test
  public void testNullPassedToNotNullConstructorParameter() { doTest(); }

  @Test
  public void testNullPassedAsPartNotNullAnnotatedOfVarArg() { doTest(); }

  @Test
  public void testNullPassedToNullableParameter() { doTest(); }

  @Test
  public void testParameterUnderDefaultNotNull() { doTest(); }

  @Test
  public void testNullableCalledWithNullUnderNotNullByDefault() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    DataFlowInspectionTest.addJavaxDefaultNullabilityAnnotations(myFixture);
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new NotNullParameterReceivesNullInspection());
    myFixture.testHighlighting(true, false, true, myFixture.getTestName(false) + ".java");
  }
}

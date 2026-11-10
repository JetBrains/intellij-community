// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.varScopeCanBeNarrowed.ParameterCanBeLocalInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class ParameterCanBeLocalTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/parameterCanBeLocal");
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new ParameterCanBeLocalInspection());
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testFor() {
    doTest();
  }

  @Test
  public void testIf() {
    doTest();
  }

  @Test
  public void testReadOnly() {
    doTest();
  }

  @Test
  public void testSimple() {
    doTest();
  }

}
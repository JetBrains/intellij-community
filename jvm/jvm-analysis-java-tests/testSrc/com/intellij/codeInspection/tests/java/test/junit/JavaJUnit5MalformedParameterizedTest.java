// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java.test.junit;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.test.junit.JUnit5MalformedParameterizedInspection;
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil;
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaJUnit5MalformedParameterizedTest extends LightJavaInspectionTestCase {

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new JUnit5MalformedParameterizedInspection();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture);
  }

  public void testMalformedSources() { doTest(); }
  public void testMethodSource() { doTest(); }
  public void testEnumSource() { doTest(); }
  public void testMalformedSourcesImplicitConversion() { doTest(); }
  public void testMalformedSourcesImplicitParameters() { doTest(); }
  public void testMalformedSourcesTestInstancePerClass() { doTest(); }

  @Override
  protected String getBasePath() {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/junit5MalformedParameterized";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
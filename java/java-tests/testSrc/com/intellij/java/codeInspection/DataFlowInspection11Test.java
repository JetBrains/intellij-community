// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.java.codeInspection.DataFlowInspectionFixture.addJSpecifyNullMarked;
import static com.intellij.java.codeInspection.DataFlowInspectionFixture.setupTypeUseAnnotations;
import static com.intellij.java.codeInspection.DataFlowInspectionFixtureKt.dataFlowInspectionFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class DataFlowInspection11Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_11_ANNOTATED);
  private final TestFixture<DataFlowInspectionFixture> dataFlow =
    dataFlowInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/");

  @Test
  public void testNullabilityJdk11() { dataFlow.get().doTest();}
  
  @Test
  public void testComparatorComparingContract() { dataFlow.get().doTest();}

  @Test
  public void testStreamCollector10Inlining() { dataFlow.get().doTest(); }
  
  @Test
  public void testStreamFlatMapOptional() {dataFlow.get().doTest();}

  @Test
  public void testAvoidWarningAtNotInferredType() { dataFlow.get().doTest();}

  @Test
  public void testSameArguments() { dataFlow.get().doTest(); }
  
  @Test
  public void testMethodReturnTypeUnmodifiableAnnotation() { 
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyStreamConcatOfNullable() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }
}
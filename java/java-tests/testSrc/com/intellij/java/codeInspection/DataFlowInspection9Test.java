// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public class DataFlowInspection9Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9_ANNOTATED);
  private final TestFixture<DataFlowInspectionFixture> dataFlow =
    dataFlowInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/fixture/");

  @Test
  public void testMutabilityJdk9() { dataFlow.get().doTest();}
  @Test
  public void testMutabilityInferred() { dataFlow.get().doTest(); }
  @Test
  public void testObjectsRequireNonNullElse() { dataFlow.get().doTest(); }
  @Test
  public void testNewCollectionAliasing() { dataFlow.get().doTest(); }
  @Test
  public void testIoContracts9() { dataFlow.get().doTest(); }
  @Test
  public void testOptionalStreamInlining() { dataFlow.get().doTest(); }

  @Test
  public void testNullabilityAnnotationOnModule() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    myFixture.addFileToProject("module-info.java", """
      import org.jspecify.annotations.NullMarked;

      @NullMarked
      module jspecifysample {
      	requires org.jspecify;
      }""");
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyNullMarkedLocals() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyUpperBound() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }

  @Test
  public void testJSpecifyUnboxingInLambda() {
    LightJavaCodeInsightTestFixture myFixture = dataFlow.get().getFixture();
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    dataFlow.get().doTest();
  }
}
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.nullable.NotNullFieldNotInitializedInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class NotNullFieldNotInitializedInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/notNullField/");
  }

  @Test
  public void testNotNullFieldNotInitialized() { doTest(); }
  @Test
  public void testNotNullFieldInitializedIndirectly() { doTest(); }
  @Test
  public void testNotNullFieldInitializedInLambda() { doTest(); }
  @Test
  public void testNotNullFieldNotInitializedInOneConstructor() { doTest(); }
  @Test
  public void testTypeUseNotNullField() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DataFlowInspectionFixture.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }
  @Test
  public void testSuppressNullAwayInit() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    doTest();
  }
  @Test
  public void testNotNullByDefaultFieldNotInitialized() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    doTest();
  }
  @Test
  public void testImplicit() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), ImplicitUsageProvider.EP_NAME, new ImplicitUsageProvider() {
      @Override
      public boolean isImplicitUsage(@NotNull PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitRead(@NotNull PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitWrite(@NotNull PsiElement element) {
        return element instanceof PsiField && "implicit".equals(((PsiField)element).getName());
      }
    }, myFixture.getTestRootDisposable());
    doTest();
  }
  @Test
  public void testSetupJunit() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package junit.framework; public class TestCase {}");
    doTest();
  }

  @Test
  public void testNullableFieldWithBound() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    DataFlowInspectionFixture.setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    DataFlowInspectionFixture.addJSpecifyNullMarked(myFixture);
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new NotNullFieldNotInitializedInspection());
    myFixture.testHighlighting(true, false, true, myFixture.getTestName(false) + ".java");
  }

}
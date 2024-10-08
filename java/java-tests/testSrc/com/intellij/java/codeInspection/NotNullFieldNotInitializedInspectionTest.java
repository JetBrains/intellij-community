// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.nullable.NotNullFieldNotInitializedInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class NotNullFieldNotInitializedInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNotNullFieldNotInitialized() { doTest(); }
  public void testNotNullFieldInitializedIndirectly() { doTest(); }
  public void testNotNullFieldInitializedInLambda() { doTest(); }
  public void testNotNullFieldNotInitializedInOneConstructor() { doTest(); }
  public void testTypeUseNotNullField() {
    DataFlowInspectionTestCase.setupTypeUseAnnotations("typeUse", myFixture);
    doTest();
  }
  public void testNotNullByDefaultFieldNotInitialized() {
    DataFlowInspectionTest.addJavaxNullabilityAnnotations(myFixture);
    doTest();
  }
  public void testImplicit() {
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
    }, getTestRootDisposable());
    doTest();
  }
  public void testSetupJunit() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    doTest();
  }

  private void doTest() {
    myFixture.enableInspections(new NotNullFieldNotInitializedInspection());
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8_ANNOTATED;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/notNullField/";
  }

}
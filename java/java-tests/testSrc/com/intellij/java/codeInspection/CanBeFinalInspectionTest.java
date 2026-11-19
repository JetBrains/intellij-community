// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.canBeFinal.CanBeFinalInspection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiNamedElement;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public class CanBeFinalInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  private final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection");


  private void doTest() {
    final CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = true;
    tool.REPORT_FIELDS = true;
    tool.REPORT_METHODS = true;
    doTest(tool);
  }

  private void doTest(final CanBeFinalInspection tool) {
    javaInspection.get().doTest("canBeFinal/" + javaInspection.get().getTestName(false), tool, false, true);
  }

  @Test
  public void testsimpleClassInheritanceField() {
    doTest();
  }

  @Test
  public void testassignedFromLambda() {
    doTest();
  }
  
  @Test
  public void testDeprecated() {
    doTest();
  }

  @Test
  public void testassignedFromLambdaInClassInitializer() {
    doTest();
  }

  @Test
  public void testFieldAssignedInClassInitializer() {
    doTest();
  }

  @Test
  public void testFieldAssignedFromOtherClass() {
    doTest();
  }

  @Test
  public void testsimpleClassInheritance() {
    doTest();
  }

  @Test
  public void testsimpleClassInheritance1() {
    doTest();
  }

  @Test
  public void testanonymous() {
    doTest();
  }

  @Test
  public void testmethodInheritance() {
    doTest();
  }

  @Test
  public void testprivateInners() {
    doTest();
  }

  @Test
  public void testfieldAndTryBlock() {
    doTest();
  }

  @Test
  public void testfields() {
    doTest();
  }

  @Test
  public void testfieldsReading() {
    doTest();
  }

  @Test
  public void testSCR6073() {
    doTest();
  }

  @Test
  public void testSCR6781() {
    doTest();
  }

  @Test
  public void testSCR6845() {
    doTest();
  }

  @Test
  public void testSCR6861() {
    doTest();
  }

  @Test
  public void testfieldAssignmentssInInitializer() {
    doTest();
  }

  @Test
  public void teststaticFields() {
    doTest();
  }

  @Test
  public void teststaticClassInitializer() {
    doTest();
  }

  @Test
  public void testSCR7737() {
    CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = false;
    tool.REPORT_FIELDS = false;
    tool.REPORT_METHODS = true;

    doTest(tool);
  }

  @Test
  public void testfieldNonInitializedUsedInClassInitializer() {
    CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = false;
    tool.REPORT_FIELDS = true;
    tool.REPORT_METHODS = false;
    doTest(tool);
  }


  @Test
  public void testInterfaceMethodInHierarchy() {
    CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = false;
    tool.REPORT_FIELDS = false;
    tool.REPORT_METHODS = true;

    doTest(tool);
  }

  @Test
  public void testfieldImplicitWrite() {
    ImplicitUsageProvider.EP_NAME.getPoint().registerExtension(new ImplicitUsageProvider() {
      @Override
      public boolean isImplicitUsage(@NotNull PsiElement element) {
        return isImplicitWrite(element);
      }

      @Override
      public boolean isImplicitRead(@NotNull PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitWrite(@NotNull PsiElement element) {
        return element instanceof PsiField && "implicitWrite".equals(((PsiNamedElement)element).getName());
      }
    }, javaInspection.get().getFixture().getTestRootDisposable());

    doTest();
  }

  @Test
  public void testfieldInitializedInClassInitializer() {
    doTest();
  }
}

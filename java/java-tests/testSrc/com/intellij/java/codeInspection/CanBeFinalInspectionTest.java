// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.canBeFinal.CanBeFinalInspection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiNamedElement;
import com.intellij.testFramework.JavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class CanBeFinalInspectionTest extends JavaInspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    final CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = true;
    tool.REPORT_FIELDS = true;
    tool.REPORT_METHODS = true;
    doTest(tool);
  }

  private void doTest(final CanBeFinalInspection tool) {
    doTest("canBeFinal/" + getTestName(false), tool);
  }

  public void testsimpleClassInheritanceField() {
    doTest();
  }

  public void testassignedFromLambda() {
    doTest();
  }

  public void testassignedFromLambdaInClassInitializer() {
    doTest();
  }

  public void testFieldAssignedInClassInitializer() {
    doTest();
  }

  public void testFieldAssignedFromOtherClass() {
    doTest();
  }

  public void testsimpleClassInheritance() {
    doTest();
  }

  public void testsimpleClassInheritance1() {
    doTest();
  }

  public void testanonymous() {
    doTest();
  }

  public void testmethodInheritance() {
    doTest();
  }

  public void testprivateInners() {
    doTest();
  }

  public void testfieldAndTryBlock() {
    doTest();
  }

  public void testfields() {
    doTest();
  }

  public void testfieldsReading() {
    doTest();
  }

  public void testSCR6073() {
    doTest();
  }

  public void testSCR6781() {
    doTest();
  }

  public void testSCR6845() {
    doTest();
  }

  public void testSCR6861() {
    doTest();
  }

  public void testfieldAssignmentssInInitializer() {
    doTest();
  }

  public void teststaticFields() {
    doTest();
  }

  public void teststaticClassInitializer() {
    doTest();
  }

  public void testSCR7737() {
    CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = false;
    tool.REPORT_FIELDS = false;
    tool.REPORT_METHODS = true;

    doTest(tool);
  }

  public void testfieldNonInitializedUsedInClassInitializer() {
    CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = false;
    tool.REPORT_FIELDS = true;
    tool.REPORT_METHODS = false;
    doTest(tool);
  }


  public void testInterfaceMethodInHierarchy() {
    CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = false;
    tool.REPORT_FIELDS = false;
    tool.REPORT_METHODS = true;

    doTest(tool);
  }

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
    }, getTestRootDisposable());

    doTest();
  }

  public void testfieldInitializedInClassInitializer() {
    doTest();
  }
}

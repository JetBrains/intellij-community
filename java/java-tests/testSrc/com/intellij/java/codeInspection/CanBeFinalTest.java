/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.intellij.codeInspection.canBeFinal.CanBeFinalInspection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiNamedElement;
import com.intellij.testFramework.InspectionTestCase;
import com.intellij.testFramework.PlatformTestUtil;

public class CanBeFinalTest extends InspectionTestCase {
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
    PlatformTestUtil.registerExtension(ImplicitUsageProvider.EP_NAME, new ImplicitUsageProvider() {
      @Override
      public boolean isImplicitUsage(PsiElement element) {
        return isImplicitWrite(element);
      }

      @Override
      public boolean isImplicitRead(PsiElement element) {
        return false;
      }

      @Override
      public boolean isImplicitWrite(PsiElement element) {
        return element instanceof PsiField && "implicitWrite".equals(((PsiNamedElement)element).getName());
      }
    }, getTestRootDisposable());

    doTest();
  }

  public void testfieldInitializedInClassInitializer() {
    doTest();
  }
}

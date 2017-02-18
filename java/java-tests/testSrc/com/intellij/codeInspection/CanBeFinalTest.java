/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 11, 2002
 * Time: 7:51:16 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection;

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

  private void doTest() throws Exception {
    final CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = true;
    tool.REPORT_FIELDS = true;
    tool.REPORT_METHODS = true;
    doTest(tool);
  }

  private void doTest(final CanBeFinalInspection tool) throws Exception {
    doTest("canBeFinal/" + getTestName(false), tool);
  }

  public void testsimpleClassInheritanceField() throws Exception {
    doTest();
  }

  public void testsimpleClassInheritance() throws Exception {
    doTest();
  }

  public void testsimpleClassInheritance1() throws Exception {
    doTest();
  }

  public void testanonymous() throws Exception {
    doTest();
  }

  public void testmethodInheritance() throws Exception {
    doTest();
  }

  public void testprivateInners() throws Exception {
    doTest();
  }

  public void testfieldAndTryBlock() throws Exception {
    doTest();
  }

  public void testfields() throws Exception {
    doTest();
  }

  public void testfieldsReading() throws Exception {
    doTest();
  }

  public void testSCR6073() throws Exception {
    doTest();
  }

  public void testSCR6781() throws Exception {
    doTest();
  }

  public void testSCR6845() throws Exception {
    doTest();
  }

  public void testSCR6861() throws Exception {
    doTest();
  }

  public void testfieldAssignmentssInInitializer() throws Exception {
    doTest();
  }

  public void teststaticFields() throws Exception {
    doTest();
  }

  public void teststaticClassInitializer() throws Exception {
    doTest();
  }

  public void testSCR7737() throws Exception {
    CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = false;
    tool.REPORT_FIELDS = false;
    tool.REPORT_METHODS = true;

    doTest(tool);
  }

  public void testfieldNonInitializedUsedInClassInitializer() throws Exception {
    CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = false;
    tool.REPORT_FIELDS = true;
    tool.REPORT_METHODS = false;
    doTest(tool);
  }


  public void testInterfaceMethodInHierarchy() throws Exception {
    CanBeFinalInspection tool = new CanBeFinalInspection();
    tool.REPORT_CLASSES = false;
    tool.REPORT_FIELDS = false;
    tool.REPORT_METHODS = true;

    doTest(tool);
  }

  public void testfieldImplicitWrite() throws Exception {
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
}

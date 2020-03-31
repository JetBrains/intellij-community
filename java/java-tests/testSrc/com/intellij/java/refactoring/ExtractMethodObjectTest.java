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

package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectHandler;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectProcessor;
import org.jetbrains.annotations.NotNull;

public class ExtractMethodObjectTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest() throws Exception {
    doTest(true);
  }

  private void doTest(final boolean createInnerClass) throws Exception {
    final String testName = getTestName(false);
    configureByFile("/refactoring/extractMethodObject/" + testName + ".java");
    PsiElement element = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiMethod);
    final PsiMethod method = (PsiMethod) element;

    final ExtractMethodObjectProcessor processor =
      new ExtractMethodObjectProcessor(getProject(), getEditor(), method.getBody().getStatements(), "InnerClass");
    final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = processor.getExtractProcessor();
    processor.setCreateInnerClass(createInnerClass);
    extractProcessor.setShowErrorDialogs(false);
    extractProcessor.prepare();
    extractProcessor.testPrepare();

    ExtractMethodObjectHandler.extractMethodObject(getProject(), getEditor(), processor, extractProcessor);

    checkResultByFile("/refactoring/extractMethodObject/" + testName + ".java" + ".after");
  }

  public void testStatic() throws Exception {
    doTest();
  }

  public void testStaticTypeParams() throws Exception {
    doTest();
  }

  public void testStaticTypeParamsReturn() throws Exception {
    doTest();
  }

  public void testTypeParamsReturn() throws Exception {
    doTest();
  }

  public void testTypeParams() throws Exception {
    doTest();
  }

  public void testMethodInHierarchy() throws Exception {
    doTest();
  }

  public void testQualifier() throws Exception {
    doTest();
  }

  public void testVarargs() throws Exception {
    doTest();
  }

  public void testFieldUsage() throws Exception {
    doTest();
  }

  public void testMethodInHierarchyReturn() throws Exception {
    doTest();
  }

  public void testStaticTypeParamsReturnNoDelete() throws Exception {
    doTest();
  }

  public void testStaticTypeParamsRecursive() throws Exception {
    doTest();
  }

  public void testRecursion() throws Exception {
    doTest();
  }

  public void testWrapWithObject() throws Exception {
    doTest(false);
  }

  public void testWrapWithObjectRecursive() throws Exception {
    doTest(false);
  }
  
  public void testWithPrivateMethodUsed() throws Exception {
    doTest();
  }
  
  public void testWithPrivateMethodUsed1() throws Exception {
    doTest();
  }

  public void testWithPrivateStaticMethodUsed() throws Exception {
    doTest();
  }

  public void testWithPrivateStaticMethodUsed2() throws Exception {
    doTest();
  }

  public void testThisAndSuperAnon() throws Exception {
    doTest(false);
  }

  public void testWithPrivateMethodWhichCantBeMoved() throws Exception {
    doTest();
  }
}

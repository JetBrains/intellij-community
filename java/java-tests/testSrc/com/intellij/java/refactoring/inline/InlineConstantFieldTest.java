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
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.refactoring.LightRefactoringTestCase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.*;
import com.intellij.refactoring.inline.InlineConstantFieldHandler;
import com.intellij.refactoring.inline.InlineConstantFieldProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class InlineConstantFieldTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17(); // has to have src.zip
  }

  public void testQualifiedExpression() throws Exception {
    doTest();
  }

  public void testQualifiedExpressionInLib() throws Exception {
    doTest(true);
  }

  public void testLocalClassDecoding() throws Exception {
    doTest();
  }

  public void testQualifiedConstantExpression() throws Exception {
    doTest();
  }

   public void testQualifiedConstantExpressionReplacedWithAnotherOne() throws Exception {
    doTest();
  }
  
  public void testStaticallyImportedQualifiedExpression() throws Exception {
    doTest();
  }

  public void testCastWhenLambdaAsQualifier() throws Exception {
    doTest();
  }

  public void testConstantFromLibrary() throws Exception {
    doTest();
  }

  public void testFinalInitializedInConstructor() throws Exception {
    doTest();
  }

  public void testDiamondInitializer() throws Exception {
    doTest();
  }

  public void testMultipleInitializers() throws Exception {
    configureByFile("/refactoring/inlineConstantField/" + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assertTrue(element instanceof PsiField);
    assertNull(InlineConstantFieldHandler.getInitializer((PsiField)element));
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(boolean inlineThisOnly) throws Exception {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineConstantField/" + name + ".java";
    configureByFile(fileName);
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    final PsiReference ref = myFile.findReferenceAt(myEditor.getCaretModel().getOffset());
    PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;
    assertTrue(element instanceof PsiField);
    PsiField field = (PsiField)element.getNavigationElement();
    new InlineConstantFieldProcessor(field, getProject(), refExpr, inlineThisOnly || element instanceof PsiCompiledElement).run();
    checkResultByFile(fileName + ".after");
  }
}
/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.makeStatic.MakeMethodStaticProcessor;
import com.intellij.refactoring.makeStatic.MakeStaticUtil;
import com.intellij.refactoring.makeStatic.Settings;
import com.intellij.refactoring.util.VariableData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class MakeMethodStaticTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testEmptyMethod() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before1.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after1.java");
  }

  public void testUseStatic() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before2.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after2.java");
  }

  public void testUseField() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before3.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after3.java");
  }

  public void testIDEADEV2556() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before21.java");
    perform(false);
    checkResultByFile("/refactoring/makeMethodStatic/after21.java");
  }

  public void testUseFieldWithThis() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before4.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after4.java");
  }

  public void testUseFieldWithSuperEmptyExtends() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before5.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after5.java");
  }

  public void testUseFieldWithSuper() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before6.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after6.java");
  }

  public void testUseMethod() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before7.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after7.java");
  }

  public void testThisInsideAnonymous() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before8.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after8.java");
  }

  public void testUsageInSubclass() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before9.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after9.java");
  }

  public void testGeneralUsageNoParam() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before10.java");
    perform(false);
    checkResultByFile("/refactoring/makeMethodStatic/after10-np.java");
  }

  public void testGeneralUsage() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before10.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after10.java");
  }

  public void testUsageInSubclassWithSuper() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before11.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after11.java");
  }

  public void testSuperUsageWithComplexSuperClass() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before12.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after12.java");
  }

  public void testExplicitThis() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before13.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after13.java");
  }

  public void testQualifiedThis() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before14.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after14.java");
  }

  public void testSCR8043() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before15.java");
    perform(true);
    checkResultByFile("/refactoring/makeMethodStatic/after15.java");
  }

  public void testJavadoc1() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before16.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after16.java");
  }

  public void testJavadoc2() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before17.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after17.java");
  }

  public void testGenericClass() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before18.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after18.java");
  }

  public void testFieldWriting() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before19.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after19.java");
  }

  public void testQualifiedInnerClassCreation() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before20.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after20.java");
  }

  public void testQualifiedThisAdded() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before22.java");
    performWithFields();
    checkResultByFile("/refactoring/makeMethodStatic/after22.java");
  }

  public void testDeepStaticOverrides() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/beforeOverrides.java");
    perform(false);
    checkResultByFile("/refactoring/makeMethodStatic/afterOverrides.java");
  }

  public void testDeepStatic() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/beforeDeep.java");
    perform(false);
    checkResultByFile("/refactoring/makeMethodStatic/afterDeep.java");
  }

  public void testPreserveTypeParams() throws Exception {
    doTestFields(false);
  }

  public void testFieldsAndDelegation() throws Exception {
    doTestFields(true);
  }

  public void testInnerStaticClassUsed() throws Exception {
    configureByFile("/refactoring/makeMethodStatic/beforeInnerStaticClassUsed.java");
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiMethod);
    assertFalse(MakeStaticUtil.isParameterNeeded((PsiMethod)element));
  }

  public void testMethodReference() throws Exception {
    doTest(true);
  }

  public void testThisMethodReference() throws Exception {
    doTest(false);
  }

  public void testPreserveParametersAlignment() throws Exception {
    doTest();
  }

  public void testDelegatePlace() throws Exception {
    doTest(true, true);
  }

  public void testClearOverrideAnnotation() throws Exception {
    doTest(true, true);
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean addClassParameter) throws Exception {
    doTest(addClassParameter, false);
  }

  private void doTest(final boolean addClassParameter, boolean delegate) throws Exception {
    configureByFile("/refactoring/makeMethodStatic/before" + getTestName(false) + ".java");
    perform(addClassParameter, delegate);
    checkResultByFile("/refactoring/makeMethodStatic/after" + getTestName(false) + ".java");
  }

  private void doTestFields(boolean delegate) throws Exception {
    final String testName = getTestName(false);
    configureByFile("/refactoring/makeMethodStatic/before" + testName + ".java");
    performWithFields(delegate);
    checkResultByFile("/refactoring/makeMethodStatic/after" + testName + ".java");
  }


  private static void perform(boolean addClassParameter) {
    perform(addClassParameter, false);
  }

  private static void perform(boolean addClassParameter, boolean delegate) {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiMethod);
    PsiMethod method = (PsiMethod) element;

    new MakeMethodStaticProcessor(
            getProject(),
            method,
            new Settings(true, addClassParameter ? "anObject" : null, null, delegate)).run();
  }

  private static void performWithFields() {
    performWithFields(false);
  }

  private static void performWithFields(boolean delegate) {
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiMethod);
    PsiMethod method = (PsiMethod) element;
    final ArrayList<VariableData> parametersForFields = new ArrayList<>();
    final boolean addClassParameter = MakeStaticUtil.buildVariableData(method, parametersForFields);

    new MakeMethodStaticProcessor(
            getProject(),
            method,
            new Settings(true, addClassParameter ? "anObject" : null,
                         parametersForFields.toArray(
                           new VariableData[parametersForFields.size()]), delegate)).run();
  }
}

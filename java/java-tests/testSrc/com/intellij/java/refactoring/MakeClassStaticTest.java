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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.makeStatic.MakeClassStaticProcessor;
import com.intellij.refactoring.makeStatic.MakeStaticUtil;
import com.intellij.refactoring.makeStatic.Settings;
import com.intellij.refactoring.util.VariableData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class MakeClassStaticTest extends LightRefactoringTestCase {
  private static final String TEST_ROOT = "/refactoring/makeClassStatic/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSimple1() { perform(); }

  public void testSimpleFields() { performWithFields(); }

  public void testFieldInitializerMovedToTheConstructor() { performWithFields(); }

  public void testQualifiedThis() { perform(); }

  public void testIDEADEV3247() { perform(); }

  public void testIDEADEV11595() { perform(); }

  public void testIDEADEV12762() { perform(); }

  public void testNewExpressionQualifier() {perform();}
  public void testThisSuperExpressions() {perform();}

  public void testNonDefaultConstructorAnonymousClass() {perform();}
  public void testDefaultConstructorAnonymousClass() {perform();}
  public void testFieldInitializerSplit() {perform();}

  public void testRegReference() {
    perform();
  }

  public void testFieldWithMyPrefix() {
    final JavaCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    String oldPrefix = settings.FIELD_NAME_PREFIX;
    settings.FIELD_NAME_PREFIX = "my";
    try {
      performWithFields();
    }
    finally {
      settings.FIELD_NAME_PREFIX = oldPrefix;
    }
  }

  private void perform() {
    configureByFile(TEST_ROOT + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiClass);
    PsiClass aClass = (PsiClass)element;

    boolean addClassParameter = MakeStaticUtil.isParameterNeeded(aClass);

    new MakeClassStaticProcessor(
            getProject(),
            aClass,
            new Settings(true, addClassParameter ? "anObject" : null, null)).run();
    checkResultByFile(TEST_ROOT + getTestName(false) + "_after.java");
  }

  private void performWithFields() {
    configureByFile(TEST_ROOT + getTestName(false) + ".java");
    PsiElement element = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiClass);
    PsiClass aClass = (PsiClass)element;
    final ArrayList<VariableData> parametersForFields = new ArrayList<>();
    final boolean addClassParameter = MakeStaticUtil.buildVariableData(aClass, parametersForFields);

    new MakeClassStaticProcessor(
            getProject(),
            aClass,
            new Settings(true, addClassParameter ? "anObject" : null,
                         parametersForFields.toArray(
                           new VariableData[parametersForFields.size()]))).run();
    checkResultByFile(TEST_ROOT + getTestName(false) + "_after.java");
  }
}

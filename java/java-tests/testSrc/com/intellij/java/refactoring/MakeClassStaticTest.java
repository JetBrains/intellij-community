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

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.makeStatic.MakeClassStaticProcessor;
import com.intellij.refactoring.makeStatic.MakeStaticUtil;
import com.intellij.refactoring.makeStatic.Settings;
import com.intellij.refactoring.util.VariableData;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class MakeClassStaticTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  private static final String TEST_ROOT = "/refactoring/makeClassStatic/";

  @Test
  public void testSimple1() { perform(); }

  @Test
  public void testSimpleFields() { performWithFields(); }

  @Test
  public void testFieldInitializerMovedToTheConstructor() { performWithFields(); }

  @Test
  public void testQualifiedThis() { perform(); }

  @Test
  public void testIDEADEV3247() { perform(); }

  @Test
  public void testIDEADEV11595() { perform(); }

  @Test
  public void testIDEADEV12762() { perform(); }

  @Test
  public void testNewExpressionQualifier() {perform();}
  @Test
  public void testThisSuperExpressions() {perform();}

  @Test
  public void testNonDefaultConstructorAnonymousClass() {perform();}
  @Test
  public void testDefaultConstructorAnonymousClass() {perform();}
  @Test
  public void testFieldInitializerSplit() {perform();}
  @Test
  public void testWarnAboutClassInitializer() {
    try {
      perform();
      fail("Conflict expected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Field 'anObject' won't be initialized inside class initializer", e.getMessage());
    }
  }

  @Test
  public void testRegReference() {
    perform();
  }

  @Test
  public void testFieldWithMyPrefix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    settings.FIELD_NAME_PREFIX = "my";
    performWithFields();
  }

  private void perform() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(TEST_ROOT + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
      assertInstanceOf(PsiClass.class, element);
      PsiClass aClass = (PsiClass)element;

      boolean addClassParameter = MakeStaticUtil.isParameterNeeded(aClass);

      new MakeClassStaticProcessor(
              myFixture.getProject(),
              aClass,
              new Settings(true, addClassParameter ? "anObject" : null, null)).run();
      myFixture.checkResultByFile(TEST_ROOT + myFixture.getTestName(false) + "_after.java");
    });
  }

  private void performWithFields() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(TEST_ROOT + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
      assertInstanceOf(PsiClass.class, element);
      PsiClass aClass = (PsiClass)element;
      final ArrayList<VariableData> parametersForFields = new ArrayList<>();
      final boolean addClassParameter = MakeStaticUtil.buildVariableData(aClass, parametersForFields);

      new MakeClassStaticProcessor(
              myFixture.getProject(),
              aClass,
              new Settings(true, addClassParameter ? "anObject" : null,
                           parametersForFields.toArray(
                             new VariableData[0]))).run();
      myFixture.checkResultByFile(TEST_ROOT + myFixture.getTestName(false) + "_after.java");
    });
  }
}

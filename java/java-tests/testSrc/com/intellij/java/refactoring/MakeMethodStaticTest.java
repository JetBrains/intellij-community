// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.makeStatic.MakeMethodStaticProcessor;
import com.intellij.refactoring.makeStatic.MakeStaticHandler;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class MakeMethodStaticTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testEmptyMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before1.java");
    myFixture.runInCommand(() -> {
      perform(true);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after1.java");
    });
  }

  @Test
  public void testUseStatic() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before2.java");
    myFixture.runInCommand(() -> {
      perform(true);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after2.java");
    });
  }

  @Test
  public void testUseField() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before3.java");
    myFixture.runInCommand(() -> {
      perform(true);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after3.java");
    });
  }

  @Test
  public void testIDEADEV2556() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before21.java");
    myFixture.runInCommand(() -> {
      perform(false);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after21.java");
    });
  }

  @Test
  public void testUseFieldWithThis() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before4.java");
    myFixture.runInCommand(() -> {
      perform(true);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after4.java");
    });
  }

  @Test
  public void testUseFieldWithSuperEmptyExtends() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before5.java");
    myFixture.runInCommand(() -> {
      perform(true);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after5.java");
    });
  }

  @Test
  public void testUseFieldWithSuper() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before6.java");
    myFixture.runInCommand(() -> {
      perform(true);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after6.java");
    });
  }

  @Test
  public void testUseMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before7.java");
    myFixture.runInCommand(() -> {
      perform(true);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after7.java");
    });
  }

  @Test
  public void testThisInsideAnonymous() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before8.java");
    myFixture.runInCommand(() -> {
      perform(true);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after8.java");
    });
  }

  @Test
  public void testUsageInSubclass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before9.java");
    myFixture.runInCommand(() -> {
      perform(true);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after9.java");
    });
  }

  @Test
  public void testGeneralUsageNoParam() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before10.java");
    myFixture.runInCommand(() -> {
      BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> perform(false));
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after10-np.java");
    });
  }

  @Test
  public void testGeneralUsage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before10.java");
    myFixture.runInCommand(() -> {
      perform(true);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after10.java");
    });
  }

  @Test
  public void testUsageInSubclassWithSuper() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before11.java");
    myFixture.runInCommand(() -> {
      BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> perform(true));
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after11.java");
    });
  }

  @Test
  public void testSuperUsageWithComplexSuperClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before12.java");
    myFixture.runInCommand(() -> {
      perform(true);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after12.java");
    });
  }

  @Test
  public void testExplicitThis() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before13.java");
    myFixture.runInCommand(() -> {
      perform(true);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after13.java");
    });
  }

  @Test
  public void testQualifiedThis() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before14.java");
    myFixture.runInCommand(() -> {
      perform(true);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after14.java");
    });
  }

  @Test
  public void testSCR8043() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before15.java");
    myFixture.runInCommand(() -> {
      perform(true);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after15.java");
    });
  }

  @Test
  public void testJavadoc1() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before16.java");
    myFixture.runInCommand(() -> {
      performWithFields();
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after16.java");
    });
  }

  @Test
  public void testJavadoc2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before17.java");
    myFixture.runInCommand(() -> {
      performWithFields();
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after17.java");
    });
  }

  @Test
  public void testGenericClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before18.java");
    myFixture.runInCommand(() -> {
      performWithFields();
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after18.java");
    });
  }

  @Test
  public void testFieldWriting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before19.java");
    myFixture.runInCommand(() -> {
      performWithFields();
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after19.java");
    });
  }

  @Test
  public void testQualifiedInnerClassCreation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before20.java");
    myFixture.runInCommand(() -> {
      performWithFields();
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after20.java");
    });
  }

  @Test
  public void testQualifiedThisAdded() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before22.java");
    myFixture.runInCommand(() -> {
      performWithFields();
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after22.java");
    });
  }

  @Test
  public void testDeepStaticOverrides() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/beforeOverrides.java");
    myFixture.runInCommand(() -> {
      perform(false);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/afterOverrides.java");
    });
  }

  @Test
  public void testDeepStatic() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/beforeDeep.java");
    myFixture.runInCommand(() -> {
      perform(false);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/afterDeep.java");
    });
  }

  @Test
  public void testPreserveTypeParams() {
    doTestFields(false);
  }

  @Test
  public void testFieldsAndDelegation() {
    doTestFields(true);
  }

  @Test
  public void testInnerStaticClassUsed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/beforeInnerStaticClassUsed.java");
    myFixture.runInCommand(() -> {
      PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
      assertInstanceOf(PsiMethod.class, element);
      assertFalse(MakeStaticUtil.isParameterNeeded((PsiMethod)element));
    });
  }

  @Test
  public void testMethodReference() {
    doTest(true);
  }

  @Test
  public void testThisMethodReference() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(false));
  }

  @Test
  public void testMethodReferenceInTheSameMethod() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(false));
  }

  @Test
  public void testExpandMethodReference() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(true));
  }

  @Test
  public void testPreserveParametersAlignment() {
    doTest();
  }
  
  @Test
  public void testReceiverParameter() { doTest(); }

  @Test
  public void testDelegatePlace() {
    doTest(true, true);
  }

  @Test
  public void testClearOverrideAnnotation() {
    doTest(true, true);
  }

  @Test
  public void testMethodReferenceInAnonymousClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> {
      myFixture.configureByFile("/refactoring/makeMethodStatic/before" + myFixture.getTestName(false) + ".java");
      myFixture.runInCommand(() -> {
        PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
        assertNull(MakeStaticHandler.validateTarget((PsiTypeParameterListOwner)element));
        perform(false, false);
        myFixture.checkResultByFile("/refactoring/makeMethodStatic/after" + myFixture.getTestName(false) + ".java");
      });
      });
  }
  
  @Test
  public void testCallSuperClassMethod() {
    doTest(true, false);
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean addClassParameter) {
    doTest(addClassParameter, false);
  }

  private void doTest(final boolean addClassParameter, boolean delegate) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/makeMethodStatic/before" + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      perform(addClassParameter, delegate);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after" + myFixture.getTestName(false) + ".java");
    });
  }

  private void doTestFields(boolean delegate) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false);
    myFixture.configureByFile("/refactoring/makeMethodStatic/before" + testName + ".java");
    myFixture.runInCommand(() -> {
      performWithFields(delegate);
      myFixture.checkResultByFile("/refactoring/makeMethodStatic/after" + testName + ".java");
    });
  }


  private void perform(boolean addClassParameter) {
    perform(addClassParameter, false);
  }

  private void perform(boolean addClassParameter, boolean delegate) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertInstanceOf(PsiMethod.class, element);
    PsiMethod method = (PsiMethod) element;

    new MakeMethodStaticProcessor(
            myFixture.getProject(),
            method,
            new Settings(true, addClassParameter ? "anObject" : null, null, delegate)).run();
  }

  private void performWithFields() {
    performWithFields(false);
  }

  private void performWithFields(boolean delegate) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertInstanceOf(PsiMethod.class, element);
    PsiMethod method = (PsiMethod) element;
    final ArrayList<VariableData> parametersForFields = new ArrayList<>();
    final boolean addClassParameter = MakeStaticUtil.buildVariableData(method, parametersForFields);

    new MakeMethodStaticProcessor(
            myFixture.getProject(),
            method,
            new Settings(true, addClassParameter ? "anObject" : null,
                         parametersForFields.toArray(
                           new VariableData[0]), delegate)).run();
  }
}

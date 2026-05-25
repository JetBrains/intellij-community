// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.refactoring.introduceField.JavaIntroduceFieldService;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IntroduceFieldInSameClassTest extends LightJavaCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/introduceField/";
  }

  public void testInClassInitializer() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, true);
  }

  public void testConflictingFieldInContainingClass() {
    configureByFile("beforeConflictingFieldInContainingClass.java");
    new MockIntroduceFieldHandler(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false) {
      @Override
      protected String getNewName(Project project, PsiExpression expr, PsiType type) {
        return "aField";
      }
    }.invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile("afterConflictingFieldInContainingClass.java");
  }

  public void testConflictingFieldInContainingClassLocal() {
    configureByFile("beforeConflictingFieldInContainingClassLocal.java");
    new MockIntroduceFieldHandler(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false) {
      @Override
      protected String getNewName(Project project, PsiExpression expr, PsiType type) {
        return "aField";
      }

      @Override
      protected int getChosenClassIndex(List<PsiClass> classes) {
        return 0;
      }
    }.invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile("afterConflictingFieldInContainingClassLocal.java");
  }

  public void testElseClause() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD, true);
  }

  public void testOuterClass() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_CONSTRUCTOR, false);
  }

  public void testConflictingConstructorParameter() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_CONSTRUCTOR, false);
  }

  public void testOnClassLevelNoDuplicates() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  public void testOnClassLevelDuplicates() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  public void testOnClassLevelDuplicates1() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  public void testOnClassLevelBinary() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  //multiple error elements on class level corresponding to the extracted fragment ------------------
  public void testOnClassLevelNewExpression() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  public void testOnClassLevelClassForName() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }
  //-------------------------------------------------------------------------------------------------

  public void testUnresolvedReferenceToLocalVar() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD, false);
  }

  public void testForcedFieldType() {
    configureByFile("beforeForcedFieldType.java");
    new MockIntroduceFieldHandler(JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD, false) {
      @Override
      protected PsiType getFieldType(PsiType type) {
        return PsiTypes.intType();
      }
    }.invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile("afterForcedFieldType.java");
  }

  public void testRejectIntroduceFieldFromExprInThisCall() {
    configureByFile("beforeRejectIntroduceFieldFromExprInThisCall.java");
    try {
      performRefactoring(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
      fail("Should not proceed");
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot perform refactoring.\nInvalid expression context.", e.getMessage());
    }
  }

  public void testRejectFieldFromLocal() {
    configureByFile("beforeRejectFieldFromLocal.java");
    try {
      performRefactoring(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
      fail("Should not proceed");
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot perform refactoring.\n" +
                   "Local class <b><code>Local</code></b> is not visible to members of class <b><code>K</code></b>", e.getMessage());
    }
  }

  public void testStaticFieldInRecord() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, true);
  }

  public void testAcceptIntroduceFieldFromExprInThisCall() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, true);
  }

  public void testEnclosingAnonymous() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_CONSTRUCTOR, false);
  }

  public void testLocalVarAnnotations() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  public void testFromLambdaExpr() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  public void testSimplifiedDiamond() {
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  public void testIncompleteInClassContext1(){
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  public void testIncompleteInClassContext2(){
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  public void testIncompleteInClassContext3(){
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  public void testIncompleteInClassContext4(){
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  public void testIncompleteInClassContext5(){
    doTest(JavaIntroduceFieldService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  public void testStaticFieldInInnerClass() {
    configureByFile("beforeStaticFieldInInnerClass.java");
    new MockIntroduceFieldHandler(JavaIntroduceFieldService.InitializationPlace.IN_CURRENT_METHOD, false) {
      @Override
      protected int getChosenClassIndex(List<PsiClass> classes) {
        return 0;
      }
    }.invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile("afterStaticFieldInInnerClass.java");
  }

  private void performRefactoring(JavaIntroduceFieldService.InitializationPlace initializationPlace, boolean declareStatic) {
    new MockIntroduceFieldHandler(initializationPlace, declareStatic).invoke(getProject(), getEditor(), getFile(), null);
  }

  private void doTest(JavaIntroduceFieldService.InitializationPlace initializationPlace, boolean declareStatic) {
    configureByFile("before" + getTestName(false) + ".java");
    performRefactoring(initializationPlace, declareStatic);
    checkResultByFile("after" + getTestName(false) + ".java");
  }
}
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author ven
 */
public class IntroduceFieldInSameClassTest extends LightCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/refactoring/introduceField/";
  }

  public void testInClassInitializer() {
    configureByFile("before1.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, true);
    checkResultByFile("after1.java");
  }

  public void testConflictingFieldInContainingClass() {
    configureByFile("beforeConflictingFieldInContainingClass.java");
    new MockIntroduceFieldHandler(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false) {
      @Override
      protected String getNewName(Project project, PsiExpression expr, PsiType type) {
        return "aField";
      }
    }.invoke(getProject(), myEditor, myFile, null);
    checkResultByFile("afterConflictingFieldInContainingClass.java");
  }

  public void testConflictingFieldInContainingClassLocal() {
    configureByFile("beforeConflictingFieldInContainingClassLocal.java");
    new MockIntroduceFieldHandler(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false) {
      @Override
      protected String getNewName(Project project, PsiExpression expr, PsiType type) {
        return "aField";
      }

      @Override
      protected int getChosenClassIndex(List<PsiClass> classes) {
        return 0;
      }
    }.invoke(getProject(), myEditor, myFile, null);
    checkResultByFile("afterConflictingFieldInContainingClassLocal.java");
  }

  public void testInElseClause() {
    configureByFile("beforeElseClause.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD, true);
    checkResultByFile("afterElseClause.java");
  }

  public void testOuterClass() {
    configureByFile("beforeOuterClass.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR, false);
    checkResultByFile("afterOuterClass.java");
  }

  public void testConflictingConstructorParameter() {
    configureByFile("beforeConflictingConstructorParameter.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR, false);
    checkResultByFile("afterConflictingConstructorParameter.java");
  }

  public void testOnClassLevelNoDuplicates() {
    configureByFile("beforeOnClassLevelNoDuplicates.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("afterOnClassLevelNoDuplicates.java");
  }

  public void testOnClassLevelDuplicates() {
    configureByFile("beforeOnClassLevelDuplicates.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("afterOnClassLevelDuplicates.java");
  }

  public void testOnClassLevelDuplicates1() {
    configureByFile("beforeOnClassLevelDuplicates1.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("afterOnClassLevelDuplicates1.java");
  }

  public void testOnClassLevelBinary() {
    configureByFile("beforeOnClassLevelBinary.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("afterOnClassLevelBinary.java");
  }

  //multiple error elements on class level corresponding to the extracted fragment ------------------
  public void testOnClassLevelNewExpression() {
    configureByFile("beforeOnClassLevelNewExpression.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("afterOnClassLevelNewExpression.java");
  }

  public void testOnClassLevelClassForName() {
    configureByFile("beforeOnClassLevelClassForName.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("afterOnClassLevelClassForName.java");
  }
  //-------------------------------------------------------------------------------------------------

  public void testUnresolvedReferenceToLocalVar() {
    configureByFile("beforeUnresolvedReferenceToLocalVar.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD, false);
    checkResultByFile("afterUnresolvedReferenceToLocalVar.java");
  }

  public void testForcedFieldType() {
    configureByFile("beforeForcedFieldType.java");
    new MockIntroduceFieldHandler(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD, false) {
      @Override
      protected PsiType getFieldType(PsiType type) {
        return PsiType.INT;
      }
    }.invoke(getProject(), myEditor, myFile, null);
    checkResultByFile("afterForcedFieldType.java");
  }

  public void testRejectIntroduceFieldFromExprInThisCall() {
    configureByFile("beforeRejectIntroduceFieldFromExprInThisCall.java");
    try {
      performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
      fail("Should not proceed");
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot perform refactoring.\nInvalid expression context.", e.getMessage());
    }
  }

  public void testInConstructorEnclosingAnonymous() {
    configureByFile("beforeEnclosingAnonymous.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR, false);
    checkResultByFile("afterEnclosingAnonymous.java");
  }

  public void testLocalVarAnnotations() {
    configureByFile("beforeLocalVarAnnotations.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("afterLocalVarAnnotations.java");
  }

  public void testFromLambdaExpr() {
    configureByFile("beforeFromLambdaExpr.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("afterFromLambdaExpr.java");
  }

  public void testSimplifyDiamond() {
    configureByFile("beforeSimplifiedDiamond.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("afterSimplifiedDiamond.java");
  }

  public void testStaticFieldInInnerClass() {
    configureByFile("beforeStaticFieldInInnerClass.java");
    new MockIntroduceFieldHandler(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD, false) {
      @Override
      protected int getChosenClassIndex(List<PsiClass> classes) {
        return 0;
      }
    }.invoke(getProject(), myEditor, myFile, null);
    checkResultByFile("afterStaticFieldInInnerClass.java");
  }

  private static void performRefactoring(BaseExpressionToFieldHandler.InitializationPlace initializationPlace, boolean declareStatic) {
    new MockIntroduceFieldHandler(initializationPlace, declareStatic).invoke(getProject(), myEditor, myFile, null);
  }
}
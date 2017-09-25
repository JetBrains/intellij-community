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
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInClassInitializer() {
    configureByFile("/refactoring/introduceField/before1.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, true);
    checkResultByFile("/refactoring/introduceField/after1.java");
  }

  public void testConflictingFieldInContainingClass() {
    configureByFile("/refactoring/introduceField/beforeConflictingFieldInContainingClass.java");
    new MockIntroduceFieldHandler(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false) {
      @Override
      protected String getNewName(Project project, PsiExpression expr, PsiType type) {
        return "aField";
      }
    }.invoke(getProject(), myEditor, myFile, null);
    checkResultByFile("/refactoring/introduceField/afterConflictingFieldInContainingClass.java");
  }

  public void testConflictingFieldInContainingClassLocal() {
    configureByFile("/refactoring/introduceField/beforeConflictingFieldInContainingClassLocal.java");
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
    checkResultByFile("/refactoring/introduceField/afterConflictingFieldInContainingClassLocal.java");
  }

  public void testInElseClause() {
    configureByFile("/refactoring/introduceField/beforeElseClause.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD, true);
    checkResultByFile("/refactoring/introduceField/afterElseClause.java");
  }

  public void testOuterClass() {
    configureByFile("/refactoring/introduceField/beforeOuterClass.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR, false);
    checkResultByFile("/refactoring/introduceField/afterOuterClass.java");
  }

  public void testOnClassLevelNoDuplicates() {
    configureByFile("/refactoring/introduceField/beforeOnClassLevelNoDuplicates.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterOnClassLevelNoDuplicates.java");
  }

  public void testOnClassLevelDuplicates() {
    configureByFile("/refactoring/introduceField/beforeOnClassLevelDuplicates.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterOnClassLevelDuplicates.java");
  }

  public void testOnClassLevelDuplicates1() {
    configureByFile("/refactoring/introduceField/beforeOnClassLevelDuplicates1.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterOnClassLevelDuplicates1.java");
  }

  public void testOnClassLevelBinary() {
    configureByFile("/refactoring/introduceField/beforeOnClassLevelBinary.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterOnClassLevelBinary.java");
  }

  //multiple error elements on class level corresponding to the extracted fragment ------------------
  public void testOnClassLevelNewExpression() {
    configureByFile("/refactoring/introduceField/beforeOnClassLevelNewExpression.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterOnClassLevelNewExpression.java");
  }

  public void testOnClassLevelClassForName() {
    configureByFile("/refactoring/introduceField/beforeOnClassLevelClassForName.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterOnClassLevelClassForName.java");
  }
  //-------------------------------------------------------------------------------------------------

  public void testUnresolvedReferenceToLocalVar() {
    configureByFile("/refactoring/introduceField/beforeUnresolvedReferenceToLocalVar.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD, false);
    checkResultByFile("/refactoring/introduceField/afterUnresolvedReferenceToLocalVar.java");
  }

  public void testForcedFieldType() {
    configureByFile("/refactoring/introduceField/beforeForcedFieldType.java");
    new MockIntroduceFieldHandler(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD, false) {
      @Override
      protected PsiType getFieldType(PsiType type) {
        return PsiType.INT;
      }
    }.invoke(getProject(), myEditor, myFile, null);
    checkResultByFile("/refactoring/introduceField/afterForcedFieldType.java");
  }

  public void testRejectIntroduceFieldFromExprInThisCall() {
    configureByFile("/refactoring/introduceField/beforeRejectIntroduceFieldFromExprInThisCall.java");
    try {
      performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
      fail("Should not proceed");
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot perform refactoring.\nInvalid expression context.", e.getMessage());
    }
  }

  public void testInConstructorEnclosingAnonymous() {
    configureByFile("/refactoring/introduceField/beforeEnclosingAnonymous.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_CONSTRUCTOR, false);
    checkResultByFile("/refactoring/introduceField/afterEnclosingAnonymous.java");
  }

  public void testLocalVarAnnotations() {
    configureByFile("/refactoring/introduceField/beforeLocalVarAnnotations.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterLocalVarAnnotations.java");
  }

  public void testFromLambdaExpr() {
    configureByFile("/refactoring/introduceField/beforeFromLambdaExpr.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterFromLambdaExpr.java");
  }

  public void testSimplifyDiamond() {
    configureByFile("/refactoring/introduceField/beforeSimplifiedDiamond.java");
    performRefactoring(BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION, false);
    checkResultByFile("/refactoring/introduceField/afterSimplifiedDiamond.java");
  }

  public void testStaticFieldInInnerClass() {
    configureByFile("/refactoring/introduceField/beforeStaticFieldInInnerClass.java");
    new MockIntroduceFieldHandler(BaseExpressionToFieldHandler.InitializationPlace.IN_CURRENT_METHOD, false) {
      @Override
      protected int getChosenClassIndex(List<PsiClass> classes) {
        return 0;
      }
    }.invoke(getProject(), myEditor, myFile, null);
    checkResultByFile("/refactoring/introduceField/afterStaticFieldInInnerClass.java");
  }

  private static void performRefactoring(BaseExpressionToFieldHandler.InitializationPlace initializationPlace, boolean declareStatic) {
    new MockIntroduceFieldHandler(initializationPlace, declareStatic).invoke(getProject(), myEditor, myFile, null);
  }
}
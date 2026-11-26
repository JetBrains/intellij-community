// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.refactoring.introduceField.JavaIntroduceFieldModCommandService;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class IntroduceFieldInSameClassTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData/refactoring/introduceField/");

  @Test
  public void testInClassInitializer() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, true);
  }

  @Test
  public void testConflictingFieldInContainingClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("beforeConflictingFieldInContainingClass.java");
    myFixture.runInCommand(() -> {
      new MockIntroduceFieldHandler(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false) {
        @Override
        protected String getNewName(Project project, PsiExpression expr, PsiType type) {
          return "aField";
        }
      }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResultByFile("afterConflictingFieldInContainingClass.java");
    });
  }

  @Test
  public void testConflictingFieldInContainingClassLocal() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("beforeConflictingFieldInContainingClassLocal.java");
    myFixture.runInCommand(() -> {
      new MockIntroduceFieldHandler(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false) {
        @Override
        protected String getNewName(Project project, PsiExpression expr, PsiType type) {
          return "aField";
        }

        @Override
        protected int getChosenClassIndex(List<PsiClass> classes) {
          return 0;
        }
      }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResultByFile("afterConflictingFieldInContainingClassLocal.java");
    });
  }

  @Test
  public void testElseClause() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_CURRENT_METHOD, true);
  }

  @Test
  public void testOuterClass() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_CONSTRUCTOR, false);
  }

  @Test
  public void testConflictingConstructorParameter() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_CONSTRUCTOR, false);
  }

  @Test
  public void testOnClassLevelNoDuplicates() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  @Test
  public void testOnClassLevelDuplicates() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  @Test
  public void testOnClassLevelDuplicates1() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  @Test
  public void testOnClassLevelBinary() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  //multiple error elements on class level corresponding to the extracted fragment ------------------
  @Test
  public void testOnClassLevelNewExpression() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  @Test
  public void testOnClassLevelClassForName() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }
  //-------------------------------------------------------------------------------------------------

  @Test
  public void testUnresolvedReferenceToLocalVar() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_CURRENT_METHOD, false);
  }

  @Test
  public void testForcedFieldType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("beforeForcedFieldType.java");
    myFixture.runInCommand(() -> {
      new MockIntroduceFieldHandler(JavaIntroduceFieldModCommandService.InitializationPlace.IN_CURRENT_METHOD, false) {
        @Override
        protected PsiType getFieldType(PsiType type) {
          return PsiTypes.intType();
        }
      }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResultByFile("afterForcedFieldType.java");
    });
  }

  @Test
  public void testRejectIntroduceFieldFromExprInThisCall() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("beforeRejectIntroduceFieldFromExprInThisCall.java");
    myFixture.runInCommand(() -> {
      try {
        performRefactoring(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
        fail("Should not proceed");
      }
      catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
        assertEquals("Cannot perform refactoring.\nInvalid expression context.", e.getMessage());
      }
    });
  }

  @Test
  public void testRejectFieldFromLocal() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("beforeRejectFieldFromLocal.java");
    myFixture.runInCommand(() -> {
      try {
        performRefactoring(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
        fail("Should not proceed");
      }
      catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
        assertEquals("Cannot perform refactoring.\n" +
                     "Local class <b><code>Local</code></b> is not visible to members of class <b><code>K</code></b>", e.getMessage());
      }
    });
  }

  @Test
  public void testStaticFieldInRecord() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, true);
  }

  @Test
  public void testAcceptIntroduceFieldFromExprInThisCall() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, true);
  }

  @Test
  public void testEnclosingAnonymous() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_CONSTRUCTOR, false);
  }

  @Test
  public void testLocalVarAnnotations() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  @Test
  public void testFromLambdaExpr() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  @Test
  public void testSimplifiedDiamond() {
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  @Test
  public void testIncompleteInClassContext1(){
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  @Test
  public void testIncompleteInClassContext2(){
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  @Test
  public void testIncompleteInClassContext3(){
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  @Test
  public void testIncompleteInClassContext4(){
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  @Test
  public void testIncompleteInClassContext5(){
    doTest(JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, false);
  }

  @Test
  public void testStaticFieldInInnerClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("beforeStaticFieldInInnerClass.java");
    myFixture.runInCommand(() -> {
      new MockIntroduceFieldHandler(JavaIntroduceFieldModCommandService.InitializationPlace.IN_CURRENT_METHOD, false) {
        @Override
        protected int getChosenClassIndex(List<PsiClass> classes) {
          return 0;
        }
      }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResultByFile("afterStaticFieldInInnerClass.java");
    });
  }

  private void performRefactoring(JavaIntroduceFieldModCommandService.InitializationPlace initializationPlace, boolean declareStatic) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    new MockIntroduceFieldHandler(initializationPlace, declareStatic).invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
  }

  private void doTest(JavaIntroduceFieldModCommandService.InitializationPlace initializationPlace, boolean declareStatic) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("before" + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      performRefactoring(initializationPlace, declareStatic);
      myFixture.checkResultByFile("after" + myFixture.getTestName(false) + ".java");
    });
  }
}
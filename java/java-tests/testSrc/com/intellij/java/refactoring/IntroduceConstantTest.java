// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.refactoring.introduceField.JavaIntroduceFieldModCommandService;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestDataPath("$CONTENT_ROOT/testData")
@TestApplication
public class IntroduceConstantTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(JAVA_21_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  private static final @NonNls String BASE_PATH = "/refactoring/introduceConstant/";

  @Test
  public void testInNonNls() { doTest(false); }
  @Test
  public void testChooseStaticContainer() { doTest(false); }
  
  @Test
  public void testNonStaticContainerForCompileTimeConstant() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      PsiLocalVariable local = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
      new MockLocalToFieldHandler(myFixture.getProject(), true, false) {
        @Override
        protected int getChosenClassIndex(List<PsiClass> classes) {
          return 0;
        }
      }
      .convertLocalToField(local, myFixture.getEditor());
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testNonStaticContainerForCompileTimeConstant2() { doTest(); }
  @Test
  public void testStaticFieldInAnonymous() { doTest(); }
  @Test
  public void testStaticFieldInAnonymousJava8() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get(); IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> doTest()); }

  private void doTest(boolean makeEnumConstant) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      PsiLocalVariable local = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
      new MockLocalToFieldHandler(myFixture.getProject(), true, makeEnumConstant).convertLocalToField(local, myFixture.getEditor());
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testFromEnumConstantInitializer() { doTest(); }

  @Test
  public void testUnresolvedReferenceInEnum() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      new MockIntroduceConstantHandler(((PsiJavaFile)myFixture.getFile()).getClasses()[0]).invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testFromEnumConstantInitializer1() { doTest(); }

  @Test
  public void testFromEnumConstantInitializer2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      new MockIntroduceConstantHandler(((PsiJavaFile)myFixture.getFile()).getClasses()[0]).invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testEnumConstant() {
    doTest(true);
  }

  @Test
  public void testAnonymousClassWithThrownClause() { doTest(); }
  @Test
  public void testAnnotationDescription() { doTest(); }
  @Test
  public void testEnumConstantInAnnotationAttribute() { doTestExtractingEnumConstantFromEnumAnnotationValue(); }
  @Test
  public void testEnumConstantInAnnotationDefaultValue() { doTestExtractingEnumConstantFromEnumAnnotationValue(); }

  private void doTestExtractingEnumConstantFromEnumAnnotationValue() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      try {
        new IntroduceConstantHandler().invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
        fail();
      }
      catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
        assertEquals("Cannot perform refactoring.\n" +
                     "Constant not allowed as annotation enum value.", e.getMessage());
      }
    });
  }

  @Test
  public void testReplaceAllSkipsEnumConstantsInAnnotationAttributes() { doTest(); }
  @Test
  public void testReplaceAllSkipsEnumConstantsInAnnotationDefaultValues() { doTest(); }
  @Test
  public void testNoExternalTypeAnnotations() { doTest(); }
  @Test
  public void testTailingErrorUnacceptableWholeLineSelection() { doTest(); }
  @Test
  public void testPartialStringLiteral() { doTest(); }
  @Test
  public void testPartialStringLiteralConvertibleToInt() { doTest(); }
  @Test
  public void testStringLiteralConvertibleToInt() { doTest(); }

  @Test
  public void testPartialStringLiteralQualified() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      final PsiClass psiClass = ((PsiJavaFile)myFixture.getFile()).getClasses()[0];
      assertNotNull(psiClass);
      final PsiClass targetClass = psiClass.findInnerClassByName("D", false);
      assertNotNull(targetClass);
      new MockIntroduceConstantHandler(targetClass).invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testPartialStringLiteralAnchor() { doTest(); }
  @Test
  public void testPartialStringLiteralAnchorFromAnnotation() { doTest(); }
  @Test
  public void testIntroduceConstantFromThisCall() { doTest(); }
  @Test
  public void testForwardReferences() { doTest(); }
  @Test
  public void testArrayFromVarargs() { doTest(); }
  @Test
  public void testWithMethodReferenceBySecondSearch() { doTest(); }
  @Test
  public void testShortenClassReferences() { doTest(); }
  @Test
  public void testComments() { doTestExpr(); }

  private void doTestExpr() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      checkDefaultType(CommonClassNames.JAVA_LANG_STRING);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testContainingClass() { doTestExpr(); }
  @Test
  public void testConstantFromAnnotationOnFieldWithoutInitializer() { doTestExpr(); }

  @Test
  public void testEscalateVisibility() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      final PsiClass[] classes = ((PsiJavaFile)myFixture.getFile()).getClasses();
      assertEquals(2, classes.length);
      final PsiClass targetClass = classes[1];
      assertNotNull(targetClass);
      new MockIntroduceConstantHandler(targetClass){
        @Override
        protected String getVisibility() {
          return VisibilityUtil.ESCALATE_VISIBILITY;
        }
      }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testResultedType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      try {
        checkDefaultType(CommonClassNames.JAVA_LANG_OBJECT);
        fail();
      } catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
        assertEquals("Cannot perform refactoring.\n" +
                     "Local class <b><code>C</code></b> is not visible to members of class <b><code>Test</code></b>", e.getMessage());
      }
    });
  }

  @Test
  public void testResultedTypeWhenNonLocal() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.runInCommand(() -> {
      checkDefaultType("Test.C");
    });
  }

  private void checkDefaultType(String expectedType) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null){
      @Override
      protected Settings showRefactoringDialog(Project project,
                                               Editor editor,
                                               PsiClass parentClass,
                                               PsiExpression expr,
                                               PsiType type,
                                               PsiExpression[] occurrences,
                                               PsiElement anchorElement,
                                               PsiElement anchorElementIfAll) {
        final TypeSelectorManagerImpl selectorManager =
          new TypeSelectorManagerImpl(project, type, PsiTreeUtil.getParentOfType(anchorElement, PsiMethod.class), expr, occurrences);
        final PsiType psiType = selectorManager.getDefaultType();
        assertEquals(psiType.getCanonicalText(), expectedType);
        return new Settings("xxx", expr, occurrences, true, true, true,
                            JavaIntroduceFieldModCommandService.InitializationPlace.IN_FIELD_DECLARATION, getVisibility(), null, psiType, false,
                            parentClass, false, false);
      }
    }.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      new MockIntroduceConstantHandler(null).invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile(), null);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }
}

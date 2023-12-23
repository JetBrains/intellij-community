// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@TestDataPath("$CONTENT_ROOT/testData")
public class IntroduceConstantTest extends LightJavaCodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/refactoring/introduceConstant/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInNonNls() {
    doTest(false);
  }

  public void testChooseStaticContainer() {
    doTest(false);
  }
  
  public void testNonStaticContainerForCompilerConstant() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    PsiLocalVariable local = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
    new MockLocalToFieldHandler(getProject(), true, false){
      @Override
      protected int getChosenClassIndex(List<PsiClass> classes) {
        return 0;
      }
    }
    .convertLocalToField(local, getEditor());
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  private void doTest(boolean makeEnumConstant) {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    convertLocal(makeEnumConstant);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testFromEnumConstantInitializer() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testUnresolvedReferenceInEnum() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }
  
  public void testFromEnumConstantInitializer1() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testFromEnumConstantInitializer2() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testEnumConstant() {
    doTest(true);    
  }

  public void testAnonymousClassWithThrownClause() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testAnnotationDescription() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testTailingErrorUnacceptableWholeLineSelection() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  private void convertLocal(final boolean makeEnumConstant) {
    PsiLocalVariable local = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
    new MockLocalToFieldHandler(getProject(), true, makeEnumConstant).convertLocalToField(local, getEditor());
  }

  public void testPartialStringLiteral() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testPartialStringLiteralConvertibleToInt() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testStringLiteralConvertibleToInt() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testPartialStringLiteralQualified() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    final PsiClass psiClass = ((PsiJavaFile)getFile()).getClasses()[0];
    assertNotNull(psiClass);
    final PsiClass targetClass = psiClass.findInnerClassByName("D", false);
    assertNotNull(targetClass);
    new MockIntroduceConstantHandler(targetClass).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testPartialStringLiteralAnchor() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testPartialStringLiteralAnchorFromAnnotation() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testIntroduceConstantFromThisCall() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testForwardReferences() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testArrayFromVarargs() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testWithMethodReferenceBySecondSearch() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testComments() {
    doTestExpr();
  }

  private void doTestExpr() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    checkDefaultType(CommonClassNames.JAVA_LANG_STRING);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testContainingClass() {
    doTestExpr();
  }

  public void testConstantFromAnnotationOnFieldWithoutInitializer() {
    doTestExpr();
  }

  public void testEscalateVisibility() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    final PsiClass[] classes = ((PsiJavaFile)getFile()).getClasses();
    assertEquals(2, classes.length);
    final PsiClass targetClass = classes[1];
    assertNotNull(targetClass);
    new MockIntroduceConstantHandler(targetClass){
      @Override
      protected String getVisibility() {
        return VisibilityUtil.ESCALATE_VISIBILITY;
      }
    }.invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testResultedType() {
    try {
      checkDefaultType(CommonClassNames.JAVA_LANG_OBJECT);
      fail();
    } catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals("Cannot perform refactoring.\n" +
                   "Local class <b><code>C</code></b> is not visible to members of class <b><code>Test</code></b>", e.getMessage());
    }
  }

  public void testResultedTypeWhenNonLocal() {
    checkDefaultType("Test.C");
  }

  private void checkDefaultType(final String expectedType) {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
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
                            InitializationPlace.IN_FIELD_DECLARATION, getVisibility(), null, psiType, false,
                            parentClass, false, false);
      }
    }.invoke(getProject(), getEditor(), getFile(), null);
  }
}

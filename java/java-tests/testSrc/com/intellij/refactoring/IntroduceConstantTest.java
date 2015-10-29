package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.VisibilityUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
@TestDataPath("$CONTENT_ROOT/testData")
public class IntroduceConstantTest extends LightCodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/refactoring/introduceConstant/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInNonNls() throws Exception {
    doTest(false);
  }

  private void doTest(boolean makeEnumConstant) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    convertLocal(makeEnumConstant);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testFromEnumConstantInitializer() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }
  
  public void testFromEnumConstantInitializer1() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testEnumConstant() throws Exception {
    doTest(true);    
  }

  public void testAnnotationDescription() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testTailingErrorUnacceptableWholeLineSelection() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  private static void convertLocal(final boolean makeEnumConstant) {
    PsiLocalVariable local = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
    new MockLocalToFieldHandler(getProject(), true, makeEnumConstant).convertLocalToField(local, getEditor());
  }

  public void testPartialStringLiteral() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testPartialStringLiteralQualified() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    final PsiClass psiClass = ((PsiJavaFile)getFile()).getClasses()[0];
    Assert.assertNotNull(psiClass);
    final PsiClass targetClass = psiClass.findInnerClassByName("D", false);
    Assert.assertNotNull(targetClass);
    new MockIntroduceConstantHandler(targetClass).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testPartialStringLiteralAnchor() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testPartialStringLiteralAnchorFromAnnotation() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testIntroduceConstantFromThisCall() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testForwardReferences() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testArrayFromVarargs() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    new MockIntroduceConstantHandler(null).invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testComments() throws Exception {
    doTestExpr();
  }

  private void doTestExpr() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    checkDefaultType(CommonClassNames.JAVA_LANG_STRING);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testContainingClass() throws Exception {
    doTestExpr();
  }

  public void testEscalateVisibility() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    final PsiClass[] classes = ((PsiJavaFile)getFile()).getClasses();
    Assert.assertTrue(classes.length == 2);
    final PsiClass targetClass = classes[1];
    Assert.assertNotNull(targetClass);
    new MockIntroduceConstantHandler(targetClass){
      @Override
      protected String getVisibility() {
        return VisibilityUtil.ESCALATE_VISIBILITY;
      }
    }.invoke(getProject(), getEditor(), getFile(), null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testResultedType() throws Exception {
    checkDefaultType(CommonClassNames.JAVA_LANG_OBJECT);
  }

  public void testResultedTypeWhenNonLocal() throws Exception {
    checkDefaultType("Test.C");
  }

  private void checkDefaultType(final String expectedType) throws Exception {
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
        Assert.assertEquals(psiType.getCanonicalText(), expectedType);
        return new Settings("xxx", expr, occurrences, true, true, true,
                            InitializationPlace.IN_FIELD_DECLARATION, getVisibility(), null, psiType, false,
                         parentClass, false, false);
      }
    }.invoke(getProject(), getEditor(), getFile(), null);
  }
}

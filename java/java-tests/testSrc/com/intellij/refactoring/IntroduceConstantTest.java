package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.VisibilityUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
@TestDataPath("$CONTENT_ROOT/testData")
public class IntroduceConstantTest extends LightCodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/refactoring/introduceConstant/";

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

  public void testEnumConstant() throws Exception {
    doTest(true);    
  }

  public void testAnnotationDescription() throws Exception {
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
    checkDefaultType("java.lang.Object");
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
                                               PsiExpression[] occurences,
                                               PsiElement anchorElement,
                                               PsiElement anchorElementIfAll) {
        final TypeSelectorManagerImpl selectorManager =
          new TypeSelectorManagerImpl(project, type, PsiTreeUtil.getParentOfType(anchorElement, PsiMethod.class), expr, occurences);
        final PsiType psiType = selectorManager.getDefaultType();
        Assert.assertEquals(psiType.getCanonicalText(), expectedType);
        return new Settings("xxx", true, true, true,
                            InitializationPlace.IN_FIELD_DECLARATION, getVisibility(), null, psiType, false,
                         parentClass, false, false);
      }
    }.invoke(getProject(), getEditor(), getFile(), null);
  }

  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }
}
package com.intellij.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 * @author ven
 */
public class InlineLocalTest extends LightCodeInsightTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk15("java 1.5");
  }

  public void testInference () throws Exception {
    doTest(false);
  }

  public void testQualifier () throws Exception {
    doTest(false);
  }

  public void testIDEADEV950 () throws Exception {
    doTest(false);
  }

  public void testNoRedundantCasts () throws Exception {
    doTest(false);
  }

  public void testIdeaDEV9404 () throws Exception {
    doTest(false);
  }

  public void testIDEADEV12244 () throws Exception {
    doTest(false);
  }

  public void testIDEADEV10376 () throws Exception {
    doTest(true);
  }

  public void testIDEADEV13151 () throws Exception {
    doTest(true);
  }

  public void testArrayInitializer() throws Exception {
    doTest(false);
  }

  public void testNonWriteUnaryExpression() throws Exception {
    doTest(true);
  }

  public void testNewExpression() throws Exception {
    doTest(false);
  }

  public void testAugmentedAssignment() throws Exception {
    String exception = null;
    try {
      doTest(false);
    }
    catch(RuntimeException ex) {
      exception = ex.getMessage();
    }
    String error = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("variable.is.accessed.for.writing", "text"));
    assertEquals(error, exception);
  }

  public void testUsedInInnerClass() throws Exception {       // IDEADEV-28786
    doTest(true);
  }

  public void testUsedInInnerClass2() throws Exception {       // IDEADEV-28786
    doTest(true);
  }

  public void testUsedInInnerClass3() throws Exception {       // IDEADEV-28786
    doTest(true);
  }

  public void testUsedInInnerClass4() throws Exception {       // IDEADEV-28786
    doTest(true);
  }

  public void testAnotherDefinitionUsed() throws Exception {
    doTest(true, "Cannot perform refactoring.\nAnother variable 'bar' definition is used together with inlined one.");
  }

  public void testAnotherDefinitionUsed1() throws Exception {
    doTest(false, "Cannot perform refactoring.\nAnother variable 'bar' definition is used together with inlined one.");
  }

  public void testTypeArgumentsStatic() throws Exception {
    doTest(true);
  }

  public void testTypeArguments() throws Exception {
    doTest(true);
  }

  public void testWildcard() throws Exception {
    doTest(true);
  }

  private void doTest(final boolean inlineDef, String conflictMessage) throws Exception {
    try {
      doTest(inlineDef);
      fail("Conflict was not detected");
    }
    catch (RuntimeException e) {
      assertEquals(e.getMessage(), conflictMessage);
    }
  }


  private void doTest(final boolean inlineDef) throws Exception {
    String name = getTestName(false);
    String fileName = "/refactoring/inlineLocal/" + name + ".java";
    configureByFile(fileName);
    if (!inlineDef) {
      performInline(getProject(), myEditor);
    }
    else {
      performDefInline(getProject(), myEditor);
    }
    checkResultByFile(fileName + ".after");
  }

  public static void performInline(Project project, Editor editor) {
    PsiElement element = TargetElementUtilBase
      .findTargetElement(editor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assertTrue(element instanceof PsiLocalVariable);

    InlineLocalHandler.invoke(project, editor, (PsiLocalVariable)element, null);
  }

  public static void performDefInline(Project project, Editor editor) {
    PsiReference reference = TargetElementUtilBase.findReference(editor);
    assertTrue(reference instanceof PsiReferenceExpression);
    final PsiElement local = reference.resolve();
    assertTrue(local instanceof PsiLocalVariable);

    InlineLocalHandler.invoke(project, editor, (PsiLocalVariable)local, (PsiReferenceExpression)reference);
  }
}

package com.intellij.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.MockInlineMethodOptions;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NonNls;

public class InlineMethodTest extends LightCodeInsightTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInlineParms() throws Exception {
    doTest();
  }

  public void testInlineWithQualifier() throws Exception {
    doTest();
  }

  public void testInlineWithQualifierFromSuper() throws Exception { doTest(); }
  public void testTry() throws Exception {
    doTest();
  }

  public void testTrySynchronized() throws Exception {
    doTest();
  }

  public void testSuperInsideHierarchy() throws Exception {
    doTest();
  }

  public void testSideEffect() throws Exception { doTest(); }

  public void testInlineWithTry() throws Exception { doTest(); }

  public void testVoidWithReturn() throws Exception { doTest(); }
  public void testVoidWithReturn1() throws Exception { doTest(); }

  public void testScr10884() throws Exception {
    doTest();
  }
  public void testFinalParameters() throws Exception { doTest(); }
  public void testFinalParameters1() throws Exception { doTest(); }

  public void testScr13831() throws Exception { doTest(); }

  public void testNameClash() throws Exception { doTest(); }

  public void testArrayAccess() throws Exception { doTest(); }

  public void testConflictingField() throws Exception { doTest(); }

  public void testCallInFor() throws Exception { doTest(); }

  public void testSCR20655() throws Exception { doTest(); }


  public void testFieldInitializer() throws Exception { doTest(); }

  public void testMethodCallInOtherAnonymousOrInner() throws Exception { doTest(); }

  public void testStaticFieldInitializer() throws Exception { doTest(); }
  public void testSCR22644() throws Exception { doTest(); }

  public void testCallUnderIf() throws Exception { doTest(); }

  //This gives extra 'result' local variable, currently I don't see a way to cope with it, todo: think about addional inline possibilities
  //public void testLocalVariableResult() throws Exception { doTest(); }

  public void testSCR31093() throws Exception { doTest(); }

  public void testSCR37742() throws Exception { doTest(); }
  
  public void testChainingConstructor() throws Exception { doTest(); }

  public void testChainingConstructor1() throws Exception { doTest(); }

  public void testNestedCall() throws Exception { doTest(); }

  public void testIDEADEV3672() throws Exception { doTest(); }

  public void testIDEADEV5806() throws Exception { doTest(); }

  public void testIDEADEV6807() throws Exception { doTest(); }

  public void testIDEADEV12616() throws Exception { doTest(); }

  public void testVarargs() throws Exception { doTest(); }

  public void testVarargs1() throws Exception { doTest(); }

  public void testFlatVarargs() throws Exception {doTest();}
  public void testFlatVarargs1() throws Exception {doTest();}

  public void testEnumConstructor() throws Exception { doTest(); }

  public void testEnumConstantConstructorParameter() throws Exception {  // IDEADEV-26133
    doTest(); 
  }

  public void testEnumConstantConstructorParameterComplex() throws Exception {  // IDEADEV-26133
    doTest();
  }

  public void testEnumConstantConstructorParameterComplex2() throws Exception {  // IDEADEV-26133
    doTest();
  }

  public void testConstantInChainingConstructor() throws Exception {   // IDEADEV-28136
    doTest();
  }

  public void testReplaceParameterWithArgumentForConstructor() throws Exception {   // IDEADEV-23652
    doTest();
  }

  public void testTailCallReturn() throws Exception {  // IDEADEV-27983
    doTest();
  }

  public void testTailCallSimple() throws Exception {  // IDEADEV-27983
    doTest();
  }

  public void testTailComment() throws Exception {   //IDEADEV-33638
    doTest();
  }

  public void testInferredType() throws Exception {
    doTest();
  }

  public void testReplaceGenericsInside() throws Exception {
    doTest();
  }

  public void testStaticMethodWithoutParams() throws Exception {
    doTest();
  }

  public void testWithSuperInside() throws Exception {
    doTest();
  }

  public void testRawSubstitution() throws Exception {
    doTest();
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }

  private void doTest() throws Exception {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineMethod/" + name + ".java";
    configureByFile(fileName);
    performAction();
    checkResultByFile(fileName + ".after");
  }

  private void performAction() {
    PsiElement element = TargetElementUtilBase
      .findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    final PsiReference ref = myFile.findReferenceAt(myEditor.getCaretModel().getOffset());
    PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;
    assertTrue(element instanceof PsiMethod);
    PsiMethod method = (PsiMethod)element;
    final boolean condition = InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method);
    assertFalse("Bad returns found", condition);
    InlineOptions options = new MockInlineMethodOptions();
    final InlineMethodProcessor processor = new InlineMethodProcessor(getProject(), method, refExpr, myEditor, options.isInlineThisOnly());
    processor.run();
  }
}

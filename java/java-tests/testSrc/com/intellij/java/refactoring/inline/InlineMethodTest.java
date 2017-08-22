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
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.refactoring.LightRefactoringTestCase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.MockInlineMethodOptions;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.inline.InlineOptions;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class InlineMethodTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testInlineParms() {
    doTest();
  }

  public void testInlineWithQualifier() {
    doTest();
  }

  public void testInlineWithQualifierFromSuper() { doTest(); }
  public void testTry() {
    doTest();
  }

  public void testTrySynchronized() {
    doTest();
  }

  public void testStaticSynchronized() {
    doTest();
  }

  public void testSuperInsideHierarchy() {
    doTest();
  }

  public void testSideEffect() { doTest(); }

  public void testInlineWithTry() { doTest(); }

  public void testVoidWithReturn() { doTest(); }
  public void testVoidWithReturn1() { doTest(); }

  public void testScr10884() {
    doTest();
  }
  public void testFinalParameters() { doTest(); }
  public void testFinalParameters1() { doTest(); }

  public void testScr13831() { doTest(); }

  public void testNameClash() { doTest(); }

  public void testArrayAccess() { doTest(); }

  public void testConflictingField() { doTest(); }

  public void testCallInFor() { doTest(); }

  public void testSCR20655() { doTest(); }


  public void testFieldInitializer() { doTest(); }

  public void testMethodCallInOtherAnonymousOrInner() { doTest(); }

  public void testStaticFieldInitializer() { doTest(); }
  public void testSCR22644() { doTest(); }

  public void testCallUnderIf() { doTest(); }

  //This gives extra 'result' local variable, currently I don't see a way to cope with it, todo: think about addional inline possibilities
  //public void testLocalVariableResult() throws Exception { doTest(); }

  public void testSCR31093() { doTest(); }

  public void testSCR37742() { doTest(); }
  
  public void testChainingConstructor() { doTest(); }

  public void testChainingConstructor1() {
    BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(true);
    try {
      doTest();
    }
    finally {
      BaseRefactoringProcessor.ConflictsInTestsException.setTestIgnore(false);
    }
  }

  public void testNestedCall() { doTest(); }

  public void testIDEADEV3672() { doTest(); }

  public void testIDEADEV5806() { doTest(); }

  public void testIDEADEV6807() { doTest(); }

  public void testIDEADEV12616() { doTest(); }

  public void testVarargs() { doTest(); }

  public void testVarargs1() { doTest(); }

  public void testFlatVarargs() {doTest();}
  public void testFlatVarargs1() {doTest();}

  public void testEnumConstructor() { doTest(); }

  public void testEnumConstantConstructorParameter() {  // IDEADEV-26133
    doTest(); 
  }

  public void testEnumConstantConstructorParameterComplex() {  // IDEADEV-26133
    doTest();
  }

  public void testEnumConstantConstructorParameterComplex2() {  // IDEADEV-26133
    doTest();
  }

  public void testEnumConstantConstructorWithArgs() {
    doTest();
  }

  public void testConstantInChainingConstructor() {   // IDEADEV-28136
    doTest();
  }

  public void testReplaceParameterWithArgumentForConstructor() {   // IDEADEV-23652
    doTest();
  }

  public void testTailCallReturn() {  // IDEADEV-27983
    doTest();
  }

  public void testTailCallSimple() {  // IDEADEV-27983
    doTest();
  }

  public void testTailComment() {   //IDEADEV-33638
    doTest();
  }

  public void testInferredType() {
    setLanguageLevel(LanguageLevel.JDK_1_7);
    doTest();
  }

  public void testReplaceGenericsInside() {
    doTest();
  }

  public void testStaticMethodWithoutParams() {
    doTest();
  }

  public void testWithSuperInside() {
    doTest();
  }

  public void testRawSubstitution() {
    doTest();
  }
  
  public void testSubstitution() {
    doTest();
  }

  public void testSubstitutionForWildcards() {
    doTest();
  }

  public void testParamNameConflictsWithLocalVar() {
    doTest();
  }

  public void testArrayTypeInferenceFromVarargs() {
    doTest();
  }

  public void testSuperMethodInAnonymousClass() {
    doTest();
  }
  
  public void testInlineAnonymousClassWithPrivateMethodInside() {
    doTest();
  }

  public void testChainedConstructor() {
    doTestInlineThisOnly();
  }

  public void testChainedConstructor1() {
    doTest();
  }

  public void testMethodUsedInJavadoc() {
    doTestConflict("Inlined method is used in javadoc");
  }

  public void testNotAStatement() {
    doTestConflict("Inlined result would contain parse errors");
  }


  public void testInSuperCall() {
    doTestConflict("Inline cannot be applied to multiline method in constructor call");
  }

  public void testMethodReferenceInsideMethodCall() {
    doTest();
  }

  private void doTestConflict(final String conflict) {
    try {
      doTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals(conflict, e.getMessage());
    }
  }

  public void testInlineRunnableRun() {
    doTestInlineThisOnly();
  }
  
  public void testOneLineLambdaVoidCompatibleToBlock() {
    doTestInlineThisOnly();
  }

  public void testOneLineLambdaValueCompatibleToBlock() {
    doTestInlineThisOnly();
  }

  public void testOneLineLambdaVoidCompatibleOneLine() {
    doTestInlineThisOnly();
  }
 
  public void testOneLineLambdaValueCompatibleOneLine() {
    doTestInlineThisOnly();
  }

  public void testOnMethodReference() {
    doTestInlineThisOnly();
  }

  public void testNonCodeUsage() {
    doTest(true);
  }

  public void testMethodInsideChangeIfStatement() {
    doTest();
  }

  public void testSameVarMethodNames() {
    doTest();
  }

  public void testThisNameConflict() {
    doTest();
  }

  public void testStringPlusOverload() {
    doTest();
  }
  
  public void testReturnStatementWithoutBraces() {
    doTestInlineThisOnly();
  }

  public void testUnresolvedArgPassedToSameNameParameter() {
    doTestInlineThisOnly();
  }

  public void testMakeTypesDenotable() {
    doTestInlineThisOnly();
  }

  public void testInlineIntoMethodRef() {
    doTestInlineThisOnly();
  }

  public void testInlineIntoConstructorRef() {
    doTestInlineThisOnly();
  }

  public void testSideEffectsInMethodRefQualifier() {
    doTestConflict("Inlined method is used in method reference with side effects in qualifier");
  }

  public void testRedundantCastOnMethodReferenceToLambda() {
    doTest();
  }

  public void testInaccessibleSuperCallWhenQualifiedInline() {
    doTestConflict("Inlined method calls super.bar() which won't be accessed in class <b><code>B</code></b>");
  }

  public void testSuperCallWhenUnqualifiedInline() {
    doTestInlineThisOnly();
  }

  public void testDeleteOverrideAnnotations() {
    doTest();
  }

  public void testNegativeArguments() {
    doTest();
  }

  public void testInaccessibleFieldInSuperClass() {
    doTestConflict("Field <b><code>A.i</code></b> that is used in inlined method is not accessible from call site(s) in method <b><code>B.bar()</code></b>");
  }

  public void testPrivateFieldInSuperClassInSameFile() {
    doTest();
  }

  public void testInlineMultipleOccurrencesInFieldInitializer() {
    doTest();
  }

  public void testAvoidMultipleSubstitutionInParameterTypes() {
    doTest();
  }

  public void testRespectProjectScopeSrc() {
    doTest();
  }

  public void testRespectProjectScopeSrcConstructorCall() {
    doTest();
  }

  public void testChainedConstructorWithMultipleStatements() {
    doTestInlineThisOnly();
  }

  public void testThisExpressionValidationForLocalClasses() {
    doTestInlineThisOnly();
  }

  @Override
  protected Sdk getProjectJDK() {
    return getTestName(false).contains("Src") ? IdeaTestUtil.getMockJdk17() : super.getProjectJDK();
  }

  private void doTestInlineThisOnly() {
    @NonNls String fileName = "/refactoring/inlineMethod/" + getTestName(false) + ".java";
    configureByFile(fileName);
    performAction(new MockInlineMethodOptions(){
      @Override
      public boolean isInlineThisOnly() {
        return true;
      }
    }, false);
    checkResultByFile(fileName + ".after");
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean nonCode) {
    String name = getTestName(false);
    @NonNls String fileName = "/refactoring/inlineMethod/" + name + ".java";
    configureByFile(fileName);
    performAction(nonCode);
    checkResultByFile(fileName + ".after");
  }

  private void performAction(final boolean nonCode) {
    performAction(new MockInlineMethodOptions(), nonCode);
  }

  private void performAction(final InlineOptions options, final boolean nonCode) {
    PsiElement element = TargetElementUtil
      .findTargetElement(myEditor, TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    final PsiReference ref = myFile.findReferenceAt(myEditor.getCaretModel().getOffset());
    if (ref instanceof PsiJavaCodeReferenceElement) {
      final PsiElement parent = ((PsiJavaCodeReferenceElement)ref).getParent();
      if (parent instanceof PsiNewExpression) {
        element = ((PsiNewExpression)parent).resolveConstructor();
      }
    }
    PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;
    assertTrue(element instanceof PsiMethod);
    PsiMethod method = (PsiMethod)element.getNavigationElement();
    final boolean condition = InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method);
    assertFalse("Bad returns found", condition);
    final InlineMethodProcessor processor =
      new InlineMethodProcessor(getProject(), method, refExpr, myEditor, options.isInlineThisOnly(), nonCode, nonCode,
                                !options.isKeepTheDeclaration());
    processor.run();
  }
}

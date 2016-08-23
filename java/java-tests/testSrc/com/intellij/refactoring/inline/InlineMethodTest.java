/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.LightRefactoringTestCase;
import com.intellij.refactoring.MockInlineMethodOptions;
import com.intellij.refactoring.util.InlineUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class InlineMethodTest extends LightRefactoringTestCase {
  @NotNull
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

  public void testStaticSynchronized() throws Exception {
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
    setLanguageLevel(LanguageLevel.JDK_1_7);
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
  
  public void testSubstitution() throws Exception {
    doTest();
  }

  public void testSubstitutionForWildcards() throws Exception {
    doTest();
  }

  public void testParamNameConflictsWithLocalVar() throws Exception {
    doTest();
  }

  public void testArrayTypeInferenceFromVarargs() throws Exception {
    doTest();
  }

  public void testSuperMethodInAnonymousClass() throws Exception {
    doTest();
  }
  
  public void testInlineAnonymousClassWithPrivateMethodInside() throws Exception {
    doTest();
  }

  public void testChainedConstructor() throws Exception {
    doTestInlineThisOnly();
  }

  public void testChainedConstructor1() throws Exception {
    doTest();
  }

  public void testMethodUsedInJavadoc() throws Exception {
    doTestConflict("Inlined method is used in javadoc");
  }

  public void testNotAStatement() throws Exception {
    doTestConflict("Inlined result would contain parse errors");
  }


  public void testInSuperCall() throws Exception {
    doTestConflict("Inline cannot be applied to multiline method in constructor call");
  }

  private void doTestConflict(final String conflict) throws Exception {
    try {
      doTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals(conflict, e.getMessage());
    }
  }

  public void testInlineRunnableRun() throws Exception {
    doTestInlineThisOnly();
  }
  
  public void testOneLineLambdaVoidCompatibleToBlock() throws Exception {
    doTestInlineThisOnly();
  }

  public void testOneLineLambdaValueCompatibleToBlock() throws Exception {
    doTestInlineThisOnly();
  }

  public void testOneLineLambdaVoidCompatibleOneLine() throws Exception {
    doTestInlineThisOnly();
  }
 
  public void testOneLineLambdaValueCompatibleOneLine() throws Exception {
    doTestInlineThisOnly();
  }

  public void testOnMethodReference() throws Exception {
    doTestInlineThisOnly();
  }

  public void testNonCodeUsage() throws Exception {
    doTest(true);
  }

  public void testMethodInsideChangeIfStatement() throws Exception {
    doTest();
  }

  public void testSameVarMethodNames() throws Exception {
    doTest();
  }

  public void testThisNameConflict() throws Exception {
    doTest();
  }

  public void testStringPlusOverload() throws Exception {
    doTest();
  }
  
  public void testReturnStatementWithoutBraces() throws Exception {
    doTestInlineThisOnly();
  }

  public void testUnresolvedArgPassedToSameNameParameter() throws Exception {
    doTestInlineThisOnly();
  }

  public void testMakeTypesDenotable() throws Exception {
    doTestInlineThisOnly();
  }

  public void testInlineIntoMethodRef() throws Exception {
    doTestInlineThisOnly();
  }

  public void testInlineIntoConstructorRef() throws Exception {
    doTestInlineThisOnly();
  }

  public void testSideEffectsInMethodRefQualifier() throws Exception {
    doTestConflict("Inlined method is used in method reference with side effects in qualifier");
  }

  public void testInaccessibleSuperCallWhenQualifiedInline() throws Exception {
    doTestConflict("Inlined method calls super.bar() which won't be accessed in class <b><code>B</code></b>");
  }

  public void testSuperCallWhenUnqualifiedInline() throws Exception {
    doTestInlineThisOnly();
  }

  public void testDeleteOverrideAnnotations() throws Exception {
    doTest();
  }

  public void testNegativeArguments() throws Exception {
    doTest();
  }

  public void testInaccessibleFieldInSuperClass() throws Exception {
    doTestConflict("Field <b><code>A.i</code></b> that is used in inlined method is not accessible from call site(s) in method <b><code>B.bar()</code></b>");
  }

  public void testPrivateFieldInSuperClassInSameFile() throws Exception {
    doTest();
  }

  public void testInlineMultipleOccurrencesInFieldInitializer() throws Exception {
    doTest();
  }

  public void testAvoidMultipleSubstitutionInParameterTypes() throws Exception {
    doTest();
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

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean nonCode) throws Exception {
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
    PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;
    assertTrue(element instanceof PsiMethod);
    PsiMethod method = (PsiMethod)element;
    final boolean condition = InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method);
    assertFalse("Bad returns found", condition);
    final InlineMethodProcessor processor =
      new InlineMethodProcessor(getProject(), method, refExpr, myEditor, options.isInlineThisOnly(), nonCode, nonCode);
    processor.run();
  }
}

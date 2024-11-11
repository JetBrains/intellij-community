// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.refactoring.LightRefactoringTestCase;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.inline.InlineMethodHandler;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.containers.ContainerUtil;
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

  public void testParamAsAutocloseableRef() { doTest(); }

  public void testInlineWithTry() { doTest(); }
  public void testEmptyMethod() { doTest(); }

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
  public void testGenericArrayCreation() { doTest(); }
  public void testNoRedundantCast() { doTest(); }
  public void testFieldInitializer() { doTest(); }

  public void testMethodCallInOtherAnonymousOrInner() { doTest(); }

  public void testStaticFieldInitializer() { doTest(); }
  public void testSCR22644() { doTest(); }
  public void testChangeContextForThisInNestedClasses() { doTest(); }

  public void testCallUnderIf() { doTest(); }
  public void testInlineEnumArgsChangeContext() { doTest(); }

  //This gives extra 'result' local variable, currently I don't see a way to cope with it, todo: think about addional inline possibilities
  //public void testLocalVariableResult() throws Exception { doTest(); }

  public void testSCR31093() { doTest(); }

  public void testSCR37742() { doTest(); }

  public void testChainingConstructor() { doTest(); }

  public void testChainingConstructor1() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(()->doTest());
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

  public void testEnumConstantConstructorParameterNestedLambda() {
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

  public void testChainedConstructorWithSpacesInvalidation() {
    doTest();
  }

  public void testChainedConstructor1() {
    doTest();
  }

  public void testMethodUsedInJavadoc() {
    doTestConflict("Inlined method is used in javadoc");
  }

  public void testMethodUsedReflectively() {
    doTestConflict("Inlined method is used reflectively");
  }

  public void testInlineOnelinerToCondition() {
    doTest();
  }

  public void testNotAStatement() {
    doTest();
  }

  public void testNotAStatement2() {
    doTest();
  }

  public void testNotAStatement3() {
    doTest();
  }

  public void testNotAStatement4() {
    doTest();
  }

  public void testForContinue() {
    doTest();
  }

  public void testSingleReturn1() {
    doTestAssertBadReturn();
  }

  public void testSingleReturn1NotFinal() {
    doTestAssertBadReturn();
  }

  public void testSingleReturn2() {
    doTestAssertBadReturn();
  }

  public void testInSuperCall() {
    doTestConflict("Inline cannot be applied to multiline method in constructor call");
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_22_PREVIEW, () -> doTest());
  }

  public void testMethodReferenceInsideMethodCall() {
    doTest();
  }

  public void testVolatilePassed() {
    doTest();
  }

  public void testBooleanConstantArgument() {
    doTest();
  }

  public void testBooleanConstantArgument2() {
    doTest();
  }

  private void doTestConflict(final String conflict) {
    try {
      doTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException | CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(conflict, e.getMessage());
    }
  }

  public void testInlineRunnableRun() {
    doTestInlineThisOnly();
  }

  public void testPreserveLeadingTailingComments() {
    doTestInlineThisOnly();
  }

  public void testSkipEmptyMethod() {
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

  public void testOnLambda() {
    doTestInlineThisOnly();
  }

  public void testNonCodeUsage() {
    doTestNonCode();
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

  public void testConcatenationInConcatenation() {
    doTest();
  }

  public void testConcatenationTurnsIntoAddition() {
    doTest();
  }

  public void testAdditionTurnsIntoConcatenation() {
    doTest();
  }

  public void testReturnStatementWithoutBraces() {
    doTestInlineThisOnly();
  }

  public void testIfElseIfWithSingleStatement() {
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

  public void testUnableToInlineCodeBlockToSuper() {
    doTestConflict("Inline cannot be applied to multiline method in constructor call");
  }

  public void testRedundantCastOnMethodReferenceToLambda() {
    doTest();
  }

  public void testInaccessibleSuperCallWhenQualifiedInline() {
    doTestConflict("Inlined method calls super.bar() which won't be accessed in class <b><code>B</code></b>");
  }

  public void testInaccessibleSuperCallWhenQualifiedInInheritor() {
    doTestConflict("Inlined method calls super.foo() which won't be accessible on qualifier c");
  }

  public void testInaccessibleConstructorInInlinedMethod() {
    doTestConflict("Constructor <b><code>SomeClass.SomeClass()</code></b> will not be accessible when method " +
                   "<b><code>SomeClass.createInstance()</code></b> is inlined into method " +
                   "<b><code>InlineWithPrivateConstructorAccessMain.main(String...)</code></b>");
  }

  public void testPreserveResultedVariableIfInitializerIsNotSideEffectsFree() {
    doTestInlineThisOnly();
  }

  public void testExprLambdaExpandToCodeBlock() {
    doTestInlineThisOnly();
  }

  public void testSuperCallWhenUnqualifiedInline() {
    doTestInlineThisOnly();
  }

  public void testRemoveReturnForTailTypeSimpleWhenNoSideEffectsPossible() {
    doTestInlineThisOnly();
  }

  public void testDeleteOverrideAnnotations() {
    doTest();
  }

  public void testNegativeArguments() {
    doTest();
  }

  public void testInaccessibleFieldInSuperClass() {
    doTestConflict("Field <b><code>A.i</code></b> will not be accessible when method <b><code>A.foo()</code></b> is inlined into " +
                   "method <b><code>B.bar()</code></b>");
  }

  public void testPrivateFieldInSuperClassInSameFile() {
    doTest();
  }

  public void testWidenArgument() {
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

  public void testTailCallInsideIf() {
    doTest();
  }

  public void testTailCallInsideLambda() {
    doTest();
  }

  public void testChainedBuilderCall() {
    doTest();
  }

  public void testMissedQualifierWithSideEffectsOnInliningEmptyMethod() {
    doTest();
  }

  public void testNotTailCallInsideIf() {
    doTestAssertBadReturn();
  }

  public void testConvertToSingleReturnWithFinished() {
    doTestAssertBadReturn();
  }

  public void testConvertToSingleReturnWithFinishedUnusedResult() {
    doTestAssertBadReturn();
  }

  public void testUnusedResult() {
    doTest();
  }

  public void testReuseResultVar() {
    doTest();
  }

  public void testSpecializeClassGetName() {
    doTest();
  }

  public void testSpecializeEnumName() {
    doTest();
  }

  public void testSpecializeEnumValueOf() {
    doTest();
  }

  public void testBooleanModelSimple() {
    doTestAssertBadReturn();
  }

  public void testBooleanModelMultiReturns() {
    doTestAssertBadReturn();
  }

  public void testBooleanModelIfElse() {
    doTestAssertBadReturn();
  }

  public void testBooleanModelIfElse2() {
    doTestAssertBadReturn();
  }

  public void testBooleanModelContinue() {
    doTestAssertBadReturn();
  }

  public void testBooleanModelFinalCondition() {
    doTestAssertBadReturn();
  }

  public void testInvertMethod() {
    doTest();
  }

  public void testUnusedParameter() {
    doTest();
  }

  public void testEnumStaticMethod() {
    doTest();
  }

  public void testTypeParameterMethodRefArgument() {
    doTest();
  }

  public void testIgnoreReturnValue() {
    doTest();
  }

  public void testSingleReturnComplexQualifier() {
    doTestAssertBadReturn();
  }

  public void testAnonymousCall() { doTest(); }
  public void testInSwitchExpression() { doTest(); }
  public void testInSwitchExpressionYield() { doTest(); }

  public void testAndChain() { doTest(); }
  public void testAndChainLambda() { doTest(); }
  public void testAndChainLambdaSingleLine() { doTest(); }

  public void testInlineDoubleCall() { doTest(); }

  public void testTernaryBranch() { doTest(); }
  public void testTernaryBranchCollapsible() { doTest(); }

  public void testNewWithSideEffect() { doTest(); }

  public void testSplitIfAndCollapseBack() { doTest(); }

  public void testThisVariableName() { doTest(); }

  public void testRenameLocalClass() { doTest(); }

  public void testRenameLocalClassDoubleConflict() { doTest(); }

  public void testBooleanResultInIfChain() { doTest(); }

  public void testInlineSingleImplementation() {
    TestDialogManager.setTestDialog(TestDialog.YES, getTestRootDisposable());
    doTest();
  }

  public void testInlineSingleImplementationGenericClass() {
    TestDialogManager.setTestDialog(TestDialog.YES, getTestRootDisposable());
    doTest();
  }

  public void testInlineSingleImplementationGenericMethod() {
    TestDialogManager.setTestDialog(TestDialog.YES, getTestRootDisposable());
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest());
  }

  @Override
  protected Sdk getProjectJDK() {
    return getTestName(false).contains("Src") ? IdeaTestUtil.getMockJdk17() : super.getProjectJDK();
  }

  private void doTestInlineThisOnly() {
    @NonNls String fileName = configure();
    performAction(true, false);
    checkResultByFile(fileName + ".after");
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean assertBadReturn) {
    String fileName = configure();
    InlineActionHandler handler = ContainerUtil.find(InlineActionHandler.EP_NAME.getExtensionList(), ep -> ep instanceof InlineMethodHandler);
    assertNotNull(handler);
    PsiMethod method = findMethod();
    final boolean condition = InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method);
    if (assertBadReturn) {
      assertTrue("Bad returns not found", condition);
    } else {
      assertFalse("Bad returns found", condition);
    }
    handler.inlineElement(getProject(), getEditor(), method);
    checkResultByFile(fileName + ".after");
  }

  private void doTestNonCode() {
    @NonNls String fileName = configure();
    performAction(false, true);
    checkResultByFile(fileName + ".after");
  }

  private void doTestAssertBadReturn() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(true));
  }

  @NotNull
  private String configure() {
    @NonNls String fileName = "/refactoring/inlineMethod/" + getTestName(false) + ".java";
    configureByFile(fileName);
    return fileName;
  }

  private void performAction(final boolean inlineThisOnly, final boolean nonCode) {
    final PsiReference ref = getFile().findReferenceAt(getEditor().getCaretModel().getOffset());
    PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;
    PsiMethod method = findMethod();
    final boolean condition = InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method);
    assertFalse("Bad returns found", condition);
    final InlineMethodProcessor processor =
      new InlineMethodProcessor(getProject(), method, refExpr, getEditor(), inlineThisOnly, nonCode, nonCode, true);
    processor.run();
  }

  private PsiMethod findMethod() {
    PsiElement element = TargetElementUtil
      .findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    final PsiReference ref = getFile().findReferenceAt(getEditor().getCaretModel().getOffset());
    if (ref instanceof PsiJavaCodeReferenceElement codeRef && codeRef.getParent() instanceof PsiNewExpression newExpression) {
      element = newExpression.resolveConstructor();
    }
    assertTrue(element instanceof PsiMethod);
    return (PsiMethod)element;
  }
}

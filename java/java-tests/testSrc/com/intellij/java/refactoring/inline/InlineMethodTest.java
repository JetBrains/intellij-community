// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.refactoring.InlineActionHandler;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.inline.InlineMethodHandler;
import com.intellij.refactoring.inline.InlineMethodProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class InlineMethodTest {
  private static final LightProjectDescriptor DESCRIPTOR = lightJavaTestDescriptor();
  private static final LightProjectDescriptor SRC_DESCRIPTOR = lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk17());
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, testName -> testName.contains("Src") ? SRC_DESCRIPTOR : DESCRIPTOR, "/java/java-tests/testData");

  @Test
  public void testInlineParms() {
    doTest();
  }

  @Test
  public void testInlineWithQualifier() {
    doTest();
  }

  @Test
  public void testInlineWithQualifierFromSuper() { doTest(); }
  @Test
  public void testTry() {
    doTest();
  }

  @Test
  public void testTrySynchronized() {
    doTest();
  }

  @Test
  public void testStaticSynchronized() {
    doTest();
  }

  @Test
  public void testSuperInsideHierarchy() {
    doTest();
  }

  @Test
  public void testSideEffect() { doTest(); }

  @Test
  public void testParamAsAutocloseableRef() { doTest(); }

  @Test
  public void testInlineWithTry() { doTest(); }
  @Test
  public void testEmptyMethod() { doTest(); }

  @Test
  public void testVoidWithReturn() { doTest(); }
  @Test
  public void testVoidWithReturn1() { doTest(); }

  @Test
  public void testScr10884() {
    doTest();
  }
  @Test
  public void testFinalParameters() { doTest(); }
  @Test
  public void testFinalParameters1() { doTest(); }

  @Test
  public void testScr13831() { doTest(); }

  @Test
  public void testNameClash() { doTest(); }

  @Test
  public void testArrayAccess() { doTest(); }

  @Test
  public void testConflictingField() { doTest(); }

  @Test
  public void testCallInFor() { doTest(); }

  @Test
  public void testSCR20655() { doTest(); }
  @Test
  public void testGenericArrayCreation() { doTest(); }
  @Test
  public void testNoRedundantCast() { doTest(); }
  @Test
  public void testFieldInitializer() { doTest(); }
  @Test
  public void testStaticInterfaceMethod() { doTest(); }

  @Test
  public void testMethodCallInOtherAnonymousOrInner() { doTest(); }

  @Test
  public void testStaticFieldInitializer() { doTest(); }
  @Test
  public void testSCR22644() { doTest(); }
  @Test
  public void testChangeContextForThisInNestedClasses() { doTest(); }

  @Test
  public void testCallUnderIf() { doTest(); }
  @Test
  public void testInlineEnumArgsChangeContext() { doTest(); }

  //This gives extra 'result' local variable, currently I don't see a way to cope with it, todo: think about addional inline possibilities
  //public void testLocalVariableResult() throws Exception { doTest(); }

  @Test
  public void testSCR31093() { doTest(); }

  @Test
  public void testSCR37742() { doTest(); }

  @Test
  public void testChainingConstructor() { doTest(); }

  @Test
  public void testChainingConstructor1() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest());
  }
  
  @Test
  public void testChainingVarargConstructor() { doTest(); }

  @Test
  public void testNestedCall() { doTest(); }

  @Test
  public void testIDEADEV3672() { doTest(); }

  @Test
  public void testIDEADEV5806() { doTest(); }

  @Test
  public void testIDEADEV6807() { doTest(); }

  @Test
  public void testIDEADEV12616() { doTest(); }

  @Test
  public void testVarargs() { doTest(); }

  @Test
  public void testVarargs1() { doTest(); }

  @Test
  public void testFlatVarargs() {doTest();}
  @Test
  public void testFlatVarargs1() {doTest();}

  @Test
  public void testEnumConstructor() { doTest(); }

  @Test
  public void testEnumConstantConstructorParameter() {  // IDEADEV-26133
    doTest();
  }

  @Test
  public void testEnumConstantConstructorParameterComplex() {  // IDEADEV-26133
    doTest();
  }

  @Test
  public void testEnumConstantConstructorParameterComplex2() {  // IDEADEV-26133
    doTest();
  }

  @Test
  public void testEnumConstantConstructorParameterNestedLambda() {
    doTest();
  }

  @Test
  public void testEnumConstantConstructorWithArgs() {
    doTest();
  }

  @Test
  public void testConstantInChainingConstructor() {   // IDEADEV-28136
    doTest();
  }

  @Test
  public void testReplaceParameterWithArgumentForConstructor() {   // IDEADEV-23652
    doTest();
  }

  @Test
  public void testTailCallReturn() {  // IDEADEV-27983
    doTest();
  }

  @Test
  public void testTailCallSimple() {  // IDEADEV-27983
    doTest();
  }

  @Test
  public void testTailComment() {   //IDEADEV-33638
    doTest();
  }

  @Test
  public void testInferredType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setModuleLanguageLevel(myFixture.getModule(), LanguageLevel.JDK_1_7, myFixture.getTestRootDisposable());
    doTest();
  }

  @Test
  public void testReplaceGenericsInside() {
    doTest();
  }

  @Test
  public void testStaticMethodWithoutParams() {
    doTest();
  }

  @Test
  public void testWithSuperInside() {
    doTest();
  }

  @Test
  public void testRawSubstitution() {
    doTest();
  }

  @Test
  public void testSubstitution() {
    doTest();
  }

  @Test
  public void testSubstitutionForWildcards() {
    doTest();
  }

  @Test
  public void testParamNameConflictsWithLocalVar() {
    doTest();
  }
  
  @Test
  public void testVariablePrefixesSuffixes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    settings.LOCAL_VARIABLE_NAME_PREFIX = "l_";
    settings.LOCAL_VARIABLE_NAME_SUFFIX = "_v";
    settings.PARAMETER_NAME_PREFIX = "p_";
    settings.PARAMETER_NAME_SUFFIX = "_r";
    doTest();
  }

  @Test
  public void testArrayTypeInferenceFromVarargs() {
    doTest();
  }

  @Test
  public void testSuperMethodInAnonymousClass() {
    doTest();
  }

  @Test
  public void testInlineAnonymousClassWithPrivateMethodInside() {
    doTest();
  }

  @Test
  public void testChainedConstructor() {
    doTestInlineThisOnly();
  }

  @Test
  public void testChainedConstructorWithSpacesInvalidation() {
    doTest();
  }

  @Test
  public void testChainedConstructor1() {
    doTest();
  }

  @Test
  public void testMethodUsedInJavadoc() {
    doTestConflict("Inlined method is used in javadoc");
  }

  @Test
  public void testMethodUsedReflectively() {
    doTestConflict("Inlined method is used reflectively");
  }

  @Test
  public void testInlineOnelinerToCondition() {
    doTest();
  }

  @Test
  public void testNotAStatement() {
    doTest();
  }

  @Test
  public void testNotAStatement2() {
    doTest();
  }

  @Test
  public void testNotAStatement3() {
    doTest();
  }

  @Test
  public void testNotAStatement4() {
    doTest();
  }

  @Test
  public void testForContinue() {
    doTest();
  }

  @Test
  public void testSingleReturn1() {
    doTestAssertBadReturn();
  }

  @Test
  public void testSingleReturn1NotFinal() {
    doTestAssertBadReturn();
  }

  @Test
  public void testSingleReturn2() {
    doTestAssertBadReturn();
  }

  @Test
  public void testInSuperCall() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, () -> doTestConflict("Inline cannot be applied to multiline method in constructor call"));
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_22_PREVIEW, this::doTest);
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_25, this::doTest);
  }

  @Test
  public void testMethodReferenceInsideMethodCall() {
    doTest();
  }

  @Test
  public void testVolatilePassed() {
    doTest();
  }

  @Test
  public void testBooleanConstantArgument() {
    doTest();
  }

  @Test
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

  @Test
  public void testInlineRunnableRun() {
    doTestInlineThisOnly();
  }

  @Test
  public void testPreserveLeadingTailingComments() {
    doTestInlineThisOnly();
  }

  @Test
  public void testSkipEmptyMethod() {
    doTestInlineThisOnly();
  }

  @Test
  public void testOneLineLambdaVoidCompatibleToBlock() {
    doTestInlineThisOnly();
  }

  @Test
  public void testOneLineLambdaValueCompatibleToBlock() {
    doTestInlineThisOnly();
  }

  @Test
  public void testOneLineLambdaVoidCompatibleOneLine() {
    doTestInlineThisOnly();
  }

  @Test
  public void testOneLineLambdaValueCompatibleOneLine() {
    doTestInlineThisOnly();
  }

  @Test
  public void testOnMethodReference() {
    doTestInlineThisOnly();
  }

  @Test
  public void testOnLambda() {
    doTestInlineThisOnly();
  }

  @Test
  public void testNonCodeUsage() {
    doTestNonCode();
  }

  @Test
  public void testMethodInsideChangeIfStatement() {
    doTest();
  }

  @Test
  public void testSameVarMethodNames() {
    doTest();
  }

  @Test
  public void testThisNameConflict() {
    doTest();
  }

  @Test
  public void testStringPlusOverload() {
    doTest();
  }

  @Test
  public void testConcatenationInConcatenation() {
    doTest();
  }

  @Test
  public void testConcatenationTurnsIntoAddition() {
    doTest();
  }

  @Test
  public void testAdditionTurnsIntoConcatenation() {
    doTest();
  }

  @Test
  public void testReturnStatementWithoutBraces() {
    doTestInlineThisOnly();
  }

  @Test
  public void testIfElseIfWithSingleStatement() {
    doTestInlineThisOnly();
  }

  @Test
  public void testUnresolvedArgPassedToSameNameParameter() {
    doTestInlineThisOnly();
  }

  @Test
  public void testMakeTypesDenotable() {
    doTestInlineThisOnly();
  }

  @Test
  public void testInlineIntoMethodRef() {
    doTestInlineThisOnly();
  }

  @Test
  public void testInlineIntoConstructorRef() {
    doTestInlineThisOnly();
  }

  @Test
  public void testSideEffectsInMethodRefQualifier() {
    doTestConflict("Inlined method is used in method reference with side effects in qualifier");
  }

  @Test
  public void testUnableToInlineCodeBlockToSuper() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21,
                           () -> doTestConflict("Inline cannot be applied to multiline method in constructor call"));
  }

  @Test
  public void testRedundantCastOnMethodReferenceToLambda() {
    doTest();
  }

  @Test
  public void testInaccessibleSuperCallWhenQualifiedInline() {
    doTestConflict("Inlined method calls super.bar() which won't be accessed in class <b><code>B</code></b>");
  }

  @Test
  public void testInaccessibleSuperCallWhenQualifiedInInheritor() {
    doTestConflict("Inlined method calls super.foo() which won't be accessible on qualifier c");
  }

  @Test
  public void testInaccessibleConstructorInInlinedMethod() {
    doTestConflict("Constructor <b><code>SomeClass.SomeClass()</code></b> will not be accessible when method " +
                   "<b><code>SomeClass.createInstance()</code></b> is inlined into method " +
                   "<b><code>InlineWithPrivateConstructorAccessMain.main(String...)</code></b>");
  }

  @Test
  public void testPreserveResultedVariableIfInitializerIsNotSideEffectsFree() {
    doTestInlineThisOnly();
  }

  @Test
  public void testExprLambdaExpandToCodeBlock() {
    doTestInlineThisOnly();
  }

  @Test
  public void testSuperCallWhenUnqualifiedInline() {
    doTestInlineThisOnly();
  }

  @Test
  public void testRemoveReturnForTailTypeSimpleWhenNoSideEffectsPossible() {
    doTestInlineThisOnly();
  }

  @Test
  public void testDeleteOverrideAnnotations() {
    doTest();
  }

  @Test
  public void testNegativeArguments() {
    doTest();
  }

  @Test
  public void testInaccessibleFieldInSuperClass() {
    doTestConflict("Field <b><code>A.i</code></b> will not be accessible when method <b><code>A.foo()</code></b> is inlined into " +
                   "method <b><code>B.bar()</code></b>");
  }

  @Test
  public void testPrivateFieldInSuperClassInSameFile() {
    doTest();
  }

  @Test
  public void testWidenArgument() {
    doTest();
  }

  @Test
  public void testInlineMultipleOccurrencesInFieldInitializer() {
    doTest();
  }

  @Test
  public void testAvoidMultipleSubstitutionInParameterTypes() {
    doTest();
  }

  @Test
  public void testRespectProjectScopeSrc() {
    doTest();
  }

  @Test
  public void testRespectProjectScopeSrcConstructorCall() {
    doTest();
  }

  @Test
  public void testChainedConstructorWithMultipleStatements() {
    doTestInlineThisOnly();
  }

  @Test
  public void testThisExpressionValidationForLocalClasses() {
    doTestInlineThisOnly();
  }

  @Test
  public void testTailCallInsideIf() {
    doTest();
  }

  @Test
  public void testTailCallInsideLambda() {
    doTest();
  }

  @Test
  public void testChainedBuilderCall() {
    doTest();
  }

  @Test
  public void testMissedQualifierWithSideEffectsOnInliningEmptyMethod() {
    doTest();
  }

  @Test
  public void testNotTailCallInsideIf() {
    doTestAssertBadReturn();
  }

  @Test
  public void testConvertToSingleReturnWithFinished() {
    doTestAssertBadReturn();
  }

  @Test
  public void testConvertToSingleReturnWithFinishedUnusedResult() {
    doTestAssertBadReturn();
  }

  @Test
  public void testUnusedResult() {
    doTest();
  }

  @Test
  public void testReuseResultVar() {
    doTest();
  }

  @Test
  public void testSpecializeClassGetName() {
    doTest();
  }

  @Test
  public void testSpecializeEnumName() {
    doTest();
  }

  @Test
  public void testSpecializeEnumValueOf() {
    doTest();
  }

  @Test
  public void testBooleanModelSimple() {
    doTestAssertBadReturn();
  }

  @Test
  public void testBooleanModelMultiReturns() {
    doTestAssertBadReturn();
  }

  @Test
  public void testBooleanModelIfElse() {
    doTestAssertBadReturn();
  }

  @Test
  public void testBooleanModelIfElse2() {
    doTestAssertBadReturn();
  }

  @Test
  public void testBooleanModelContinue() {
    doTestAssertBadReturn();
  }

  @Test
  public void testBooleanModelFinalCondition() {
    doTestAssertBadReturn();
  }

  @Test
  public void testInvertMethod() {
    doTest();
  }

  @Test
  public void testUnusedParameter() {
    doTest();
  }

  @Test
  public void testEnumStaticMethod() {
    doTest();
  }

  @Test
  public void testTypeParameterMethodRefArgument() {
    doTest();
  }

  @Test
  public void testIgnoreReturnValue() {
    doTest();
  }

  @Test
  public void testSingleReturnComplexQualifier() {
    doTestAssertBadReturn();
  }

  @Test
  public void testAnonymousCall() { doTest(); }
  @Test
  public void testInSwitchExpression() { doTest(); }
  @Test
  public void testInSwitchExpressionYield() { doTest(); }

  @Test
  public void testAndChain() { doTest(); }
  @Test
  public void testAndChainLambda() { doTest(); }
  @Test
  public void testAndChainLambdaSingleLine() { doTest(); }

  @Test
  public void testInlineDoubleCall() { doTest(); }
  @Test
  public void testInlineNestedCall() { doTest(); }

  @Test
  public void testTernaryBranch() { doTest(); }
  @Test
  public void testTernaryBranchCollapsible() { doTest(); }

  @Test
  public void testNewWithSideEffect() { doTest(); }

  @Test
  public void testSplitIfAndCollapseBack() { doTest(); }

  @Test
  public void testThisVariableName() { doTest(); }

  @Test
  public void testRenameLocalClass() { doTest(); }

  @Test
  public void testRenameLocalClassDoubleConflict() { doTest(); }

  @Test
  public void testBooleanResultInIfChain() { doTest(); }
  
  @Test
  public void testLambdaIfAnd() { doTest(); }
  
  @Test
  public void testLambdaIfAndGenericNot() { doTest(); }

  @Test
  public void testInlineSingleImplementation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestDialogManager.setTestDialog(TestDialog.YES, myFixture.getTestRootDisposable());
    doTest();
  }

  @Test
  public void testInlineSingleImplementationGenericClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestDialogManager.setTestDialog(TestDialog.YES, myFixture.getTestRootDisposable());
    doTest();
  }

  @Test
  public void testInlineSingleImplementationGenericMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestDialogManager.setTestDialog(TestDialog.YES, myFixture.getTestRootDisposable());
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest());
  }
  
  @Test
  public void testInlineSingleImplementationCastOnThis() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestDialogManager.setTestDialog(TestDialog.YES, myFixture.getTestRootDisposable());
    doTest();
  }
  
  @Test
  public void testInlineSingleImplementationCastOnThisUnnecessary() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestDialogManager.setTestDialog(TestDialog.YES, myFixture.getTestRootDisposable());
    doTest();
  }
  
  @Test
  public void testAutomaticGetterUse() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestDialogManager.setTestDialog(TestDialog.YES, myFixture.getTestRootDisposable());
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest());
  }
  
  @Test
  public void testNoAutomaticGetterUseGetterDoesDifferentThing() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestDialogManager.setTestDialog(TestDialog.YES, myFixture.getTestRootDisposable());
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest());
  }
  
  @Test
  public void testNoAutomaticGetterUseAccessibleField() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestDialogManager.setTestDialog(TestDialog.YES, myFixture.getTestRootDisposable());
    doTest();
  }
  
  @Test
  public void testInlineNullInForEach() {
    doTest();
  }
  
  @Test
  public void testAutomaticGetterSetterUse() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestDialogManager.setTestDialog(TestDialog.YES, myFixture.getTestRootDisposable());
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest());
  }

  @Test
  public void testAutomaticGetterUsePreferRecordAccessor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestDialogManager.setTestDialog(TestDialog.YES, myFixture.getTestRootDisposable());
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest());
  }

  @Test
  public void testAutomaticGetterUseOverridableConflict() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestDialogManager.setTestDialog(TestDialog.YES, myFixture.getTestRootDisposable());
    doTestConflict("Field <b><code>F.x</code></b> is not accessible from method <b><code>G.main()</code></b>. " +
                   "The refactoring will use accessor <b><code>F.getX()</code></b> instead. " +
                   "Note that subclasses may override the accessor and change the behaviour.");
  }

  @Test
  public void testAutomaticGetterUseExactNoConflict() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TestDialogManager.setTestDialog(TestDialog.YES, myFixture.getTestRootDisposable());
    doTest();
  }

  @Test
  public void testThrowCallSite() { doTest(); }

  @Test
  public void testThrowCallSiteReturn() { doTest(); }

  private void doTestInlineThisOnly() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @NonNls String fileName = configure();
    myFixture.runInCommand(() -> {
      performAction(true, false);
      myFixture.checkResultByFile(fileName + ".after");
    });
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean assertBadReturn) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String fileName = configure();
    myFixture.runInCommand(() -> {
      InlineActionHandler handler = ContainerUtil.find(InlineActionHandler.EP_NAME.getExtensionList(), ep -> ep instanceof InlineMethodHandler);
      assertNotNull(handler);
      PsiMethod method = findMethod();
      final boolean condition = InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method);
      if (assertBadReturn) {
        assertTrue(condition, "Bad returns not found");
      } else {
        assertFalse(condition, "Bad returns found");
      }
      handler.inlineElement(myFixture.getProject(), myFixture.getEditor(), method);
      myFixture.checkResultByFile(fileName + ".after");
    });
  }

  private void doTestNonCode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @NonNls String fileName = configure();
    myFixture.runInCommand(() -> {
      performAction(false, true);
      myFixture.checkResultByFile(fileName + ".after");
    });
  }

  private void doTestAssertBadReturn() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> doTest(true));
  }

  @NotNull
  private String configure() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @NonNls String fileName = "/refactoring/inlineMethod/" + myFixture.getTestName(false) + ".java";
    myFixture.configureByFile(fileName);
    return fileName;
  }

  private void performAction(final boolean inlineThisOnly, final boolean nonCode) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression expression ? expression : null;
    PsiMethod method = findMethod();
    final boolean condition = InlineMethodProcessor.checkBadReturns(method) && !InlineUtil.allUsagesAreTailCalls(method);
    assertFalse(condition, "Bad returns found");
    final InlineMethodProcessor processor =
      new InlineMethodProcessor(myFixture.getProject(), method, refExpr, myFixture.getEditor(), inlineThisOnly, nonCode, nonCode, true);
    processor.run();
  }

  private PsiMethod findMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiElement element = TargetElementUtil
      .findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    final PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    if (ref instanceof PsiJavaCodeReferenceElement codeRef && codeRef.getParent() instanceof PsiNewExpression newExpression) {
      element = newExpression.resolveConstructor();
    }
    assertInstanceOf(PsiMethod.class, element);
    return (PsiMethod)element;
  }
}

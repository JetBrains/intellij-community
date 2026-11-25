// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.extractMethod.newImpl.ExtractException;
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class ExtractMethodNewTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  private static final @NonNls String BASE_PATH = "/refactoring/extractMethodNew/";
  private boolean myCatchOnNewLine = true;

  @Test
  public void testExitPoints1() {
    doExitPointsTest(true);
  }

  @Test
  public void testExitPoints2() {
    doTest();
  }

  @Test
  public void testExitPoints3() {
    doExitPointsTest(true);
  }

  @Test
  public void testExitPoints4() {
    doTest();
  }

  @Test
  public void testExitPoints4Nullable() {
    doExitPointsTest(false);
  }

  @Test
  public void testExitPointsInsideLoop() {
    doExitPointsTest(true);
  }

  @Test
  public void testExitPoints5() {
    doTest();
  }

  @Test
  public void testExitPoints6() {
    doTest();
  }

  @Test
  public void testExitPoints7() {
    doExitPointsTest(false);
  }

  @Test
  public void testExitPoints8() {
    doTest();
  }

  @Test
  public void testExitPoints9() {
    doTest();
  }

  @Test
  public void testExitPoints10() {
    doExitPointsTest(false);
  }

  @Test
  public void testExitPoints11() {
    doTest();
  }

  @Test
  public void testNotNullCheckNameConflicts() {
    doTest();
  }

  @Test
  public void testContinueInside() {
    doTest();
  }

  @Test
  public void testBooleanExpression() {
    doTest();
  }

  @Test
  public void testScr6241() {
    doTest();
  }

  @Test
  public void testScr7091() {
    doTest();
  }

  @Test
  public void testScr10464() {
    doTest();
  }

  @Test
  public void testScr9852() {
    doTest();
  }

  @Test
  public void testUseVarAfterTry() {
    doTest();
  }

  @Test
  public void testUseParamInCatch() {
    doExitPointsTest(false);
  }

  @Test
  public void testUseParamInFinally() {
    doExitPointsTest(false);
  }

  @Test
  public void testUseVarAfterCatch() {
    doExitPointsTest(false);
  }

  @Test
  public void testUseVarInCatch1() {
    doTest();
  }

  @Test
  public void testUseVarInCatch2() {
    doExitPointsTest(false);
  }

  @Test
  public void testUseVarInCatchInvisible() {
    doTest();
  }

  @Test
  public void testUseVarInCatchNested1() {
    doTest();
  }

  @Test
  public void testUseVarInCatchNested2() {
    doExitPointsTest(false);
  }

  @Test
  public void testUseVarInOtherCatch() {
    doTest();
  }

  @Test
  public void testUseVarInFinally1() {
    doTest();
  }

  @Test
  public void testUseVarInFinally2() {
    doExitPointsTest(false);
  }

  @Test
  public void testOneBranchAssignment() {
    doTest();
  }

  @Test
  public void testExtractFromCodeBlock() {
    doTest();
  }

  @Test
  public void testUnusedInitializedVar() {
    doTest();
  }

  @Test
  public void testTryFinally() {
    doTest();
  }

  @Test
  public void testFinally() {
    doTest();
  }

  @Test
  public void testExtractFromAnonymous() {
    doTest();
  }

  @Test
  public void testSCR12245() {
    try {
      doTest();
      fail("Should not work for assignment expression");
    } catch (PrepareFailedException ignored) {
    }
  }

  @Test
  public void testLeaveCommentsWhenExpressionExtracted() {
    doTest();
  }

  @Test
  public void testSCR15815() {
    doTest();
  }

  @Test
  public void testFieldGroupAnchor() {
    doTest();
  }

  @Test
  public void testFieldGroupAnchor2() {
    doTest();
  }

  @Test
  public void testSCR27887() {
    doTest();
  }

  @Test
  public void testSCR28427() {
    doTest();
  }

  @Test
  public void testTryFinallyInsideFor() {
    doTest();
  }

  @Test
  public void testExtractFromTryFinally() {
    doTest();
  }

  @Test
  public void testInferredReturnType1() {
    doTest();
  }

  @Test
  public void testInferredReturnType2() {
    doTest();
  }

  @Test
  public void testInferredReturnType3() {
    doTest();
  }

  @Test
  public void testInferredReturnType4() {
    doTest();
  }

  @Test
  public void testInferredReturnType5() {
    doTest();
  }

  @Test
  public void testInferredReturnType6() {
    doTest();
  }

  @Test
  public void testNotPassedStaticField() {
    doTestPassFieldsAsParams();
  }

  @Test
  public void testNotPassedStaticField2() {
    doTestPassFieldsAsParams();
  }

  @Test
  public void testExtractAssignmentExpression() {
    try {
      doTest();
      fail("Should not work for assignment expression");
    } catch (PrepareFailedException ignored) {
    }
  }

  @Test
  public void testExtractAssignmentExpressionFromStatement() {
    try {
      doTest();
      fail("Should not work for assignment expression");
    } catch (PrepareFailedException ignored) {
    }
  }

  @Test
  public void testAssignmentInsideLambda() {
    doTest();
  }

  @Test
  public void testAssignmentToField() {
    doTest();
  }

  @Test
  public void testExtractFromTryFinally2() {
    doTest();
  }

  @Test
  public void testLesyaBug() {
    myCatchOnNewLine = false;
    doTest();
  }

  @Test
  public void testForEach() {
    doTest();
  }

  @Test
  public void testAnonInner() {
    doTest();
  }


  @Test
  public void testConflictingAnonymous() {
    doTest();
  }

  @Test
  public void testVarDeclAfterExpressionExtraction() {
    doTest();
  }

  @Test
  public void testFinalParamUsedInsideAnon() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).GENERATE_FINAL_PARAMETERS = false;
    doTestWithJava17();
  }

  private void doTestWithJava17() {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_7);
  }

  private void doTestWithLanguageLevel(LanguageLevel languageLevel) {
    IdeaTestUtil.withLevel(codeInsightFixture.get().getModule(), languageLevel, () -> doTest());
  }

  @Test
  public void testNonFinalWritableParam() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings.getInstance(myFixture.getProject()).GENERATE_FINAL_PARAMETERS = true;
    doTest();
  }

  @Test
  public void testCodeDuplicatesWithContinue() {
    doDuplicatesTest();
  }

  @Test
  public void testDuplicatesFromAnonymous() {
    doDuplicatesTest();
  }

  @Test
  public void testDuplicatesFullyQualifiedType() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicatesWithContinueNoReturn() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicatesWithStaticInitializer() {
    doDuplicatesTest();
  }

  @Test
  public void testDuplicateInUnreachableCode() {
    doDuplicatesTest();
  }

  @Test
  public void testExpressionDuplicates() {
    doDuplicatesTest();
  }

  @Test
  public void testClassReference() {
    doDuplicatesTest();
  }

  @Test
  public void testClassReference2() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicates() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicates2() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicates3() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicates4() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicates5() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicatesWithOutputValue() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicatesWithOutputValue1() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicatesWithEmptyStatementsBlocksParentheses() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicatesWithMultExitPoints() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicatesWithReturn() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicatesWithReturn2() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicatesWithReturnInAnonymous() {
    doDuplicatesTest();
  }

  @Test
  public void testCodeDuplicatesWithComments() {
    doDuplicatesTest();
  }

  @Test
  public void testSCR32924() {
    doDuplicatesTest();
  }

  @Test
  public void testFinalOutputVar() {
    doDuplicatesTest();
  }

  @Test
  public void testGenerateFinalVar() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    settings.GENERATE_FINAL_LOCALS = true;
    doTest();
  }

  @Test
  public void testIdeaDev2291() {
    doTest();
  }

  @Test
  public void testOxfordBug() {
    doTest();
  }

  @Test
  public void testIDEADEV33368() {
    doExitPointsTest(false);
  }

  @Test
  public void testInlineCreated2ReturnLocalVariablesOnly() {
    doTest();
  }

  @Test
  public void testGuardMethodDuplicates() {
    doDuplicatesTest();
  }

  @Test
  public void testGuardMethodDuplicates1() {
    doDuplicatesTest();
  }

  @Test
  public void testInstanceMethodDuplicatesInStaticContext() {
    doDuplicatesTest();
  }


  @Test
  public void testLValueNotDuplicate() {
    doDuplicatesTest();
  }

  protected void doDuplicatesTest() {
    doTest(true);
  }

  @Test
  public void testExtractFromFinally() {
    doTest();
  }

  @Test
  public void testNoShortCircuit() {
    doTest();
  }

  @Test
  public void testStopFolding() {
    doTest();
  }

  @Test
  public void testStopFoldingForArrayWriteAccessInConsequentUsages() {
    doTest();
  }

  @Test
  public void testStopFoldingPostfixInside() {
    doTest();
  }

  @Test
  public void testFoldedWithNestedExpressions() {
    doTest();
  }

  @Test
  public void testFoldedWithConflictedNames() {
    doTest();
  }

  @Test
  public void testFoldingWithFieldInvolved() {
    doTest();
  }

  @Test
  public void testFoldingWithFunctionCall() {
    doTest();
  }

  @Test
  public void testDontSkipVariablesUsedInLeftSideOfAssignments() {
    doTest();
  }

  @Test
  public void testIDEADEV11748() {
    doTest();
  }

  @Test
  public void testIDEADEV11848() {
    doTest();
  }

  //TODO support as method conflict
  public void _testIDEADEV11036() {
    doTest();
  }

  @Test
  public void testLocalClass() {
    doPrepareErrorTest("Local class is defined out of the selected block.");
  }

  @Test
  public void testLocalClassUsage() {
    doPrepareErrorTest("Local class is used out of the selected block.");
  }

  @Test
  public void testLocalClassScope() {
    doTest();
  }

  @Test
  public void testStaticImport() {
    doTest();
  }

  @Test
  public void testThisCall() {
    doTest();
  }

  @Test
  public void testCompactRecordConstructor() {
    doTest();
  }

  @Test
  public void testChainedConstructor() {
    doChainedConstructorTest(false);
  }

  @Test
  public void testChainedConstructorDuplicates() {
    doChainedConstructorTest(true);
  }

  @Test
  public void testChainedConstructorInvalidDuplicates() {
    doChainedConstructorTest(true);
  }

  @Test
  public void testReturnFromTry() {
    doTest();
  }

  @Test
  public void testLocalClassDefinedInMethodWhichIsUsedLater() {
    doPrepareErrorTest("Local class is used out of the selected block.");
  }

  @Test
  public void testForceBraces() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    int old = settings.IF_BRACE_FORCE;
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    try {
      doTest();
    }
    finally {
      settings.IF_BRACE_FORCE = old;
    }
  }

  @Test
  public void testConstantConditionsAffectingControlFlow() {
    doTest();
  }

  @Test
  public void testConstantConditionsAffectingControlFlow1() {
    doTest();
  }
  @Test
  public void testNotInitializedInsideFinally() {
    doTest();
  }

  @Test
  public void testGenericsParameters() {
    doTest();
  }

  @Test
  public void testUnusedGenerics() {
    doTest();
  }

  @Test
  public void testParamsUsedInLocalClass() {
    doTestWithJava17();
  }

  private void doChainedConstructorTest(boolean replaceAllDuplicates) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      boolean success = performExtractMethod(true, replaceAllDuplicates, myFixture.getEditor(), myFixture.getFile(), myFixture.getProject(), true);
      assertTrue(success);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testReassignedVarAfterCall() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    settings.GENERATE_FINAL_LOCALS = true;
    doTest();
  }

  @Test
  public void testNonPhysicalAssumptions() {
    doTest();
  }

  @Test
  public void testNullableCheck() {
    doTest();
  }

  @Test
  public void testNullableCheck1() {
    doTest();
  }

  @Test
  public void testNullableCheckVoid() {
    doTest();
  }

  @Test
  public void testNullableCheckDontMissFinal() {
    doTest();
  }

  @Test
  public void testNullableCheckBreak() {
    doTest();
  }

  @Test
  public void testArrayInitializer() {
    doTest();
  }

  @Test
  public void testSimpleArrayAccess() {
    doTest();
  }

  @Test
  public void testArrayAccess() {
    doTest();
  }

  @Test
  public void testArrayAccess1() {
    doTest();
  }

  @Test
  public void testArrayAccessWithLocalIndex() {
    doTest();
  }

  @Test
  public void testArrayAccessWithTopExpression() {
    doTest();
  }

  @Test
  public void testArrayAccessWithDuplicates() {
    doDuplicatesTest();
  }

  @Test
  public void testVerboseArrayAccess() {
    doTest();
  }

  //TODO keep bad formatting if possible
  public void _testParameterFormatting() {
    doTest();
  }

  public void _testNoExpressionFormatting1() {
    doTest();
  }

  public void _testNoExpressionFormatting2() {
    doTest();
  }

  @Test
  public void testIgnoreSwitchBreak() {
    doTest();
  }

  @Test
  public void testExtractSwitchVariable() {
    doTest();
  }

  @Test
  public void testReturnVariablesResolved() {
    doTest();
  }

  @Test
  public void testExtractBranchedPrimitive() {
    doTest();
  }

  @Test
  public void testExtractSwitchNotNullVariable() {
    doTest();
  }

  @Test
  public void testAvoidContinueInsideMethod() {
    doTest();
  }

  @Test
  public void testLocalVariablesAreNotExposed() {
    doTest();
  }

  @Test
  public void testExtractConditionalContinue() {
    doTest();
  }

  @Test
  public void testExtractConditionalBreak() {
    doTest();
  }

  @Test
  public void testDontMissReturn() {
    doTest();
  }

  @Test
  public void testNoRedundantContinue() {
    doTest();
  }

  @Test
  public void testDontReplaceThrowWithReturn() {
    doTest();
  }

  @Test
  public void testDontMissReturnDueToThrowable() {
    doTest();
  }

  @Test
  public void testThrowInNonVoidMethodNeedsReturn() {
    doTest();
  }

  @Test
  public void testThrowCaughtInsideNeedsReturn() {
    doTest();
  }

  @Test
  public void testTwoUncheckedThrowsNeedReturn() {
    doTest();
  }

  @Test
  public void testCheckedThrowHandledNeedsReturn() {
    doTest();
  }

  @Test
  public void testCheckedThrowsUnhandledNeedReturn() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      try {
        performAction(true, false);
        fail("Conflict expected");
      }
      catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
        assertEquals(JavaRefactoringBundle.message("extract.method.conflict.missing.return"), e.getMessage());
      }
    });
  }

  @Test
  public void testCheckedThrowsUnhandledNeedReturnConfirmed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + "CheckedThrowsUnhandledNeedReturn.java");
    myFixture.runInCommand(() -> {
      BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> assertTrue(performAction(true, false)));
      assertTrue(myFixture.getFile().getText().contains("newMethod(b);"));
    });
  }

  @Test
  public void testDontExtractInsideSwitch() {
    try {
      doTest();
      fail("Should fail inside switch");
    } catch (PrepareFailedException ignore) {
    }
  }

  @Test
  public void testDontExtractFieldWithConstructor() {
    doChainedConstructorTest(false);
  }

  @Test
  public void testExtractSingleLabelFromSwitch() {
    doTest();
  }

  @Test
  public void testExtractVariableAndReturn() {
    doTest();
  }

  @Test
  public void testExtractVariableAndReturn1() {
    try {
      doTest();
      fail("Should not extract different returns");
    } catch (PrepareFailedException ignore) {
    }
  }

  @Test
  public void testExtractVariableAndReturn2() {
    try {
      doTest();
      fail("Should not extract internal references");
    } catch (PrepareFailedException ignore) {
    }
  }

  @Test
  public void testExtractVariableAndReturn3() {
    try {
      doTest();
      fail("Should not extract semantically different references");
    } catch (PrepareFailedException ignore) {
    }
  }

  @Test
  public void testDontExtractUnfoldableVariable() {
    try {
      doTest();
      fail("Should not extract nullable variable if primitive type declared outside");
    } catch (PrepareFailedException ignore) {
    }
  }

  @Test
  public void testExtractConstantExpressions() {
      doTest();
  }

  @Test
  public void testDontExtractLocalConstant() {
    try {
      doTest();
      fail("Should fail if expression is linked to the scope");
    } catch (PrepareFailedException ignore) {
    }
  }

  @Test
  public void testDontExtractCustomFinalObjects() {
    try {
      doTest();
      fail("Should fail if expression contains mutable object");
    } catch (PrepareFailedException ignore) {
    }
  }

  @Test
  public void testWriteArrayAccess() {
    doTest();
  }

  @Test
  public void testShortCircuit() {
    doTest();
  }

  @Test
  public void testRecursiveCallToExtracted() {
    doTest();
  }

  @Test
  public void testCodeDuplicatesVarargsShouldNotChangeReturnType() {
    doDuplicatesTest();
  }

  @Test
  public void testVarargsArguments() {
    doTest();
  }

  @Test
  public void testParametersFromAnonymous() {
    doTest();
  }

  @Test
  public void testCast4ParamGeneration() {
    doTest();
  }

  @Test
  public void testNearComment() {
    doTest();
  }

  @Test
  public void testFoldInWhile() {
    doTest();
  }

  @Test
  public void testFoldedParamNameSuggestion() {
    doTest();
  }

  @Test
  public void testNonFoldInIfBody() {
    doTest();
  }

  @Test
  public void testComplexTypeParams() {
    doTest();
  }

  @Test
  public void testExtractWithLeadingComment() {
    doTest();
  }

  @Test
  public void testInvalidReference() {
    doTest();
  }

  @Test
  public void testRedundantCast() {
    doTest();
  }

  @Test
  public void testDisabledParam() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      boolean success = performExtractMethod(true, true, myFixture.getEditor(), myFixture.getFile(), myFixture.getProject(), false, 0);
      assertTrue(success);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testTypeParamsList() {
    doTest();
  }

  @Test
  public void testTypeParamsListWithRecursiveDependencies() {
    doTest();
  }

  @Test
  public void testFromLambdaBody() {
    doTest();
  }

  @Test
  public void testFromLambdaBody1() {
    doTest();
  }

  @Test
  public void testFromLambdaBodyCapturedWildcardParams() {
    doTest();
  }

  @Test
  public void testFromLambdaBodyToAnonymous() {
    doTest();
  }

  @Test
  public void testFromLambdaBodyToToplevelInsideCodeBlock() {
    doTest();
  }

  @Test
  public void testFromLambdaBodyWithReturn() {
    doTest();
  }

  @Test
  public void testOneLineLambda() {
    doTest();
  }

  @Test
  public void testMethod2Interface() {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_8);
  }

  @Test
  public void testMethod2InterfaceFromStatic() {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_8);
  }

  @Test
  public void testMethod2InterfaceFromConstant() {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_8);
  }

  @Test
  public void testParamDetection() {
    doTest();
  }

  @Test
  public void testSkipComments() {
    doTest();
  }

  @Test
  public void testFinalParams4LocalClasses() {
    doTestWithJava17();
  }

  @Test
  public void testIncompleteExpression() {
    doTest();
  }

  @Test
  public void testTwoFromThreeEqStatements() {
    doDuplicatesTest();
  }

  @Test
  public void testCastWhenDuplicateReplacement() {
    doDuplicatesTest();
  }

  @Test
  public void testCheckQualifierMapping() {
    doDuplicatesTest();
  }

  @Test
  public void testArrayReturnType() {
    doDuplicatesTest();
  }

  @Test
  public void testOverloadedMethods() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureOneParam() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureOneParamMultipleTimesInside() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureLeaveSameExpressionsUntouched() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureSameParamNames() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureCallToSameClassMethod() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureInitialParameterUnused() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureWithFolding() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureWithArrayFolding() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureWithGetterFolding() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureWithMultiFolding() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureWithTwoWayFolding() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureSameSubexpression() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureSameSubexpressionWholeLine() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureTrivialMethod() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignaturePlusOneFolding() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureVoidCallFolding() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureThreeOccurrencesTwoLiteralFolding() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureFourOccurrencesTwoLiteralFolding() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureThreeOccurrencesTwoVariableFolding() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureFourOccurrencesTwoVariableFolding() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureWithOutputVariables() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureEqualConstExprFolding() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureLongConstExprFolding() {
    doDuplicatesTest();
  }

  @Test
  public void testSuggestChangeSignatureTwoFieldInitializers() {
    doDuplicatesTest();
  }

  @Test
  public void testConditionalReturnInDuplicate() {
    doDuplicatesTest();
  }

  // todo DuplicatesFinder.canBeEquivalent() should see the difference between 'return' and assignment
  public void _testConditionalReturnVsAssignDuplicate() {
    doDuplicatesTest();
  }

  @Test
  public void testConditionalWithTwoParameters() {
    doDuplicatesTest();
  }

  @Test
  public void testOverlappingDuplicate() {
    doDuplicatesTest();
  }

  @Test
  public void testEffectivelyLocalVariables() {
    doDuplicatesTest();
  }

  @Test
  public void testEffectivelyLocalWithinExpression() {
    doDuplicatesTest();
  }

  @Test
  public void testParametrizedDuplicateNestedSubexpression() {
    doDuplicatesTest();
  }

  @Test
  public void testParametrizedDuplicateDeclaredOutputVariable() {
    doDuplicatesTest();
  }

  @Test
  public void testParametrizedDuplicateDeclaredReusedVariable() {
    doDuplicatesTest();
  }

  @Test
  public void testExactDuplicateDeclaredReusedVariable() {
    doDuplicatesTest();
  }

  @Test
  public void testExactDuplicateTwoDeclaredReusedVariables() {
    doDuplicatesTest();
  }

  @Test
  public void testParametrizedDuplicateUnfoldArrayArgument() {
    doDuplicatesTest();
  }

  @Test
  public void testParametrizedDuplicateFoldListElement() {
    doDuplicatesTest();
  }

  @Test
  public void testParametrizedDuplicateFoldArrayElement() {
    doDuplicatesTest();
  }

  @Test
  public void testParametrizedMultiDuplicatesFoldArrayElement() {
    doDuplicatesTest();
  }

  @Test
  public void testParametrizedDuplicateFoldArrayElementTwoUsages() {
    doDuplicatesTest();
  }

  @Test
  public void testTripleParametrizedDuplicate() {
    doDuplicatesTest();
  }

  @Test
  public void testParametrizedDuplicateKeepSignature() {
    doExactDuplicatesTest();
  }

  @Test
  public void testRejectParametrizedDuplicate() {
    doExactDuplicatesTest();
  }

  @Test
  public void testParametrizedDuplicateRepeatedArguments() {
    doDuplicatesTest();
  }

  @Test
  public void testParametrizedDuplicateTripleRepeatedArguments() {
    doDuplicatesTest();
  }

  @Test
  public void testParametrizedDuplicateExactlyRepeatedArguments() {
    doDuplicatesTest();
  }

  @Test
  public void testParametrizedDuplicateExpression() {
    doDuplicatesTest();
  }

  @Test
  public void testPatternVariable() {
    doTestWithLanguageLevel(LanguageLevel.HIGHEST);
  }

  @Test
  public void testPatternVariableIntroduced() {
    doExitPointsTest(false);
  }

  @Test
  public void testPatternVariableIntroduced2() {
    doExitPointsTest(false);
  }

  @Test
  public void testPatternVariableIntroduced3() {
    doTestWithLanguageLevel(LanguageLevel.HIGHEST);
  }

  @Test
  public void testSuggestChangeSignatureWithChangedParameterName() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      boolean success = performExtractMethod(true, true, myFixture.getEditor(), myFixture.getFile(), myFixture.getProject(), false, null, false, "p");
      assertTrue(success);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testTargetAnonymous() {
    doTest();
  }

  @Test
  public void testSimpleMethodsInOneLine() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    doTest();
  }

  @Test
  public void testExtractUnresolvedLambdaParameter() {
    try {
      doTest();
      fail("Should not work for single lambda parameter");
    }
    catch (PrepareFailedException ignore) {}
  }

  @Test
  public void testExtractUnresolvedLambdaExpression() {
    doTest();
  }

  @Test
  public void testNoNPE1() {
    doTest();
  }

  @Test
  public void testNoNPE2() {
    doTest();
  }

  @Test
  public void testTheOnlyParenthesisExpressionWhichIsSkippedInControlFlow() {
    doTest();
  }

  @Test
  public void testExpression() {
    doTestWithJava17();
  }

  @Test
  public void testNonPhysicalSubexpression() {
    doTest();
  }

  @Test
  public void testCopyParamAnnotations() {
    doTest();
  }

  @Test
  public void testInferredNotNullInReturnStatement() {
    doTest();
  }

  @Test
  public void testSkipThrowsDeclaredInLambda() {
    doTest();
  }

  @Test
  public void testChangedReturnType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiType type = ReadAction.computeBlocking(
      () -> PsiType.getJavaLangObject(myFixture.getPsiManager(), GlobalSearchScope.allScope(myFixture.getProject())));
    doTestReturnTypeChanged(type);
  }

  //TODO remove or implement
  public void _testMakeVoidMethodReturnVariable() {
    doTestReturnTypeChanged(PsiTypes.intType());
  }

  @Test
  public void testNoReturnTypesSuggested() {
    doTestReturnTypeChanged(PsiTypes.intType());
  }

  @Test
  public void testMultipleVarsInMethodNoReturnStatementAndAssignment() {
    //return type should not be suggested but still
    doTestReturnTypeChanged(PsiTypes.intType());
  }

  @Test
  public void testReassignFinalFieldInside() {
    doTestReturnTypeChanged(PsiTypes.intType());
  }

  @Test
  public void testReassignFinalFieldInsideWriteOnly() {
    doTest();
  }

  @Test
  public void testShortenClassRefsInNewReturnType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiType type = ReadAction.computeBlocking(
      () -> PsiType.getTypeByName(CommonClassNames.JAVA_UTIL_COLLECTION, myFixture.getProject(), GlobalSearchScope.allScope(myFixture.getProject())));
    doTestReturnTypeChanged(type);
  }

  @Test
  public void testPassFieldAsParameterAndMakeStatic() {
    doTestPassFieldsAsParams();
  }

  @Test
  public void testDefaultNamesConflictResolution() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    settings.LOCAL_VARIABLE_NAME_PREFIX = "_";
    doTest();
  }

  @Test
  public void testInferredNotNull() {
    doTest();
  }

  @Test
  public void testCantPassFieldAsParameter() {
    try {
      doTestPassFieldsAsParams();
      fail("Field was modified inside. Make static should be disabled.");
    }
    catch (PrepareFailedException ignore) {
    }
  }

  @Test
  public void testCantMakeStatic() {
    try {
      doTestPassFieldsAsParams();
      fail("Local method is used. Make static should be disabled.");
    }
    catch (PrepareFailedException ignore) {
    }
  }

  @Test
  public void testConditionalExitCombinedWithNullabilityShouldPreserveVarsUsedInExitStatements() {
    doTest();
  }

  @Test
  public void testSingleExitPOintWithTryFinally() {
    doTest();
  }

  @Test
  public void testLocalVariableModifierList() {
    doTest();
  }

  @Test
  public void testLocalVariableAnnotationsOrder() {
    doTest();
  }

  @Test
  public void testDifferentAnnotations() {
    doTest();
  }

  @Test
  public void testSkipCustomAnnotations() {
    doTest();
  }

  @Test
  public void testNullabilityIsTypeAnnotation() {
    doTest();
  }

  @Test
  public void testKeepDeclarationWithAnnotations() {
    doTest();
  }

  @Test
  public void testFilterAnnotations() {
    doTest();
  }

  @Test
  public void testNullabilityAnnotationOverridden() {
    doTest();
  }

  @Test
  public void testSameAnnotations() {
    doTest();
  }

  @Test
  public void testNormalExitIf() {
    doTest();
  }

  @Test
  public void testNormalExitTry() {
    doTest();
  }

  @Test
  public void testMethodAnnotations() {
    doTest();
  }

  @Test
  public void testNotNullArgument0() {
    doTest();
  }

  @Test
  public void testNotNullArgument1() {
    doTest();
  }

  @Test
  public void testNotNullArgument2() {
    doTest();
  }

  @Test
  public void testNotNullArgument3() {
    doTest();
  }

  @Test
  public void testNotNullArgument4() {
    doTest();
  }

  @Test
  public void testNotNullArgument5() {
    doTest();
  }

  @Test
  public void testNotNullArgument6() {
    doTest();
  }

  @Test
  public void testNotNullArgument7() {
    doTest();
  }

  @Test
  public void testNotNullArgumentTooComplexCode() {
    RegistryValue value = Registry.get("ide.dfa.state.limit");
    int oldValue = value.asInteger();
    try {
      value.setValue(50);
      doTest();
    }finally {
      value.setValue(oldValue);
    }
  }

  @Test
  public void testVariableInLoopWithConditionalBreak() {
    doTest();
  }

  @Test
  public void testNotNullArgumentLambdaBare() {
    doTest();
  }

  @Test
  public void testNotNullArgumentLambdaInIf() {
    doTest();
  }

  @Test
  public void testNotNullArgumentLambdaInIfNoBlock() {
    doTest();
  }

  @Test
  public void testNotNullArgumentLambdaInWhileNoBlock() {
    doTest();
  }

  @Test
  public void testNotNullArgumentLambdaInsideBody() {
    doTest();
  }

  @Test
  public void testNotNullArgumentLambdaInIfInsideBody() {
    doTest();
  }

  @Test
  public void testNotNullArgumentAnonymousClassBare() {
    doTest();
  }

  @Test
  public void testNotNullArgumentAnonymousClassInIf() {
    doTest();
  }

  @Test
  public void testNotNullArgumentAnonymousClassInIfNoBlock() {
    doTest();
  }

  @Test
  public void testNotNullArgumentLambdaInForInitializer() {
    doTest();
  }

  @Test
  public void testEmptyParenthesis() {
    try {
      doTest();
      fail("Should not work for empty parenthesis");
    }
    catch (PrepareFailedException ignore) {
    }
  }

  @Test
  public void testNestedReference() {
    doTest();
  }

  @Test
  public void testQualifyWhenConflictingNamePresent() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      final PsiClass psiClass = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getSelectionModel().getLeadSelectionOffset()), PsiClass.class);
      assertNotNull(psiClass);
      boolean success =
        performExtractMethod(true, true, myFixture.getEditor(), myFixture.getFile(), myFixture.getProject(), false, null, false, null, psiClass.getContainingClass(), null);
      assertTrue(success);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testNoStaticForInnerClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_15, () -> {
      try {
        myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
        myFixture.runInCommand(() -> {
          performExtractMethod(true, true, myFixture.getEditor(), myFixture.getFile(), myFixture.getProject(), false, null, true, null, null, null);
          fail("Static modifier is forbidden inside inner classes");
        });
      } catch (PrepareFailedException ignored){
      }
    });
  }

  @Test
  public void testStaticForNestedClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      performExtractMethod(true, true, myFixture.getEditor(), myFixture.getFile(), myFixture.getProject(), false, null, true, null, null, null);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testStaticForOuterClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      final int caret = myFixture.getEditor().getSelectionModel().getLeadSelectionOffset();
      final PsiClass outerClass = PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(caret), PsiClass.class).getContainingClass();
      performExtractMethod(true, true, myFixture.getEditor(), myFixture.getFile(), myFixture.getProject(), false, null, true, null, outerClass, null);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testDontMakeParametersFinalDueToUsagesInsideAnonymous() {
    doTest();
  }

  @Test
  public void testBuilderChainWithArrayAccess() {
    doTest();
  }

  @Test
  public void testBuilderChainWithArrayAccessExpr() {
    doTest();
  }

  @Test
  public void testBuilderChainWithArrayAccessIf() {
    doTest();
  }

  @Test
  public void testBuilderChainWith2DimArrayAccess() {
    doTest();
  }

  @Test
  public void testCallOnArrayElement() {
    doTest();
  }

  @Test
  public void testCallOn2DimArrayElement() {
    doTest();
  }

  @Test
  public void testCallOnFieldArrayElement() {
    doTest();
  }

  @Test
  public void testExtractedVariableReused() {
    doTest();
  }

  @Test
  public void testExtractBareThenBranch() {
    doTest();
  }

  @Test
  public void testExtractBareElseBranch() {
    doTest();
  }

  //TODO fix formatting
  public void _testExtractBareForBody() {
    doTest();
  }

  @Test
  public void testExtractBareDoWhileBody() {
    doTest();
  }

  @Test
  public void testExtractBracedElseBranch() {
    doTest();
  }

  @Test
  public void testExtractBracedDoWhileBody() {
    doTest();
  }

  @Test
  public void testInferredNotNullInReturnStatementDuplicate() {
    doDuplicatesTest();
  }

  @Test
  public void testNullableCheckBreakDuplicate() {
    doDuplicatesTest();
  }

  @Test
  public void testOutputVariableDuplicate() {
    doDuplicatesTest();
  }

  @Test
  public void testNullableCheckVoidDuplicate() {
    doTest();
  }

  @Test
  public void testWriteDifferentFieldsDuplicate() {
    doDuplicatesTest();
  }

  @Test
  public void testDecrementDifferentFieldsDuplicate() {
    doDuplicatesTest();
  }

  @Test
  public void testDecrementDifferentStaticFieldsDuplicate() {
    doDuplicatesTest();
  }

  @Test
  public void testDecrementDifferentOuterFieldsDuplicate() {
    doDuplicatesTest();
  }

  @Test
  public void testDecrementDifferentInnerFieldsDuplicate() {
    doDuplicatesTest();
  }

  @Test
  public void testDecrementDifferentChainedFieldsDuplicate() {
    doDuplicatesTest();
  }

  @Test
  public void testArgumentFoldingWholeStatement() {
    doDuplicatesTest();
  }

  @Test
  public void testArgumentFoldingWholeStatementForUpdate() {
    doDuplicatesTest();
  }

  @Test
  public void testArgumentFoldingWholeStatementForUpdateList() {
    doDuplicatesTest();
  }

  @Test
  public void testArgumentFoldingMethodCall() {
    doDuplicatesTest();
  }

  @Test
  public void testBoxedConditionalReturn() {
    doDuplicatesTest();
  }

  @Test
  public void testAvoidGenericArgumentCast() {
    doDuplicatesTest();
  }

  @Test
  public void testAvoidGenericArgumentCastLocalClass() {
    doDuplicatesTest();
  }

  @Test
  public void testDuplicatePreserveComments() {
    doDuplicatesTest();
  }

  @Test
  public void testDuplicateSubexpression() {
    doDuplicatesTest();
  }

  @Test
  public void testDuplicateSubexpressionWithParentheses() {
    doDuplicatesTest();
  }

  @Test
  public void testOneVariableExpression() {
    doDuplicatesTest();
  }

  @Test
  public void testInterfaceMethodVisibility() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String doesNotExist = "foo.bar.baz.DoesNotExist";
    final NullableNotNullManager nullManager = NullableNotNullManager.getInstance(myFixture.getProject());

    final List<String> nullables = nullManager.getNullables();
    final List<String> notNulls = nullManager.getNotNulls();
    final String defaultNullable = nullManager.getDefaultNullable();
    final String defaultNotNull = nullManager.getDefaultNotNull();
    try {
      nullManager.setNullables(doesNotExist);
      nullManager.setNotNulls(doesNotExist);
      nullManager.setDefaultNullable(doesNotExist);
      nullManager.setDefaultNotNull(doesNotExist);

      myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
      myFixture.runInCommand(() -> {
        boolean success =
          performExtractMethod(true, true, myFixture.getEditor(), myFixture.getFile(), myFixture.getProject(), false, null, false, null, null, PsiModifier.PUBLIC);
        assertTrue(success);
        myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
      });
    }
    finally {
      nullManager.setNullables(ArrayUtilRt.toStringArray(nullables));
      nullManager.setNotNulls(ArrayUtilRt.toStringArray(notNulls));
      nullManager.setDefaultNullable(defaultNullable);
      nullManager.setDefaultNotNull(defaultNotNull);
    }
  }

  @Test
  public void testBeforeCommentAfterSelectedFragment() {
    doTest();
  }

  @Test
  public void testInsideCommentAfterSelectedFragment() {
    doTest();
  }

  @Test
  public void testEmptyBlockStatement() {
    doExitPointsTest(false);
  }

  @Test
  public void testCallChainExpression() {
    doTest();
  }

  @Test
  public void testFromDefaultMethodInInterface() {
    doTest();
  }

  @Test
  public void testFromPrivateMethodInInterface() {
    doTest();
  }

  @Test
  public void testFromStaticMethodInInterface() {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_8);
  }

  @Test
  public void testDisjunctionType() {
    doTest();
  }

  @Test
  public void testExtractFromAnnotation() {
    try {
      doTest();
      fail("Should not work for annotations");
    }
    catch (PrepareFailedException ignore) {
    }
  }

  @Test
  public void testExtractFromAnnotation1() {
    try {
      doTest();
      fail("Should not work for annotations");
    }
    catch (PrepareFailedException ignore) {
    }
  }

  @Test
  public void testExtractConditionFromSimpleIf() {
    doTest();
  }

  @Test
  public void testExtractConditionFromSimpleIf1() {
    doTest();
  }

  @Test
  public void testSimpleWithNullableDirectlyBeforeType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings instance = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    boolean oldValue = instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE;
    instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = true;
    try {
      doTest();
    }
    finally {
      instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = oldValue;
    }
  }

  @Test
  public void testSimpleWithNullableDirectlyBeforeKeyword() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings instance = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    boolean oldValue = instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE;
    instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = false;
    try {
      doTest();
    }
    finally {
      instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = oldValue;
    }
  }

  @Test
  public void testNullableAnnotationNotTypeUse() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final NullableNotNullManager nullManager = NullableNotNullManager.getInstance(myFixture.getProject());

    final List<String> nullables = nullManager.getNullables();
    final List<String> notNulls = nullManager.getNotNulls();
    final String defaultNullable = nullManager.getDefaultNullable();
    final String defaultNotNull = nullManager.getDefaultNotNull();
    try {
      String nullable = "com.test.Nullable";
      String notNull = "com.test.NotNull";
      nullManager.setNullables(nullable);
      nullManager.setNotNulls(notNull);
      nullManager.setDefaultNullable(nullable);
      nullManager.setDefaultNotNull(notNull);

      doTest();
    }
    finally {
      nullManager.setNullables(ArrayUtilRt.toStringArray(nullables));
      nullManager.setNotNulls(ArrayUtilRt.toStringArray(notNulls));
      nullManager.setDefaultNullable(defaultNullable);
      nullManager.setDefaultNotNull(defaultNotNull);
    }
  }

  @Test
  public void testNotNullContainerNotNullParam() {
    doTest();
  }

  @Test
  public void testNotNullContainerNullableParam() {
    doTest();
  }

  @Test
  public void testNotNullContainerUnknownParam() {
    doTest();
  }

  @Test
  public void testNotNullContainerNotNullResult() {
    doTest();
  }

  @Test
  public void testNotNullContainerNullableResult() {
    doTest();
  }

  @Test
  public void testNotNullContainerUnknownResult() {
    doTest();
  }

  private void doTestReturnTypeChanged(PsiType type) throws PrepareFailedException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      boolean success = performExtractMethod(true, true, myFixture.getEditor(), myFixture.getFile(), myFixture.getProject(), false, type, false, null);
      assertTrue(success);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  private void doTestPassFieldsAsParams() throws PrepareFailedException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      boolean success = performExtractMethod(true, true, myFixture.getEditor(), myFixture.getFile(), myFixture.getProject(), false, null, true, null);
      assertTrue(success);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  private void doPrepareErrorTest(String expectedMessage) {
    String expectedError = null;
    try {
      doErrorTest();
    }
    catch(PrepareFailedException ex) {
      expectedError = ex.getMessage();
    }
    assertEquals(expectedMessage, expectedError);
  }

  private void doErrorTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String fileName = myFixture.getTestName(false) + ".java";
    myFixture.configureByFile(BASE_PATH + fileName);
    myFixture.runInCommand(() -> {
      performAction(false,false);
    });
  }

  private void doExitPointsTest(boolean shouldSucceed) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String fileName = myFixture.getTestName(false) + ".java";
    myFixture.configureByFile(BASE_PATH + fileName);
    boolean succeed = false;
    try {
      doErrorTest();
      succeed = true;
    }
    catch (PrepareFailedException ignore) {}
    assertEquals(shouldSucceed, succeed);
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    doTest(true);
  }

  private void doTest(boolean duplicates) {
    doTest(duplicates, null);
  }

  private void doExactDuplicatesTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest(true, () -> myFixture.getFile().putUserData(ExtractMethodProcessor.SIGNATURE_CHANGE_ALLOWED, Boolean.FALSE));
  }

  private void doTest(boolean duplicates, @Nullable Runnable prepare) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_PATH + myFixture.getTestName(false) + ".java");
    if (prepare != null) prepare.run();
    myFixture.runInCommand(() -> {
      boolean success = performAction(true, duplicates);
      assertTrue(success);
      myFixture.checkResultByFile(BASE_PATH + myFixture.getTestName(false) + "_after.java");
    });
  }

  private boolean performAction(boolean doRefactor, boolean replaceAllDuplicates) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return performExtractMethod(doRefactor, replaceAllDuplicates, myFixture.getEditor(), myFixture.getFile(), myFixture.getProject());
  }

  public static boolean performExtractMethod(boolean doRefactor,
                                             boolean replaceAllDuplicates,
                                             @NotNull Editor editor,
                                             @NotNull PsiFile file,
                                             @NotNull Project project) throws PrepareFailedException, IncorrectOperationException {
    return performExtractMethod(doRefactor, replaceAllDuplicates, editor, file, project, false);
  }

  private static boolean performExtractMethod(boolean doRefactor,
                                              boolean replaceAllDuplicates,
                                              @NotNull Editor editor,
                                              @NotNull PsiFile file,
                                              @NotNull Project project,
                                              boolean extractChainedConstructor) throws PrepareFailedException, IncorrectOperationException {
    return performExtractMethod(doRefactor, replaceAllDuplicates, editor, file, project, extractChainedConstructor,
                                ArrayUtilRt.EMPTY_INT_ARRAY);
  }

  private static boolean performExtractMethod(boolean doRefactor,
                                              boolean replaceAllDuplicates,
                                              @NotNull Editor editor,
                                              @NotNull PsiFile file,
                                              @NotNull Project project,
                                              boolean extractChainedConstructor,
                                              int @NotNull ... disabledParams) throws PrepareFailedException, IncorrectOperationException {
    return performExtractMethod(doRefactor, replaceAllDuplicates, editor, file, project, extractChainedConstructor, null, false, null, disabledParams);
  }

  private static boolean performExtractMethod(boolean doRefactor,
                                              boolean replaceAllDuplicates,
                                              @NotNull Editor editor,
                                              @NotNull PsiFile file,
                                              @NotNull Project project,
                                              boolean extractChainedConstructor,
                                              PsiType returnType,
                                              boolean makeStatic,
                                              String newNameOfFirstParam,
                                              int @NotNull ... disabledParams) throws PrepareFailedException, IncorrectOperationException {
    return performExtractMethod(doRefactor, replaceAllDuplicates, editor, file, project, extractChainedConstructor, returnType, makeStatic,
                                newNameOfFirstParam, null, null, disabledParams);
  }

  private static boolean performExtractMethod(boolean doRefactor,
                                              boolean replaceAllDuplicates,
                                              @NotNull Editor editor,
                                              @NotNull PsiFile file,
                                              @NotNull Project project,
                                              boolean extractChainedConstructor,
                                              PsiType returnType,
                                              boolean makeStatic,
                                              String newNameOfFirstParam,
                                              PsiClass targetClass,
                                              @Nullable @PsiModifier.ModifierConstant String methodVisibility,
                                              int @NotNull ... disabledParams) throws PrepareFailedException, IncorrectOperationException {
    int startOffset = editor.getSelectionModel().getSelectionStart();
    int endOffset = editor.getSelectionModel().getSelectionEnd();

    PsiElement[] elements;
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(file, startOffset, endOffset);
    if (expr != null) {
      elements = new PsiElement[]{expr};
    }
    else {
      elements = CodeInsightUtil.findStatementsInRange(file, startOffset, endOffset);
    }
    if (elements.length == 0) {
      final PsiExpression expression = IntroduceVariableUtil.getSelectedExpression(project, file, startOffset, endOffset);
      if (expression != null) {
        elements = new PsiElement[]{expression};
      }
    }
    assertTrue(elements.length > 0);

    final ExtractMethodProcessor processor =
      new ExtractMethodProcessor(project, editor, elements, null, "Extract Method", "newMethod", null);
    processor.setShowErrorDialogs(false);
    processor.setChainedConstructor(extractChainedConstructor);

    if (ExtractMethodHandler.canUseNewImpl(project, file, elements)) {
      try {
        return new MethodExtractor().doTestExtract(true, editor, extractChainedConstructor, makeStatic, returnType,
                                                   newNameOfFirstParam, targetClass, methodVisibility, disabledParams);
      } catch (ExtractException e) {
        throw new PrepareFailedException(e.getMessage(), file);
      }
    }

    if (!processor.prepare()) {
      return false;
    }

    if (doRefactor) {
      processor.setTargetClass(targetClass);
      processor.testPrepare(returnType, makeStatic);
      if (methodVisibility != null) processor.setMethodVisibility(methodVisibility);
      processor.prepareNullability();
      for (int param : disabledParams) {
        processor.doNotPassParameter(param);
      }
      if (newNameOfFirstParam != null) {
        processor.changeParamName(0, newNameOfFirstParam);
      }
      ExtractMethodHandler.extractMethod(project, processor);
    }

    if (replaceAllDuplicates) {
      final Boolean hasDuplicates = processor.hasDuplicates();
      if (hasDuplicates == null || hasDuplicates.booleanValue()) {
        final List<Match> duplicates = processor.getDuplicates();
        for (Match match : duplicates) {
          if (!match.getMatchStart().isValid() || !match.getMatchEnd().isValid()) continue;
          PsiDocumentManager.getInstance(project).commitAllDocuments();
          ApplicationManager.getApplication().runWriteAction(() -> {
            processor.processMatch(match);
          });
        }
      }
    }

    return true;
  }
}
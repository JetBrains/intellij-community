// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.refactoring.IntroduceVariableUtil;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.extractMethod.newImpl.ExtractException;
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ExtractMethodNewTest extends LightJavaCodeInsightTestCase {
  @NonNls private static final String BASE_PATH = "/refactoring/extractMethodNew/";
  private boolean myCatchOnNewLine = true;

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testExitPoints1() {
    doExitPointsTest(true);
  }

  public void testExitPoints2() {
    doTest();
  }

  public void testExitPoints3() {
    doExitPointsTest(true);
  }

  public void testExitPoints4() {
    doTest();
  }

  public void testExitPoints4Nullable() {
    doExitPointsTest(false);
  }

  public void testExitPointsInsideLoop() {
    doExitPointsTest(true);
  }

  public void testExitPoints5() {
    doTest();
  }

  public void testExitPoints6() {
    doTest();
  }

  public void testExitPoints7() {
    doExitPointsTest(false);
  }

  public void testExitPoints8() {
    doTest();
  }

  public void testExitPoints9() {
    doTest();
  }

  public void testExitPoints10() {
    doExitPointsTest(false);
  }

  public void testExitPoints11() {
    doTest();
  }

  public void testNotNullCheckNameConflicts() {
    doTest();
  }

  public void testContinueInside() {
    doTest();
  }

  public void testBooleanExpression() {
    doTest();
  }

  public void testScr6241() {
    doTest();
  }

  public void testScr7091() {
    doTest();
  }

  public void testScr10464() {
    doTest();
  }

  public void testScr9852() {
    doTest();
  }

  public void testUseVarAfterTry() {
    doTest();
  }

  public void testUseParamInCatch() {
    doExitPointsTest(false);
  }

  public void testUseParamInFinally() {
    doExitPointsTest(false);
  }

  public void testUseVarAfterCatch() {
    doExitPointsTest(false);
  }

  public void testUseVarInCatch1() {
    doTest();
  }

  public void testUseVarInCatch2() {
    doExitPointsTest(false);
  }

  public void testUseVarInCatchInvisible() {
    doTest();
  }

  public void testUseVarInCatchNested1() {
    doTest();
  }

  public void testUseVarInCatchNested2() {
    doExitPointsTest(false);
  }

  public void testUseVarInOtherCatch() {
    doTest();
  }

  public void testUseVarInFinally1() {
    doTest();
  }

  public void testUseVarInFinally2() {
    doExitPointsTest(false);
  }

  public void testOneBranchAssignment() {
    doTest();
  }

  public void testExtractFromCodeBlock() {
    doTest();
  }

  public void testUnusedInitializedVar() {
    doTest();
  }

  public void testTryFinally() {
    doTest();
  }

  public void testFinally() {
    doTest();
  }

  public void testExtractFromAnonymous() {
    doTest();
  }

  public void testSCR12245() {
    try {
      doTest();
      fail("Should not work for assignment expression");
    } catch (PrepareFailedException ignored) {
    }
  }

  public void testLeaveCommentsWhenExpressionExtracted() {
    doTest();
  }

  public void testSCR15815() {
    doTest();
  }

  public void testFieldGroupAnchor() {
    doTest();
  }

  public void testFieldGroupAnchor2() {
    doTest();
  }

  public void testSCR27887() {
    doTest();
  }

  public void testSCR28427() {
    doTest();
  }

  public void testTryFinallyInsideFor() {
    doTest();
  }

  public void testExtractFromTryFinally() {
    doTest();
  }

  public void testInferredReturnType1() {
    doTest();
  }

  public void testInferredReturnType2() {
    doTest();
  }

  public void testInferredReturnType3() {
    doTest();
  }

  public void testInferredReturnType4() {
    doTest();
  }

  public void testInferredReturnType5() {
    doTest();
  }

  public void testInferredReturnType6() {
    doTest();
  }

  public void testNotPassedStaticField() {
    doTestPassFieldsAsParams();
  }

  public void testNotPassedStaticField2() {
    doTestPassFieldsAsParams();
  }

  public void testExtractAssignmentExpression() {
    try {
      doTest();
      fail("Should not work for assignment expression");
    } catch (PrepareFailedException ignored) {
    }
  }

  public void testExtractAssignmentExpressionFromStatement() {
    try {
      doTest();
      fail("Should not work for assignment expression");
    } catch (PrepareFailedException ignored) {
    }
  }

  public void testAssignmentInsideLambda() {
    doTest();
  }

  public void testAssignmentToField() {
    doTest();
  }

  public void testExtractFromTryFinally2() {
    doTest();
  }

  public void testLesyaBug() {
    myCatchOnNewLine = false;
    doTest();
  }

  public void testForEach() {
    doTest();
  }

  public void testAnonInner() {
    doTest();
  }


  public void testConflictingAnonymous() {
    doTest();
  }

  public void testVarDeclAfterExpressionExtraction() {
    doTest();
  }

  public void testFinalParamUsedInsideAnon() {
    JavaCodeStyleSettings.getInstance(getProject()).GENERATE_FINAL_PARAMETERS = false;
    doTestWithJava17();
  }

  private void doTestWithJava17() {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_7);
  }

  private void doTestWithLanguageLevel(LanguageLevel languageLevel) {
    LanguageLevel oldLevel = IdeaTestUtil.setProjectLanguageLevel(getProject(), languageLevel);
    try {
      doTest();
    }
    finally {
      IdeaTestUtil.setProjectLanguageLevel(getProject(), oldLevel);
    }
  }

  public void testNonFinalWritableParam() {
    JavaCodeStyleSettings.getInstance(getProject()).GENERATE_FINAL_PARAMETERS = true;
    doTest();
  }

  public void testCodeDuplicatesWithContinue() {
    doDuplicatesTest();
  }

  public void testDuplicatesFromAnonymous() {
    doDuplicatesTest();
  }

  public void testDuplicatesFullyQualifiedType() {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithContinueNoReturn() {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithStaticInitializer() {
    doDuplicatesTest();
  }

  public void testDuplicateInUnreachableCode() {
    doDuplicatesTest();
  }

  public void testExpressionDuplicates() {
    doDuplicatesTest();
  }

  public void testClassReference() {
    doDuplicatesTest();
  }

  public void testClassReference2() {
    doDuplicatesTest();
  }

  public void testCodeDuplicates() {
    doDuplicatesTest();
  }

  public void testCodeDuplicates2() {
    doDuplicatesTest();
  }

  public void testCodeDuplicates3() {
    doDuplicatesTest();
  }

  public void testCodeDuplicates4() {
    doDuplicatesTest();
  }

  public void testCodeDuplicates5() {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithOutputValue() {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithOutputValue1() {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithEmptyStatementsBlocksParentheses() {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithMultExitPoints() {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithReturn() {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithReturn2() {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithReturnInAnonymous() {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithComments() {
    doDuplicatesTest();
  }

  public void testSCR32924() {
    doDuplicatesTest();
  }

  public void testFinalOutputVar() {
    doDuplicatesTest();
  }

  public void testIdeaDev2291() {
    doTest();
  }

  public void testOxfordBug() {
    doTest();
  }

  public void testIDEADEV33368() {
    doExitPointsTest(false);
  }

  public void testInlineCreated2ReturnLocalVariablesOnly() {
    doTest();
  }

  public void testGuardMethodDuplicates() {
    doDuplicatesTest();
  }

  public void testGuardMethodDuplicates1() {
    doDuplicatesTest();
  }

  public void testInstanceMethodDuplicatesInStaticContext() {
    doDuplicatesTest();
  }


  public void testLValueNotDuplicate() {
    doDuplicatesTest();
  }

  protected void doDuplicatesTest() {
    doTest(true);
  }

  public void testExtractFromFinally() {
    doTest();
  }

  public void testNoShortCircuit() {
    doTest();
  }

  public void testStopFolding() {
    doTest();
  }

  public void testStopFoldingForArrayWriteAccessInConsequentUsages() {
    doTest();
  }

  public void testStopFoldingPostfixInside() {
    doTest();
  }

  public void testFoldedWithNestedExpressions() {
    doTest();
  }

  public void testFoldedWithConflictedNames() {
    doTest();
  }

  public void testFoldingWithFieldInvolved() {
    doTest();
  }

  public void testFoldingWithFunctionCall() {
    doTest();
  }

  public void testDontSkipVariablesUsedInLeftSideOfAssignments() {
    doTest();
  }

  public void testIDEADEV11748() {
    doTest();
  }

  public void testIDEADEV11848() {
    doTest();
  }

  //TODO support as method conflict
  public void _testIDEADEV11036() {
    doTest();
  }

  public void testLocalClass() {
    doPrepareErrorTest("Local class is defined out of the selected block.");
  }

  public void testLocalClassUsage() {
    doPrepareErrorTest("Local class is used out of the selected block.");
  }

  public void testLocalClassScope() {
    doTest();
  }

  public void testStaticImport() {
    doTest();
  }

  public void testThisCall() {
    doTest();
  }

  public void testChainedConstructor() {
    doChainedConstructorTest(false);
  }

  public void testChainedConstructorDuplicates() {
    doChainedConstructorTest(true);
  }

  public void testChainedConstructorInvalidDuplicates() {
    doChainedConstructorTest(true);
  }

  public void testReturnFromTry() {
    doTest();
  }

  public void testLocalClassDefinedInMethodWhichIsUsedLater() {
    doPrepareErrorTest("Local class is used out of the selected block.");
  }

  public void testForceBraces() {
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    int old = settings.IF_BRACE_FORCE;
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    try {
      doTest();
    }
    finally {
      settings.IF_BRACE_FORCE = old;
    }
  }

  public void testConstantConditionsAffectingControlFlow() {
    doTest();
  }

  public void testConstantConditionsAffectingControlFlow1() {
    doTest();
  }
  public void testNotInitializedInsideFinally() {
    doTest();
  }

  public void testGenericsParameters() {
    doTest();
  }

  public void testUnusedGenerics() {
    doTest();
  }

  public void testParamsUsedInLocalClass() {
    doTestWithJava17();
  }

  private void doChainedConstructorTest(boolean replaceAllDuplicates) {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = performExtractMethod(true, replaceAllDuplicates, getEditor(), getFile(), getProject(), true);
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testReassignedVarAfterCall() {
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.GENERATE_FINAL_LOCALS = true;
    doTest();
  }

  public void testNonPhysicalAssumptions() {
    doTest();
  }

  public void testNullableCheck() {
    doTest();
  }

  public void testNullableCheck1() {
    doTest();
  }

  public void testNullableCheckVoid() {
    doTest();
  }

  public void testNullableCheckDontMissFinal() {
    doTest();
  }

  public void testNullableCheckBreak() {
    doTest();
  }

  public void testSimpleArrayAccess() {
    doTest();
  }

  public void testArrayAccess() {
    doTest();
  }

  public void testArrayAccess1() {
    doTest();
  }

  public void testArrayAccessWithLocalIndex() {
    doTest();
  }

  public void testArrayAccessWithTopExpression() {
    doTest();
  }

  public void testArrayAccessWithDuplicates() {
    doDuplicatesTest();
  }

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

  public void testIgnoreSwitchBreak() {
    doTest();
  }

  public void testExtractSwitchVariable() {
    doTest();
  }

  public void testReturnVariablesResolved() {
    doTest();
  }

  public void testExtractBranchedPrimitive() {
    doTest();
  }

  public void testExtractSwitchNotNullVariable() {
    doTest();
  }

  public void testAvoidContinueInsideMethod() {
    doTest();
  }

  public void testLocalVariablesAreNotExposed() {
    doTest();
  }

  public void testExtractConditionalContinue() {
    doTest();
  }

  public void testExtractConditionalBreak() {
    doTest();
  }

  public void testDontMissReturn() {
    doTest();
  }

  public void testNoRedundantContinue() {
    doTest();
  }

  public void testDontReplaceThrowWithReturn() {
    doTest();
  }

  public void testDontMissReturnDueToThrowable() {
    doTest();
  }

  public void testDontExtractInsideSwitch() {
    try {
      doTest();
      fail("Should fail inside switch");
    } catch (PrepareFailedException ignore) {
    }
  }

  public void testDontExtractFieldWithConstructor() {
    doChainedConstructorTest(false);
  }

  public void testExtractSingleLabelFromSwitch() {
    doTest();
  }

  public void testExtractVariableAndReturn() {
    doTest();
  }

  public void testExtractVariableAndReturn1() {
    try {
      doTest();
      fail("Should not extract different returns");
    } catch (PrepareFailedException ignore) {
    }
  }

  public void testExtractVariableAndReturn2() {
    try {
      doTest();
      fail("Should not extract internal references");
    } catch (PrepareFailedException ignore) {
    }
  }

  public void testExtractVariableAndReturn3() {
    try {
      doTest();
      fail("Should not extract semantically different references");
    } catch (PrepareFailedException ignore) {
    }
  }

  public void testDontExtractUnfoldableVariable() {
    try {
      doTest();
      fail("Should not extract nullable variable if primitive type declared outside");
    } catch (PrepareFailedException ignore) {
    }
  }

  public void testExtractConstantExpressions() {
      doTest();
  }

  public void testDontExtractLocalConstant() {
    try {
      doTest();
      fail("Should fail if expression is linked to the scope");
    } catch (PrepareFailedException ignore) {
    }
  }

  public void testDontExtractCustomFinalObjects() {
    try {
      doTest();
      fail("Should fail if expression contains mutable object");
    } catch (PrepareFailedException ignore) {
    }
  }

  public void testWriteArrayAccess() {
    doTest();
  }

  public void testShortCircuit() {
    doTest();
  }

  public void testRecursiveCallToExtracted() {
    doTest();
  }

  public void testCodeDuplicatesVarargsShouldNotChangeReturnType() {
    doDuplicatesTest();
  }

  public void testVarargsArguments() {
    doTest();
  }

  public void testParametersFromAnonymous() {
    doTest();
  }

  public void testCast4ParamGeneration() {
    doTest();
  }

  public void testNearComment() {
    doTest();
  }

  public void testFoldInWhile() {
    doTest();
  }

  public void testFoldedParamNameSuggestion() {
    doTest();
  }

  public void testNonFoldInIfBody() {
    doTest();
  }

  public void testComplexTypeParams() {
    doTest();
  }

  public void testExtractWithLeadingComment() {
    doTest();
  }

  public void testInvalidReference() {
    doTest();
  }

  public void testRedundantCast() {
    doTest();
  }

  public void testDisabledParam() {
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, 0);
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testTypeParamsList() {
    doTest();
  }

  public void testTypeParamsListWithRecursiveDependencies() {
    doTest();
  }

  public void testFromLambdaBody() {
    doTest();
  }

  public void testFromLambdaBody1() {
    doTest();
  }

  public void testFromLambdaBodyCapturedWildcardParams() {
    doTest();
  }

  public void testFromLambdaBodyToAnonymous() {
    doTest();
  }

  public void testFromLambdaBodyToToplevelInsideCodeBlock() {
    doTest();
  }

  public void testFromLambdaBodyWithReturn() {
    doTest();
  }

  public void testOneLineLambda() {
    doTest();
  }

  public void testMethod2Interface() {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_8);
  }

  public void testMethod2InterfaceFromStatic() {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_8);
  }

  public void testMethod2InterfaceFromConstant() {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_8);
  }

  public void testParamDetection() {
    doTest();
  }

  public void testSkipComments() {
    doTest();
  }

  public void testFinalParams4LocalClasses() {
    doTestWithJava17();
  }

  public void testIncompleteExpression() {
    doTest();
  }

  public void testTwoFromThreeEqStatements() {
    doDuplicatesTest();
  }

  public void testCastWhenDuplicateReplacement() {
    doDuplicatesTest();
  }

  public void testCheckQualifierMapping() {
    doDuplicatesTest();
  }

  public void testArrayReturnType() {
    doDuplicatesTest();
  }

  public void testOverloadedMethods() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureOneParam() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureOneParamMultipleTimesInside() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureLeaveSameExpressionsUntouched() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureSameParamNames() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureCallToSameClassMethod() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureInitialParameterUnused() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureWithFolding() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureWithArrayFolding() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureWithGetterFolding() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureWithMultiFolding() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureWithTwoWayFolding() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureSameSubexpression() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureSameSubexpressionWholeLine() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureTrivialMethod() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignaturePlusOneFolding() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureVoidCallFolding() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureThreeOccurrencesTwoLiteralFolding() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureFourOccurrencesTwoLiteralFolding() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureThreeOccurrencesTwoVariableFolding() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureFourOccurrencesTwoVariableFolding() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureWithOutputVariables() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureEqualConstExprFolding() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureLongConstExprFolding() {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureTwoFieldInitializers() {
    doDuplicatesTest();
  }

  public void testConditionalReturnInDuplicate() {
    doDuplicatesTest();
  }

  // todo DuplicatesFinder.canBeEquivalent() should see the difference between 'return' and assignment
  public void _testConditionalReturnVsAssignDuplicate() {
    doDuplicatesTest();
  }

  public void testConditionalWithTwoParameters() {
    doDuplicatesTest();
  }

  public void testOverlappingDuplicate() {
    doDuplicatesTest();
  }

  public void testEffectivelyLocalVariables() {
    doDuplicatesTest();
  }

  public void testEffectivelyLocalWithinExpression() {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateNestedSubexpression() {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateDeclaredOutputVariable() {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateDeclaredReusedVariable() {
    doDuplicatesTest();
  }

  public void testExactDuplicateDeclaredReusedVariable() {
    doDuplicatesTest();
  }

  public void testExactDuplicateTwoDeclaredReusedVariables() {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateUnfoldArrayArgument() {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateFoldListElement() {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateFoldArrayElement() {
    doDuplicatesTest();
  }

  public void testParametrizedMultiDuplicatesFoldArrayElement() {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateFoldArrayElementTwoUsages() {
    doDuplicatesTest();
  }

  public void testTripleParametrizedDuplicate() {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateKeepSignature() {
    doExactDuplicatesTest();
  }

  public void testRejectParametrizedDuplicate() {
    doExactDuplicatesTest();
  }

  public void testParametrizedDuplicateRepeatedArguments() {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateTripleRepeatedArguments() {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateExactlyRepeatedArguments() {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateExpression() {
    doDuplicatesTest();
  }

  public void testPatternVariable() {
    doTestWithLanguageLevel(LanguageLevel.HIGHEST);
  }

  public void testPatternVariableIntroduced() {
    doExitPointsTest(false);
  }

  public void testPatternVariableIntroduced2() {
    doExitPointsTest(false);
  }

  public void testPatternVariableIntroduced3() {
    doTestWithLanguageLevel(LanguageLevel.HIGHEST);
  }

  public void testSuggestChangeSignatureWithChangedParameterName() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, null, false, "p");
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testTargetAnonymous() {
    doTest();
  }

  public void testSimpleMethodsInOneLine() {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    doTest();
  }

  public void testExtractUnresolvedLambdaParameter() {
    try {
      doTest();
      fail("Should not work for single lambda parameter");
    }
    catch (PrepareFailedException ignore) {}
  }

  public void testExtractUnresolvedLambdaExpression() {
    doTest();
  }

  public void testNoNPE1() {
    doTest();
  }

  public void testNoNPE2() {
    doTest();
  }

  public void testTheOnlyParenthesisExpressionWhichIsSkippedInControlFlow() {
    doTest();
  }

  public void testExpression() {
    doTestWithJava17();
  }

  public void testNonPhysicalSubexpression() {
    doTest();
  }

  public void testCopyParamAnnotations() {
    doTest();
  }

  public void testInferredNotNullInReturnStatement() {
    doTest();
  }

  public void testSkipThrowsDeclaredInLambda() {
    doTest();
  }

  public void testChangedReturnType() {
    doTestReturnTypeChanged(PsiType.getJavaLangObject(getPsiManager(), GlobalSearchScope.allScope(getProject())));
  }

  //TODO remove or implement
  public void _testMakeVoidMethodReturnVariable() {
    doTestReturnTypeChanged(PsiTypes.intType());
  }

  public void testNoReturnTypesSuggested() {
    doTestReturnTypeChanged(PsiTypes.intType());
  }

  public void testMultipleVarsInMethodNoReturnStatementAndAssignment() {
    //return type should not be suggested but still
    doTestReturnTypeChanged(PsiTypes.intType());
  }

  public void testReassignFinalFieldInside() {
    doTestReturnTypeChanged(PsiTypes.intType());
  }

  public void testShortenClassRefsInNewReturnType() {
    doTestReturnTypeChanged(PsiType.getTypeByName(CommonClassNames.JAVA_UTIL_COLLECTION, getProject(), GlobalSearchScope.allScope(getProject())));
  }

  public void testPassFieldAsParameterAndMakeStatic() {
    doTestPassFieldsAsParams();
  }

  public void testDefaultNamesConflictResolution() {
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.LOCAL_VARIABLE_NAME_PREFIX = "_";
    doTest();
  }

  public void testInferredNotNull() {
    doTest();
  }

  public void testCantPassFieldAsParameter() {
    try {
      doTestPassFieldsAsParams();
      fail("Field was modified inside. Make static should be disabled.");
    }
    catch (PrepareFailedException ignore) {
    }
  }

  public void testCantMakeStatic() {
    try {
      doTestPassFieldsAsParams();
      fail("Local method is used. Make static should be disabled.");
    }
    catch (PrepareFailedException ignore) {
    }
  }

  public void testConditionalExitCombinedWithNullabilityShouldPreserveVarsUsedInExitStatements() {
    doTest();
  }

  public void testSingleExitPOintWithTryFinally() {
    doTest();
  }

  public void testLocalVariableModifierList() {
    doTest();
  }

  public void testLocalVariableAnnotationsOrder() {
    doTest();
  }

  public void testDifferentAnnotations() {
    doTest();
  }

  public void testSkipCustomAnnotations() {
    doTest();
  }

  public void testNullabilityIsTypeAnnotation() {
    doTest();
  }

  public void testKeepDeclarationWithAnnotations() {
    doTest();
  }

  public void testFilterAnnotations() {
    doTest();
  }

  public void testNullabilityAnnotationOverridden() {
    doTest();
  }

  public void testSameAnnotations() {
    doTest();
  }

  public void testNormalExitIf() {
    doTest();
  }

  public void testNormalExitTry() {
    doTest();
  }

  public void testMethodAnnotations() {
    doTest();
  }

  public void testNotNullArgument0() {
    doTest();
  }

  public void testNotNullArgument1() {
    doTest();
  }

  public void testNotNullArgument2() {
    doTest();
  }

  public void testNotNullArgument3() {
    doTest();
  }

  public void testNotNullArgument4() {
    doTest();
  }

  public void testNotNullArgument5() {
    doTest();
  }

  public void testNotNullArgument6() {
    doTest();
  }

  public void testNotNullArgument7() {
    doTest();
  }

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

  public void testVariableInLoopWithConditionalBreak() {
    doTest();
  }

  public void testNotNullArgumentLambdaBare() {
    doTest();
  }

  public void testNotNullArgumentLambdaInIf() {
    doTest();
  }

  public void testNotNullArgumentLambdaInIfNoBlock() {
    doTest();
  }

  public void testNotNullArgumentLambdaInWhileNoBlock() {
    doTest();
  }

  public void testNotNullArgumentLambdaInsideBody() {
    doTest();
  }

  public void testNotNullArgumentLambdaInIfInsideBody() {
    doTest();
  }

  public void testNotNullArgumentAnonymousClassBare() {
    doTest();
  }

  public void testNotNullArgumentAnonymousClassInIf() {
    doTest();
  }

  public void testNotNullArgumentAnonymousClassInIfNoBlock() {
    doTest();
  }

  public void testNotNullArgumentLambdaInForInitializer() {
    doTest();
  }

  public void testEmptyParenthesis() {
    try {
      doTest();
      fail("Should not work for empty parenthesis");
    }
    catch (PrepareFailedException ignore) {
    }
  }

  public void testNestedReference() {
    doTest();
  }

  public void testQualifyWhenConflictingNamePresent() {
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getSelectionModel().getLeadSelectionOffset()), PsiClass.class);
    assertNotNull(psiClass);
    boolean success =
      performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, null, false, null, psiClass.getContainingClass(), null);
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testNoStaticForInnerClass() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_15, () -> {
      try {
        configureByFile(BASE_PATH + getTestName(false) + ".java");
        performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, null, true, null, null, null);
        fail("Static modifier is forbidden inside inner classes");
      } catch (PrepareFailedException ignored){
      }
    });
  }

  public void testStaticForNestedClass() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, null, true, null, null, null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testStaticForOuterClass() {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    final int caret = getEditor().getSelectionModel().getLeadSelectionOffset();
    final PsiClass outerClass = PsiTreeUtil.getParentOfType(getFile().findElementAt(caret), PsiClass.class).getContainingClass();
    performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, null, true, null, outerClass, null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testDontMakeParametersFinalDueToUsagesInsideAnonymous() {
    doTest();
  }

  public void testBuilderChainWithArrayAccess() {
    doTest();
  }

  public void testBuilderChainWithArrayAccessExpr() {
    doTest();
  }

  public void testBuilderChainWithArrayAccessIf() {
    doTest();
  }

  public void testBuilderChainWith2DimArrayAccess() {
    doTest();
  }

  public void testCallOnArrayElement() {
    doTest();
  }

  public void testCallOn2DimArrayElement() {
    doTest();
  }

  public void testCallOnFieldArrayElement() {
    doTest();
  }

  public void testExtractedVariableReused() {
    doTest();
  }

  public void testExtractBareThenBranch() {
    doTest();
  }

  public void testExtractBareElseBranch() {
    doTest();
  }

  //TODO fix formatting
  public void _testExtractBareForBody() {
    doTest();
  }

  public void testExtractBareDoWhileBody() {
    doTest();
  }

  public void testExtractBracedElseBranch() {
    doTest();
  }

  public void testExtractBracedDoWhileBody() {
    doTest();
  }

  public void testInferredNotNullInReturnStatementDuplicate() {
    doDuplicatesTest();
  }

  public void testNullableCheckBreakDuplicate() {
    doDuplicatesTest();
  }

  public void testOutputVariableDuplicate() {
    doDuplicatesTest();
  }

  public void testNullableCheckVoidDuplicate() {
    doTest();
  }

  public void testWriteDifferentFieldsDuplicate() {
    doDuplicatesTest();
  }

  public void testDecrementDifferentFieldsDuplicate() {
    doDuplicatesTest();
  }

  public void testDecrementDifferentStaticFieldsDuplicate() {
    doDuplicatesTest();
  }

  public void testDecrementDifferentOuterFieldsDuplicate() {
    doDuplicatesTest();
  }

  public void testDecrementDifferentInnerFieldsDuplicate() {
    doDuplicatesTest();
  }

  public void testDecrementDifferentChainedFieldsDuplicate() {
    doDuplicatesTest();
  }

  public void testArgumentFoldingWholeStatement() {
    doDuplicatesTest();
  }

  public void testArgumentFoldingWholeStatementForUpdate() {
    doDuplicatesTest();
  }

  public void testArgumentFoldingWholeStatementForUpdateList() {
    doDuplicatesTest();
  }

  public void testArgumentFoldingMethodCall() {
    doDuplicatesTest();
  }

  public void testBoxedConditionalReturn() {
    doDuplicatesTest();
  }

  public void testAvoidGenericArgumentCast() {
    doDuplicatesTest();
  }

  public void testAvoidGenericArgumentCastLocalClass() {
    doDuplicatesTest();
  }

  public void testDuplicatePreserveComments() {
    doDuplicatesTest();
  }

  public void testDuplicateSubexpression() {
    doDuplicatesTest();
  }

  public void testDuplicateSubexpressionWithParentheses() {
    doDuplicatesTest();
  }

  public void testOneVariableExpression() {
    doDuplicatesTest();
  }

  public void testInterfaceMethodVisibility() {
    final String doesNotExist = "foo.bar.baz.DoesNotExist";
    final NullableNotNullManager nullManager = NullableNotNullManager.getInstance(getProject());

    final List<String> nullables = nullManager.getNullables();
    final List<String> notNulls = nullManager.getNotNulls();
    final String defaultNullable = nullManager.getDefaultNullable();
    final String defaultNotNull = nullManager.getDefaultNotNull();
    try {
      nullManager.setNullables(doesNotExist);
      nullManager.setNotNulls(doesNotExist);
      nullManager.setDefaultNullable(doesNotExist);
      nullManager.setDefaultNotNull(doesNotExist);

      configureByFile(BASE_PATH + getTestName(false) + ".java");
      boolean success =
        performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, null, false, null, null, PsiModifier.PUBLIC);
      assertTrue(success);
      checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
    }
    finally {
      nullManager.setNullables(ArrayUtilRt.toStringArray(nullables));
      nullManager.setNotNulls(ArrayUtilRt.toStringArray(notNulls));
      nullManager.setDefaultNullable(defaultNullable);
      nullManager.setDefaultNotNull(defaultNotNull);
    }
  }

  public void testBeforeCommentAfterSelectedFragment() {
    doTest();
  }

  public void testInsideCommentAfterSelectedFragment() {
    doTest();
  }

  public void testEmptyBlockStatement() {
    doExitPointsTest(false);
  }

  public void testCallChainExpression() {
    doTest();
  }

  public void testFromDefaultMethodInInterface() {
    doTest();
  }

  public void testFromPrivateMethodInInterface() {
    doTest();
  }

  public void testFromStaticMethodInInterface() {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_8);
  }

  public void testDisjunctionType() {
    doTest();
  }

  public void testExtractFromAnnotation() {
    try {
      doTest();
      fail("Should not work for annotations");
    }
    catch (PrepareFailedException ignore) {
    }
  }

  public void testExtractFromAnnotation1() {
    try {
      doTest();
      fail("Should not work for annotations");
    }
    catch (PrepareFailedException ignore) {
    }
  }

  public void testExtractConditionFromSimpleIf() {
    doTest();
  }

  public void testExtractConditionFromSimpleIf1() {
    doTest();
  }

  public void testSimpleWithNullableDirectlyBeforeType() {
    JavaCodeStyleSettings instance = JavaCodeStyleSettings.getInstance(getProject());
    boolean oldValue = instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE;
    instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = true;
    try {
      doTest();
    }
    finally {
      instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = oldValue;
    }
  }

  public void testSimpleWithNullableDirectlyBeforeKeyword() {
    JavaCodeStyleSettings instance = JavaCodeStyleSettings.getInstance(getProject());
    boolean oldValue = instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE;
    instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = false;
    try {
      doTest();
    }
    finally {
      instance.GENERATE_USE_TYPE_ANNOTATION_BEFORE_TYPE = oldValue;
    }
  }

  public void testNullableAnnotationNotTypeUse() {
    final NullableNotNullManager nullManager = NullableNotNullManager.getInstance(getProject());

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

  public void testNotNullContainerNotNullParam() {
    doTest();
  }

  public void testNotNullContainerNullableParam() {
    doTest();
  }

  public void testNotNullContainerUnknownParam() {
    doTest();
  }

  public void testNotNullContainerNotNullResult() {
    doTest();
  }

  public void testNotNullContainerNullableResult() {
    doTest();
  }

  public void testNotNullContainerUnknownResult() {
    doTest();
  }

  private void doTestReturnTypeChanged(PsiType type) throws PrepareFailedException {
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, type, false, null);
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  private void doTestPassFieldsAsParams() throws PrepareFailedException {
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, null, true, null);
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
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
    String fileName = getTestName(false) + ".java";
    configureByFile(BASE_PATH + fileName);
    performAction(false,false);
  }

  private void doExitPointsTest(boolean shouldSucceed) {
    String fileName = getTestName(false) + ".java";
    configureByFile(BASE_PATH + fileName);
    boolean succeed = false;
    try {
      doErrorTest();
      succeed = true;
    }
    catch (PrepareFailedException ignore) {}
    assertEquals(shouldSucceed, succeed);
  }

  private void doTest() {
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    doTest(true);
  }

  private void doTest(boolean duplicates) {
    doTest(duplicates, null);
  }

  private void doExactDuplicatesTest() {
    doTest(true, () -> getFile().putUserData(ExtractMethodProcessor.SIGNATURE_CHANGE_ALLOWED, Boolean.FALSE));
  }

  private void doTest(boolean duplicates, @Nullable Runnable prepare) {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    if (prepare != null) prepare.run();
    boolean success = performAction(true, duplicates);
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  private boolean performAction(boolean doRefactor, boolean replaceAllDuplicates) {
    return performExtractMethod(doRefactor, replaceAllDuplicates, getEditor(), getFile(), getProject());
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
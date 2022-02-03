// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
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
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.util.duplicates.Match;
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

  public void testExitPoints1() throws Exception {
    doExitPointsTest(true);
  }

  public void testExitPoints2() throws Exception {
    doTest();
  }

  public void testExitPoints3() throws Exception {
    doExitPointsTest(true);
  }

  public void testExitPoints4() throws Exception {
    doTest();
  }

  public void testExitPoints4Nullable() throws Exception {
    doExitPointsTest(false);
  }

  public void testExitPointsInsideLoop() throws Exception {
    doExitPointsTest(true);
  }

  public void testExitPoints5() throws Exception {
    doTest();
  }

  public void testExitPoints6() throws Exception {
    doTest();
  }

  public void testExitPoints7() throws Exception {
    doExitPointsTest(false);
  }

  public void testExitPoints8() throws Exception {
    doTest();
  }

  public void testExitPoints9() throws Exception {
    doTest();
  }

  public void testExitPoints10() throws Exception {
    doExitPointsTest(false);
  }

  public void testExitPoints11() throws Exception {
    doTest();
  }

  public void testNotNullCheckNameConflicts() throws Exception {
    doTest();
  }

  public void testContinueInside() throws Exception {
    doTest();
  }

  public void testBooleanExpression() throws Exception {
    doTest();
  }

  public void testScr6241() throws Exception {
    doTest();
  }

  public void testScr7091() throws Exception {
    doTest();
  }

  public void testScr10464() throws Exception {
    doTest();
  }

  public void testScr9852() throws Exception {
    doTest();
  }

  public void testUseVarAfterTry() throws Exception {
    doTest();
  }

  public void testUseParamInCatch() throws Exception {
    doExitPointsTest(false);
  }

  public void testUseParamInFinally() throws Exception {
    doExitPointsTest(false);
  }

  public void testUseVarAfterCatch() throws Exception {
    doExitPointsTest(false);
  }

  public void testUseVarInCatch1() throws Exception {
    doTest();
  }

  public void testUseVarInCatch2() throws Exception {
    doExitPointsTest(false);
  }

  public void testUseVarInCatchInvisible() throws Exception {
    doTest();
  }

  public void testUseVarInCatchNested1() throws Exception {
    doTest();
  }

  public void testUseVarInCatchNested2() throws Exception {
    doExitPointsTest(false);
  }

  public void testUseVarInOtherCatch() throws Exception {
    doTest();
  }

  public void testUseVarInFinally1() throws Exception {
    doTest();
  }

  public void testUseVarInFinally2() throws Exception {
    doExitPointsTest(false);
  }

  public void testOneBranchAssignment() throws Exception {
    doTest();
  }

  public void testExtractFromCodeBlock() throws Exception {
    doTest();
  }

  public void testUnusedInitializedVar() throws Exception {
    doTest();
  }

  public void testTryFinally() throws Exception {
    doTest();
  }

  public void testFinally() throws Exception {
    doTest();
  }

  public void testExtractFromAnonymous() throws Exception {
    doTest();
  }

  public void testSCR12245() throws Exception {
    try {
      doTest();
      fail("Should not work for assignment expression");
    } catch (PrepareFailedException e){
    }
  }

  public void testLeaveCommentsWhenExpressionExtracted() throws Exception {
    doTest();
  }

  public void testSCR15815() throws Exception {
    doTest();
  }

  public void testFieldGroupAnchor() throws Exception {
    doTest();
  }

  public void testFieldGroupAnchor2() throws Exception {
    doTest();
  }

  public void testSCR27887() throws Exception {
    doTest();
  }

  public void testSCR28427() throws Exception {
    doTest();
  }

  public void testTryFinallyInsideFor() throws Exception {
    doTest();
  }

  public void testExtractFromTryFinally() throws Exception {
    doTest();
  }

  public void testInferredReturnType1() throws Exception {
    doTest();
  }

  public void testInferredReturnType2() throws Exception {
    doTest();
  }

  public void testInferredReturnType3() throws Exception {
    doTest();
  }

  public void testInferredReturnType4() throws Exception {
    doTest();
  }

  public void testInferredReturnType5() throws Exception {
    doTest();
  }

  public void testInferredReturnType6() throws Exception {
    doTest();
  }

  public void testNotPassedStaticField() throws Exception {
    doTestPassFieldsAsParams();
  }

  public void testNotPassedStaticField2() throws Exception {
    doTestPassFieldsAsParams();
  }

  public void testExtractAssignmentExpression() throws Exception {
    try {
      doTest();
      fail("Should not work for assignment expression");
    } catch (PrepareFailedException e){
    }
  }

  public void testExtractAssignmentExpressionFromStatement() throws Exception {
    try {
      doTest();
      fail("Should not work for assignment expression");
    } catch (PrepareFailedException e){
    }
  }

  public void testExtractFromTryFinally2() throws Exception {
    doTest();
  }

  public void testLesyaBug() throws Exception {
    myCatchOnNewLine = false;
    doTest();
  }

  public void testForEach() throws Exception {
    doTest();
  }

  public void testAnonInner() throws Exception {
    doTest();
  }


  public void testConflictingAnonymous() throws Exception {
    doTest();
  }

  public void testVarDeclAfterExpressionExtraction() throws Exception {
    doTest();
  }

  public void testFinalParamUsedInsideAnon() throws Exception {
    JavaCodeStyleSettings.getInstance(getProject()).GENERATE_FINAL_PARAMETERS = false;
    doTestWithJava17();
  }

  private void doTestWithJava17() throws Exception {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_7);
  }

  private void doTestWithLanguageLevel(LanguageLevel languageLevel) throws Exception {
    LanguageLevelProjectExtension projectExtension = LanguageLevelProjectExtension.getInstance(getProject());
    LanguageLevel oldLevel = projectExtension.getLanguageLevel();
    try {
      projectExtension.setLanguageLevel(languageLevel);
      doTest();
    }
    finally {
      projectExtension.setLanguageLevel(oldLevel);
    }
  }

  public void testNonFinalWritableParam() throws Exception {
    JavaCodeStyleSettings.getInstance(getProject()).GENERATE_FINAL_PARAMETERS = true;
    doTest();
  }

  public void testCodeDuplicatesWithContinue() throws Exception {
    doDuplicatesTest();
  }

  public void testDuplicatesFromAnonymous() throws Exception {
    doDuplicatesTest();
  }

  public void testDuplicatesFullyQualifiedType() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithContinueNoReturn() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithStaticInitializer() throws Exception {
    doDuplicatesTest();
  }

  public void testDuplicateInUnreachableCode() throws Exception {
    doDuplicatesTest();
  }

  public void testExpressionDuplicates() throws Exception {
    doDuplicatesTest();
  }

  public void testClassReference() throws Exception {
    doDuplicatesTest();
  }

  public void testClassReference2() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates2() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates3() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates4() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicates5() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithOutputValue() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithOutputValue1() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithEmptyStatementsBlocksParentheses() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithMultExitPoints() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithReturn() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithReturn2() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithReturnInAnonymous() throws Exception {
    doDuplicatesTest();
  }

  public void testCodeDuplicatesWithComments() throws Exception {
    doDuplicatesTest();
  }

  public void testSCR32924() throws Exception {
    doDuplicatesTest();
  }

  public void testFinalOutputVar() throws Exception {
    doDuplicatesTest();
  }

  public void testIdeaDev2291() throws Exception {
    doTest();
  }

  public void testOxfordBug() throws Exception {
    doTest();
  }

  public void testIDEADEV33368() throws Exception {
    doExitPointsTest(false);
  }

  public void testInlineCreated2ReturnLocalVariablesOnly() throws Exception {
    doTest();
  }

  public void testGuardMethodDuplicates() throws Exception {
    doDuplicatesTest();
  }

  public void testGuardMethodDuplicates1() throws Exception {
    doDuplicatesTest();
  }

  public void testInstanceMethodDuplicatesInStaticContext() throws Exception {
    doDuplicatesTest();
  }


  public void testLValueNotDuplicate() throws Exception {
    doDuplicatesTest();
  }

  protected void doDuplicatesTest() throws Exception {
    doTest(true);
  }

  public void testExtractFromFinally() throws Exception {
    doTest();
  }

  public void testNoShortCircuit() throws Exception {
    doTest();
  }

  public void testStopFolding() throws Exception {
    doTest();
  }

  public void testStopFoldingForArrayWriteAccessInConsequentUsages() throws Exception {
    doTest();
  }

  public void testStopFoldingPostfixInside() throws Exception {
    doTest();
  }

  public void testFoldedWithNestedExpressions() throws Exception {
    doTest();
  }

  public void testFoldedWithConflictedNames() throws Exception {
    doTest();
  }

  public void testFoldingWithFieldInvolved() throws Exception {
    doTest();
  }

  public void testFoldingWithFunctionCall() throws Exception {
    doTest();
  }

  public void testDontSkipVariablesUsedInLeftSideOfAssignments() throws Exception {
    doTest();
  }

  public void testIDEADEV11748() throws Exception {
    doTest();
  }

  public void testIDEADEV11848() throws Exception {
    doTest();
  }

  //TODO support as method conflict
  public void _testIDEADEV11036() throws Exception {
    doTest();
  }

  public void testLocalClass() throws Exception {
    doPrepareErrorTest("Local class is defined out of the selected block.");
  }

  public void testLocalClassUsage() throws Exception {
    doPrepareErrorTest("Local class is used out of the selected block.");
  }

  public void testLocalClassScope() throws Exception {
    doTest();
  }

  public void testStaticImport() throws Exception {
    doTest();
  }

  public void testThisCall() throws Exception {
    doTest();
  }

  public void testChainedConstructor() throws Exception {
    doChainedConstructorTest(false);
  }

  public void testChainedConstructorDuplicates() throws Exception {
    doChainedConstructorTest(true);
  }

  public void testChainedConstructorInvalidDuplicates() throws Exception {
    doChainedConstructorTest(true);
  }

  public void testReturnFromTry() throws Exception {
    doTest();
  }

  public void testLocalClassDefinedInMethodWhichIsUsedLater() throws Exception {
    doPrepareErrorTest("Local class is used out of the selected block.");
  }

  public void testForceBraces() throws Exception {
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

  public void testConstantConditionsAffectingControlFlow() throws Exception {
    doTest();
  }

  public void testConstantConditionsAffectingControlFlow1() throws Exception {
    doTest();
  }
  public void testNotInitializedInsideFinally() throws Exception {
    doTest();
  }

  public void testGenericsParameters() throws Exception {
    doTest();
  }

  public void testUnusedGenerics() throws Exception {
    doTest();
  }

  public void testParamsUsedInLocalClass() throws Exception {
    doTestWithJava17();
  }

  private void doChainedConstructorTest(final boolean replaceAllDuplicates) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = performExtractMethod(true, replaceAllDuplicates, getEditor(), getFile(), getProject(), true);
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testReassignedVarAfterCall() throws Exception {
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.GENERATE_FINAL_LOCALS = true;
    doTest();
  }

  public void testNonPhysicalAssumptions() throws Exception {
    doTest();
  }

  public void testNullableCheck() throws Exception {
    doTest();
  }

  public void testNullableCheck1() throws Exception {
    doTest();
  }

  public void testNullableCheckVoid() throws Exception {
    doTest();
  }

  public void testNullableCheckDontMissFinal() throws Exception {
    doTest();
  }

  public void testNullableCheckBreak() throws Exception {
    doTest();
  }

  public void testSimpleArrayAccess() throws Exception {
    doTest();
  }

  public void testArrayAccess() throws Exception {
    doTest();
  }

  public void testArrayAccess1() throws Exception {
    doTest();
  }

  public void testArrayAccessWithLocalIndex() throws Exception {
    doTest();
  }

  public void testArrayAccessWithTopExpression() throws Exception {
    doTest();
  }

  public void testArrayAccessWithDuplicates() throws Exception {
    doDuplicatesTest();
  }

  public void testVerboseArrayAccess() throws Exception {
    doTest();
  }

  //TODO keep bad formatting if possible
  public void _testParameterFormatting() throws Exception {
    doTest();
  }

  public void _testNoExpressionFormatting1() throws Exception {
    doTest();
  }

  public void _testNoExpressionFormatting2() throws Exception {
    doTest();
  }

  public void testIgnoreSwitchBreak() throws Exception {
    doTest();
  }

  public void testExtractSwitchVariable() throws Exception {
    doTest();
  }

  public void testReturnVariablesResolved() throws Exception {
    doTest();
  }

  public void testExtractBranchedPrimitive() throws Exception {
    doTest();
  }

  public void testExtractSwitchNotNullVariable() throws Exception {
    doTest();
  }

  public void testAvoidContinueInsideMethod() throws Exception {
    doTest();
  }

  public void testLocalVariablesAreNotExposed() throws Exception {
    doTest();
  }

  public void testExtractConditionalContinue() throws Exception {
    doTest();
  }

  public void testExtractConditionalBreak() throws Exception {
    doTest();
  }

  public void testDontMissReturn() throws Exception {
    doTest();
  }

  public void testNoRedundantContinue() throws Exception {
    doTest();
  }

  public void testDontReplaceThrowWithReturn() throws Exception {
    doTest();
  }

  public void testDontMissReturnDueToThrowable() throws Exception {
    doTest();
  }

  public void testDontExtractInsideSwitch() throws Exception {
    try {
      doTest();
      fail("Should fail inside switch");
    } catch (PrepareFailedException e){
    }
  }

  public void testDontExtractFieldWithConstructor() throws Exception {
    doChainedConstructorTest(false);
  }

  public void testExtractSingleLabelFromSwitch() throws Exception {
    doTest();
  }

  public void testExtractVariableAndReturn() throws Exception {
    doTest();
  }

  public void testExtractVariableAndReturn1() throws Exception {
    try {
      doTest();
      fail("Should not extract different returns");
    } catch (PrepareFailedException e) {
    }
  }

  public void testExtractVariableAndReturn2() throws Exception {
    try {
      doTest();
      fail("Should not extract internal references");
    } catch (PrepareFailedException e) {
    }
  }

  public void testExtractVariableAndReturn3() throws Exception {
    try {
      doTest();
      fail("Should not extract semantically different references");
    } catch (PrepareFailedException e) {
    }
  }

  public void testDontExtractUnfoldableVariable() throws Exception {
    try {
      doTest();
      fail("Should not extract nullable variable if primitive type delcared outside");
    } catch (PrepareFailedException e) {
    }
  }

  public void testExtractConstantExpressions() throws Exception {
      doTest();
  }

  public void testDontExtractLocalConstant() throws Exception {
    try {
      doTest();
      fail("Should fail if expression is linked to the scope");
    } catch (PrepareFailedException e){
    }
  }

  public void testDontExtractCustomFinalObjects() throws Exception {
    try {
      doTest();
      fail("Should fail if expression contains mutable object");
    } catch (PrepareFailedException e){
    }
  }

  public void testWriteArrayAccess() throws Exception {
    doTest();
  }

  public void testShortCircuit() throws Exception {
    doTest();
  }

  public void testRecursiveCallToExtracted() throws Exception {
    doTest();
  }

  public void testCodeDuplicatesVarargsShouldNotChangeReturnType() throws Exception {
    doDuplicatesTest();
  }

  public void testParametersFromAnonymous() throws Exception {
    doTest();
  }

  public void testCast4ParamGeneration() throws Exception {
    doTest();
  }

  public void testNearComment() throws Exception {
    doTest();
  }

  public void testFoldInWhile() throws Exception {
    doTest();
  }

  public void testFoldedParamNameSuggestion() throws Exception {
    doTest();
  }

  public void testNonFoldInIfBody() throws Exception {
    doTest();
  }

  public void testComplexTypeParams() throws Exception {
    doTest();
  }

  public void testExtractWithLeadingComment() throws Exception {
    doTest();
  }

  public void testInvalidReference() throws Exception {
    doTest();
  }

  public void testRedundantCast() throws Exception {
    doTest();
  }

  public void testDisabledParam() throws Exception {
    doTestDisabledParam();
  }

  public void testTypeParamsList() throws Exception {
    doTest();
  }

  public void testTypeParamsListWithRecursiveDependencies() throws Exception {
    doTest();
  }

  public void testFromLambdaBody() throws Exception {
    doTest();
  }

  public void testFromLambdaBody1() throws Exception {
    doTest();
  }

  public void testFromLambdaBodyCapturedWildcardParams() throws Exception {
    doTest();
  }

  public void testFromLambdaBodyToAnonymous() throws Exception {
    doTest();
  }

  public void testFromLambdaBodyToToplevelInsideCodeBlock() throws Exception {
    doTest();
  }

  public void testFromLambdaBodyWithReturn() throws Exception {
    doTest();
  }

  public void testOneLineLambda() throws Exception {
    doTest();
  }

  public void testMethod2Interface() throws Exception {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_8);
  }

  public void testMethod2InterfaceFromStatic() throws Exception {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_8);
  }

  public void testMethod2InterfaceFromConstant() throws Exception {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_8);
  }

  public void testParamDetection() throws Exception {
    doTest();
  }

  public void testSkipComments() throws Exception {
    doTest();
  }

  public void testFinalParams4LocalClasses() throws Exception {
    doTestWithJava17();
  }

  public void testIncompleteExpression() throws Exception {
    doTest();
  }

  public void testTwoFromThreeEqStatements() throws Exception {
    doDuplicatesTest();
  }

  public void testCastWhenDuplicateReplacement() throws Exception {
    doDuplicatesTest();
  }

  public void testCheckQualifierMapping() throws Exception {
    doDuplicatesTest();
  }

  public void testArrayReturnType() throws Exception {
    doDuplicatesTest();
  }

  public void testOverloadedMethods() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureOneParam() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureOneParamMultipleTimesInside() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureLeaveSameExpressionsUntouched() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureSameParamNames() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureCallToSameClassMethod() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureInitialParameterUnused() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureWithFolding() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureWithArrayFolding() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureWithGetterFolding() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureWithMultiFolding() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureWithTwoWayFolding() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureSameSubexpression() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureSameSubexpressionWholeLine() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureTrivialMethod() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignaturePlusOneFolding() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureVoidCallFolding() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureThreeOccurrencesTwoLiteralFolding() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureFourOccurrencesTwoLiteralFolding() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureThreeOccurrencesTwoVariableFolding() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureFourOccurrencesTwoVariableFolding() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureWithOutputVariables() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureEqualConstExprFolding() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureLongConstExprFolding() throws Exception {
    doDuplicatesTest();
  }

  public void testSuggestChangeSignatureTwoFieldInitializers() throws Exception {
    doDuplicatesTest();
  }

  public void testConditionalReturnInDuplicate() throws Exception {
    doDuplicatesTest();
  }

  // todo DuplicatesFinder.canBeEquivalent() should see the difference between 'return' and assignment
  public void _testConditionalReturnVsAssignDuplicate() throws Exception {
    doDuplicatesTest();
  }

  public void testConditionalWithTwoParameters() throws Exception {
    doDuplicatesTest();
  }

  public void testOverlappingDuplicate() throws Exception {
    doDuplicatesTest();
  }

  public void testEffectivelyLocalVariables() throws Exception {
    doDuplicatesTest();
  }

  public void testEffectivelyLocalWithinExpression() throws Exception {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateNestedSubexpression() throws Exception {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateDeclaredOutputVariable() throws Exception {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateDeclaredReusedVariable() throws Exception {
    doDuplicatesTest();
  }

  public void testExactDuplicateDeclaredReusedVariable() throws Exception {
    doDuplicatesTest();
  }

  public void testExactDuplicateTwoDeclaredReusedVariables() throws Exception {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateUnfoldArrayArgument() throws Exception {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateFoldListElement() throws Exception {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateFoldArrayElement() throws Exception {
    doDuplicatesTest();
  }

  public void testParametrizedMultiDuplicatesFoldArrayElement() throws Exception {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateFoldArrayElementTwoUsages() throws Exception {
    doDuplicatesTest();
  }

  public void testTripleParametrizedDuplicate() throws Exception {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateKeepSignature() throws Exception {
    doExactDuplicatesTest();
  }

  public void testRejectParametrizedDuplicate() throws Exception {
    doExactDuplicatesTest();
  }

  public void testParametrizedDuplicateRepeatedArguments() throws Exception {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateTripleRepeatedArguments() throws Exception {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateExactlyRepeatedArguments() throws Exception {
    doDuplicatesTest();
  }

  public void testParametrizedDuplicateExpression() throws Exception {
    doDuplicatesTest();
  }

  public void testPatternVariable() throws Exception {
    doTestWithLanguageLevel(LanguageLevel.HIGHEST);
  }

  public void testPatternVariableIntroduced() throws Exception {
    doExitPointsTest(false);
  }

  public void testPatternVariableIntroduced2() throws Exception {
    doExitPointsTest(false);
  }

  public void testPatternVariableIntroduced3() throws Exception {
    doTestWithLanguageLevel(LanguageLevel.HIGHEST);
  }

  public void testSuggestChangeSignatureWithChangedParameterName() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, null, false, "p");
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testTargetAnonymous() throws Exception {
    doTest();
  }

  public void testSimpleMethodsInOneLine() throws Exception {
    CodeStyleSettings settings = CodeStyle.getSettings(getProject());
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    doTest();
  }

  public void testExtractUnresolvedLambdaParameter() throws Exception {
    try {
      doTest();
      fail("Should not work for single lambda parameter");
    } catch (PrepareFailedException e){

    }
  }

  public void testExtractUnresolvedLambdaExpression() throws Exception {
    doTest();
  }

  public void testNoNPE1() throws Exception {
    doTest();
  }

  public void testNoNPE2() throws Exception {
    doTest();
  }

  public void testTheOnlyParenthesisExpressionWhichIsSkippedInControlFlow() throws Exception {
    doTest();
  }

  public void testExpression() throws Exception {
    doTestWithJava17();
  }

  public void testNonPhysicalSubexpression() throws Exception {
    doTest();
  }

  public void testCopyParamAnnotations() throws Exception {
    doTest();
  }

  public void testInferredNotNullInReturnStatement() throws Exception {
    doTest();
  }

  public void testSkipThrowsDeclaredInLambda() throws Exception {
    doTest();
  }

  public void testChangedReturnType() throws Exception {
    doTestReturnTypeChanged(PsiType.getJavaLangObject(getPsiManager(), GlobalSearchScope.allScope(getProject())));
  }

  //TODO remove or implement
  public void _testMakeVoidMethodReturnVariable() throws Exception {
    doTestReturnTypeChanged(PsiType.INT);
  }

  public void testNoReturnTypesSuggested() throws Exception {
    doTestReturnTypeChanged(PsiType.INT);
  }

  public void testMultipleVarsInMethodNoReturnStatementAndAssignment() throws Exception {
    //return type should not be suggested but still
    doTestReturnTypeChanged(PsiType.INT);
  }

  public void testReassignFinalFieldInside() throws Exception {
    doTestReturnTypeChanged(PsiType.INT);
  }

  public void testShortenClassRefsInNewReturnType() throws Exception {
    doTestReturnTypeChanged(PsiType.getTypeByName(CommonClassNames.JAVA_UTIL_COLLECTION, getProject(), GlobalSearchScope.allScope(getProject())));
  }

  public void testPassFieldAsParameterAndMakeStatic() throws Exception {
    doTestPassFieldsAsParams();
  }

  public void testDefaultNamesConflictResolution() throws Exception {
    final JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.LOCAL_VARIABLE_NAME_PREFIX = "_";
    doTest();
  }

  public void testInferredNotNull() throws Exception {
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

  public void testConditionalExitCombinedWithNullabilityShouldPreserveVarsUsedInExitStatements() throws Exception {
    doTest();
  }

  public void testSingleExitPOintWithTryFinally() throws Exception {
    doTest();
  }

  public void testLocalVariableModifierList() throws Exception {
    doTest();
  }

  public void testLocalVariableAnnotationsOrder() throws Exception {
    doTest();
  }

  public void testDifferentAnnotations() throws Exception {
    doTest();
  }

  public void testSkipCustomAnnotations() throws Exception {
    doTest();
  }

  public void testNullabilityIsTypeAnnotation() throws Exception {
    doTest();
  }

  public void testKeepDeclarationWithAnnotations() throws Exception {
    doTest();
  }

  public void testFilterAnnotations() throws Exception {
    doTest();
  }

  public void testNullabilityAnnotationOverridden() throws Exception {
    doTest();
  }

  public void testSameAnnotations() throws Exception {
    doTest();
  }

  public void testNormalExitIf() throws Exception {
    doTest();
  }

  public void testNormalExitTry() throws Exception {
    doTest();
  }

  public void testMethodAnnotations() throws Exception {
    doTest();
  }

  public void testNotNullArgument0() throws Exception {
    doTest();
  }

  public void testNotNullArgument1() throws Exception {
    doTest();
  }

  public void testNotNullArgument2() throws Exception {
    doTest();
  }

  public void testNotNullArgument3() throws Exception {
    doTest();
  }

  public void testNotNullArgument4() throws Exception {
    doTest();
  }

  public void testNotNullArgument5() throws Exception {
    doTest();
  }

  public void testNotNullArgument6() throws Exception {
    doTest();
  }

  public void testNotNullArgument7() throws Exception {
    doTest();
  }

  public void testNotNullArgumentTooComplexCode() throws Exception {
    RegistryValue value = Registry.get("ide.dfa.state.limit");
    int oldValue = value.asInteger();
    try {
      value.setValue(50);
      doTest();
    }finally {
      value.setValue(oldValue);
    }
  }

  public void testVariableInLoopWithConditionalBreak() throws Exception {
    doTest();
  }

  public void testNotNullArgumentLambdaBare() throws Exception {
    doTest();
  }

  public void testNotNullArgumentLambdaInIf() throws Exception {
    doTest();
  }

  public void testNotNullArgumentLambdaInIfNoBlock() throws Exception {
    doTest();
  }

  public void testNotNullArgumentLambdaInWhileNoBlock() throws Exception {
    doTest();
  }

  public void testNotNullArgumentLambdaInsideBody() throws Exception {
    doTest();
  }

  public void testNotNullArgumentLambdaInIfInsideBody() throws Exception {
    doTest();
  }

  public void testNotNullArgumentAnonymousClassBare() throws Exception {
    doTest();
  }

  public void testNotNullArgumentAnonymousClassInIf() throws Exception {
    doTest();
  }

  public void testNotNullArgumentAnonymousClassInIfNoBlock() throws Exception {
    doTest();
  }

  public void testNotNullArgumentLambdaInForInitializer() throws Exception {
    doTest();
  }

  public void testEmptyParenthesis() throws Exception {
    try {
      doTest();
      fail("Should not work for empty parenthesis");
    }
    catch (PrepareFailedException ignore) {
    }
  }

  public void testNestedReference() throws Exception {
    doTest();
  }

  public void testQualifyWhenConflictingNamePresent() throws Exception {
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
    try {
      configureByFile(BASE_PATH + getTestName(false) + ".java");
      performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, null, true, null, null, null);
      fail("Static modifier is forbidden inside inner classes");
    } catch (PrepareFailedException e){
    }
  }

  public void testStaticForNestedClass() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, null, true, null, null, null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testStaticForOuterClass() throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    final int caret = getEditor().getSelectionModel().getLeadSelectionOffset();
    final PsiClass outerClass = PsiTreeUtil.getParentOfType(getFile().findElementAt(caret), PsiClass.class).getContainingClass();
    performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, null, true, null, outerClass, null);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  public void testDontMakeParametersFinalDueToUsagesInsideAnonymous() throws Exception {
    doTest();
  }

  public void testBuilderChainWithArrayAccess() throws Exception {
    doTest();
  }

  public void testBuilderChainWithArrayAccessExpr() throws Exception {
    doTest();
  }

  public void testBuilderChainWithArrayAccessIf() throws Exception {
    doTest();
  }

  public void testBuilderChainWith2DimArrayAccess() throws Exception {
    doTest();
  }

  public void testCallOnArrayElement() throws Exception {
    doTest();
  }

  public void testCallOn2DimArrayElement() throws Exception {
    doTest();
  }

  public void testCallOnFieldArrayElement() throws Exception {
    doTest();
  }

  public void testExtractedVariableReused() throws Exception {
    doTest();
  }

  public void testExtractBareThenBranch() throws Exception {
    doTest();
  }

  public void testExtractBareElseBranch() throws Exception {
    doTest();
  }

  //TODO fix formatting
  public void _testExtractBareForBody() throws Exception {
    doTest();
  }

  public void testExtractBareDoWhileBody() throws Exception {
    doTest();
  }

  public void testExtractBracedElseBranch() throws Exception {
    doTest();
  }

  public void testExtractBracedDoWhileBody() throws Exception {
    doTest();
  }

  public void testInferredNotNullInReturnStatementDuplicate() throws Exception {
    doDuplicatesTest();
  }

  public void testNullableCheckBreakDuplicate() throws Exception {
    doDuplicatesTest();
  }

  public void testOutputVariableDuplicate() throws Exception {
    doDuplicatesTest();
  }

  public void testNullableCheckVoidDuplicate() throws Exception {
    doTest();
  }

  public void testWriteDifferentFieldsDuplicate() throws Exception {
    doDuplicatesTest();
  }

  public void testDecrementDifferentFieldsDuplicate() throws Exception {
    doDuplicatesTest();
  }

  public void testDecrementDifferentStaticFieldsDuplicate() throws Exception {
    doDuplicatesTest();
  }

  public void testDecrementDifferentOuterFieldsDuplicate() throws Exception {
    doDuplicatesTest();
  }

  public void testDecrementDifferentInnerFieldsDuplicate() throws Exception {
    doDuplicatesTest();
  }

  public void testDecrementDifferentChainedFieldsDuplicate() throws Exception {
    doDuplicatesTest();
  }

  public void testArgumentFoldingWholeStatement() throws Exception {
    doDuplicatesTest();
  }

  public void testArgumentFoldingWholeStatementForUpdate() throws Exception {
    doDuplicatesTest();
  }

  public void testArgumentFoldingWholeStatementForUpdateList() throws Exception {
    doDuplicatesTest();
  }

  public void testArgumentFoldingMethodCall() throws Exception {
    doDuplicatesTest();
  }

  public void testBoxedConditionalReturn() throws Exception {
    doDuplicatesTest();
  }

  public void testAvoidGenericArgumentCast() throws Exception {
    doDuplicatesTest();
  }

  public void testAvoidGenericArgumentCastLocalClass() throws Exception {
    doDuplicatesTest();
  }

  public void testDuplicatePreserveComments() throws Exception {
    doDuplicatesTest();
  }

  public void testDuplicateSubexpression() throws Exception {
    doDuplicatesTest();
  }

  public void testDuplicateSubexpressionWithParentheses() throws Exception {
    doDuplicatesTest();
  }

  public void testOneVariableExpression() throws Exception {
    doDuplicatesTest();
  }

  public void testInterfaceMethodVisibility() throws Exception {
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

  public void testBeforeCommentAfterSelectedFragment() throws Exception {
    doTest();
  }

  public void testInsideCommentAfterSelectedFragment() throws Exception {
    doTest();
  }

  public void testEmptyBlockStatement() throws Exception {
    doExitPointsTest(false);
  }

  public void testCallChainExpression() throws Exception {
    doTest();
  }

  public void testFromDefaultMethodInInterface() throws Exception {
    doTest();
  }

  public void testFromPrivateMethodInInterface() throws Exception {
    doTest();
  }

  public void testFromStaticMethodInInterface() throws Exception {
    doTestWithLanguageLevel(LanguageLevel.JDK_1_8);
  }

  public void testDisjunctionType() throws Exception {
    doTest();
  }

  public void testExtractFromAnnotation() throws Exception {
    try {
      doTest();
      fail("Should not work for annotations");
    }
    catch (PrepareFailedException ignore) {
    }
  }

  public void testExtractFromAnnotation1() throws Exception {
    try {
      doTest();
      fail("Should not work for annotations");
    }
    catch (PrepareFailedException ignore) {
    }
  }

  public void testExtractConditionFromSimpleIf() throws Exception {
    doTest();
  }

  public void testExtractConditionFromSimpleIf1() throws Exception {
    doTest();
  }

  private void doTestDisabledParam() throws PrepareFailedException {
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    boolean success = performExtractMethod(true, true, getEditor(), getFile(), getProject(), false, 0);
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
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

  private void doPrepareErrorTest(final String expectedMessage) throws Exception {
    String expectedError = null;
    try {
      doErrorTest();
    }
    catch(PrepareFailedException ex) {
      expectedError = ex.getMessage();
    }
    assertEquals(expectedMessage, expectedError);
  }

  private void doErrorTest() throws Exception {
    String fileName = getTestName(false) + ".java";
    configureByFile(BASE_PATH + fileName);
    performAction(false,false);
  }

  private void doExitPointsTest(boolean shouldSucceed) throws Exception {
    String fileName = getTestName(false) + ".java";
    configureByFile(BASE_PATH + fileName);
    boolean succeed = false;
    try {
      doErrorTest();
      succeed = true;
    } catch (PrepareFailedException e) {
    }
    assertEquals(shouldSucceed, succeed);
  }

  private void doTest() throws Exception {
    final CommonCodeStyleSettings settings = CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    settings.ELSE_ON_NEW_LINE = true;
    settings.CATCH_ON_NEW_LINE = myCatchOnNewLine;
    doTest(true);
  }

  private void doTest(boolean duplicates) throws Exception {
    doTest(duplicates, null);
  }

  private void doExactDuplicatesTest() throws Exception {
    doTest(true, () -> getFile().putUserData(ExtractMethodProcessor.SIGNATURE_CHANGE_ALLOWED, Boolean.FALSE));
  }

  private void doTest(boolean duplicates, @Nullable Runnable prepare) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");
    if (prepare != null) prepare.run();
    boolean success = performAction(true, duplicates);
    assertTrue(success);
    checkResultByFile(BASE_PATH + getTestName(false) + "_after.java");
  }

  private boolean performAction(boolean doRefactor, boolean replaceAllDuplicates) throws Exception {
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
                                              final boolean extractChainedConstructor) throws PrepareFailedException, IncorrectOperationException {
    return performExtractMethod(doRefactor, replaceAllDuplicates, editor, file, project, extractChainedConstructor,
                                ArrayUtilRt.EMPTY_INT_ARRAY);
  }

  private static boolean performExtractMethod(boolean doRefactor,
                                              boolean replaceAllDuplicates,
                                              @NotNull Editor editor,
                                              @NotNull PsiFile file,
                                              @NotNull Project project,
                                              final boolean extractChainedConstructor,
                                              int @NotNull ... disabledParams) throws PrepareFailedException, IncorrectOperationException {
    return performExtractMethod(doRefactor, replaceAllDuplicates, editor, file, project, extractChainedConstructor, null, false, null, disabledParams);
  }

  private static boolean performExtractMethod(boolean doRefactor,
                                              boolean replaceAllDuplicates,
                                              @NotNull Editor editor,
                                              @NotNull PsiFile file,
                                              @NotNull Project project,
                                              final boolean extractChainedConstructor,
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
                                              final boolean extractChainedConstructor,
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
        for (final Match match : duplicates) {
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
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NonNls;

public class OverloadResolutionTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/overloadResolution";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  public void testPertinentToApplicabilityOfExplicitlyTypedLambda() {
    doTest();
  }

  public void testVoidValueCompatibilityOfImplicitlyTypedLambda() {
    doTest();
  }
  
  public void testVoidValueCompatibilityCachedControlFlow() {
    doTest();
  }

  public void testVoidValueCompatibilityCanCompleteNormallyWithCallWithExceptionAsLastStatement() {
    doTest();
  }

  public void testVoidValueCompatibilityCantCompleteNormallyWithCallWithExceptionAsLastReturnStatement() {
    doTest();
  }

  public void testTryCatchWithoutFinallyBlockProcessing() {
    doTest(false);
  }

  public void testValueCompatibleWithThrowsStatement() {
    doTest(false);
  }

  public void testIDEA102800() {
    doTest();
  }

  public void testReturnStatementsInsideNestedLambdasDuringVoidValueCompatibilityChecks() {
    doTest();
  }

  public void testIgnoreNonFunctionalArgumentsWhenCheckIfFunctionalMoreSpecific() {
    doTest();
  }

  public void testLambdaIsNotCongruentWithFunctionalTypeWithTypeParams() {
    doTest();
  }

  public void testDetectPolyExpressionInReturnsOfExplicitlyTypedLambdaWhenPrimitiveCouldWin() {
    doTest();
  }

  public void testDetectNotEqualParametersInFunctionalTypesForExactMethodReferences() {
    doTest();
  }

  public void testPreferDefaultMethodsOverStatic() {
    doTest();
  }

  public void testDefaultAbstractConflictResolution() {
    doTest();
  }

  public void testLambdaValueCompatibleWithNestedTryWithResources() {
    doTest(false);
  }

  public void testManyOverloadsWithVarargs() {
    PlatformTestUtil.startPerformanceTest("Overload resolution with 14 overloads", 10000, () -> doTest(false)).useLegacyScaling().assertTiming();
  }

  public void testConstructorOverloadsWithDiamonds() {
    PlatformTestUtil.startPerformanceTest("Overload resolution with chain constructor calls with diamonds", 5000, () -> doTest(false)).useLegacyScaling().assertTiming();
  }

  public void testMultipleOverloadsWithNestedGeneric() {
    doTest(false);
  }

  public void testSecondSearchPossibleForFunctionalInterfacesWithPrimitiveFisrtParameter() {
    doTest(false);
  }

  public void testIDEA139875() {
    doTest();
  }

  public void testMethodReferenceWithTypeArgs() {
    doTest();
  }

  public void testPrimitiveVarargsAreNoMoreSpecificThanNonPrimitiveWhenNoArgIsActuallyProvided() {
    doTest();
  }

  public void testProperUnrelatedFunctionalInterfacesTypesComparison() {
    doTest();
  }

  public void testDoNotCheckConstantIfsDuringValueCompatibleChecks() {
    doTest();
  }

  public void testFunctionalExpressionTypeErasure() {
    doTest();
  }

  public void testOverrideObjectMethods() {
    doTest();
  }

  public void testStaticImportOfObjectsToString() {
    doTest();
  }

  public void testConflictsWithRawQualifier() {
    doTest();
  }

  public void testIgnoreCandidatesWithLowerApplicabilityLevel() {
    doTest();
  }

  public void testSiteSubstituteTypeParameterBoundsWhenCheckForMostSpecific() {
    doTest();
  }

  public void testChooseAbstractMethodArbitrarily() {
    doTest();
  }

  public void testFunctionalInterfaceIncompatibilityBasedOnAbsenceOfVoidToTypeConvertion() {
    doTest();
  }

  public void testNoBoxingWithNullType() {
    doTest();
  }

  public void testFunctionalInterfacesAtVarargsPositionMostSpecificCheck() {
    doTest();
  }

  public void testIgnoreNumberOfParametersInPotentiallyCompatibleCheckNotToExcludeAllConflicts() {
    doTest(false);
  }

  public void testPotentialCompatibilityInCaseWhenNoMethodHasValidNumberOfParameters() {
    doTest(false);
  }

  public void testNoNeedToPreferGenericToRawSubstitution() {
    doTest();
  }

  public void testLongerParamsWhenVarargs() {
    doTest();
  }

  public void testPotentiallyCompatibleShouldCheckAgainstSubstitutedWithSiteSubstitutor() {
    doTest(false);
  }

  public void testCompareFormalParametersWithNotionOfSiteSubstitutorInIsMoreSpecificCheck() {
    doTest(true);
  }

  public void testDonotIncludeAdditionalConstraintsDuringApplicabilityChecksInsideOverloadResolution() {
    doTest(true);
  }

  public void testPreserveErrorsFromOuterVariables() {
    doTest(true);
  }

  public void testIDEA151823() {
    doTest();
  }

  public void testTypeCalculationOfQualifierShouldNotDependOnOverloadResolutionOfContainingMethodCall() {
    doTest();
  }

  public void testIDEA153076() {
    doTest();
  }

  //java 8 error
  public void testNotPotentiallyCompatibleMethodReference() {
    doTest();
  }

  public void testSpecificFunctionalInterfaces() {
    doTest();
  }

  public void testIgnoreStaticCorrectnessDuringOverloadResolution() {
    doTest(false);
  }

  public void testIgnoreLambdaVoidValueIncompatibilitiesPreferringMethodWithFunctionalTypeToNonFunctionalType() {
    doTest(false);
  }

  public void testVarargComponentTypesShouldBeExcludedFromBoxingComparison() {
    doTest(false);
  }

  public void testOverriddenVarargWithaArray() {
    doTest();
  }

  public void testForceCleanupErrorsInConditionalWhenBothBranchesProduceError() {
    doTest();
  }

  public void testStaticMethodInOuterClassConflictWithToString() {
    doTest();
  }

  public void testPreserveStaticMethodConflictsWhenMethodsAreNotHidden() {
    doTest(false);
  }

  public void testDontSkipInapplicableMethodsDuringSameSignatureCheck() {
    doTest(false);
  }

  public void testInferenceErrorInArgumentWhenWrongOverloadWasChosen() {
    doTest(false);
  }

  public void testAdaptReturnTypesOfSiblingMethods() { doTest(false);}

  public void testOverriddenMethodWithOtherRawSignature() { doTest(false);}

  public void testMoreSpecificForRawSignatureOnStaticProblem() { doTest(false);}
  public void testSkipStaticInterfaceMethodCalledOnInheritorForMethodRefConflictResolving() { doTest(false);}

  public void testUnqualifiedStaticInterfaceMethodCallsOnInnerClasses() { doTest(false);}

  public void testStaticMethodInSuperInterfaceConflictWithCurrentStatic() { doTest(false);}
  public void testPreferMethodInsideSameComb() { doTest(false);}
  public void testFixedContainingClassTypeArguments() { doTest(false);}
  public void testPotentialCompatibilityWithArrayCreation() { doTest(false);}
  public void testOverloadsWithOneNonCompatible() { doTest(false);}
  public void testOverloadedConstructors() { doTest(false);}
  public void testIncompleteLambdasWithDifferentSignatures() { doTest(false);}
  public void testTwoFunctionalInterfacesWithVarargs() { doTest(false);}
  public void testVarargsAndBareInferenceVariable() { 
    doTest(false);
  
    PsiMethodCallExpression getReference =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiMethodCallExpression.class);
    assertNotNull(getReference);
    PsiType type = getReference.getType();
    assertTrue(type instanceof PsiArrayType);
    assertFalse(type instanceof PsiEllipsisType);
  }
  public void testSecondSearchOverloadsBoxing() {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    String filePath = BASE_PATH + "/" + getTestName(false) + ".java";
    configureByFile(filePath);
    PsiReference reference = getFile().findReferenceAt(getEditor().getCaretModel().getOffset());
    assertNotNull(reference);
    PsiElement resolve = reference.resolve();
    assertInstanceOf(resolve, PsiMethod.class);
    assertEquals("java.lang.StringBuilder append(int i)", 
                 PsiFormatUtil.formatMethod((PsiMethod)resolve, PsiSubstitutor.EMPTY, 
                                            PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES | PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                            PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_FQ_NAME));
    
  }

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean warnings) {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", warnings, false);
  }
}

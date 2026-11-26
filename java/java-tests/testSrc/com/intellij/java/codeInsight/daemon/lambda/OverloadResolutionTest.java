// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class OverloadResolutionTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/overloadResolution";

  @BeforeEach
  void setUp() {
    daemonFixture.get().getFixture().enableInspections(new UnusedDeclarationInspection());
  }

  @Test
  public void testPertinentToApplicabilityOfExplicitlyTypedLambda() {
    doTest();
  }

  @Test
  public void testVoidValueCompatibilityOfImplicitlyTypedLambda() {
    doTest();
  }
  
  @Test
  public void testVoidValueCompatibilityCachedControlFlow() {
    doTest();
  }

  @Test
  public void testVoidValueCompatibilityCanCompleteNormallyWithCallWithExceptionAsLastStatement() {
    doTest();
  }

  @Test
  public void testVoidValueCompatibilityCantCompleteNormallyWithCallWithExceptionAsLastReturnStatement() {
    doTest();
  }

  @Test
  public void testTryCatchWithoutFinallyBlockProcessing() {
    doTest(false);
  }

  @Test
  public void testValueCompatibleWithThrowsStatement() {
    doTest(false);
  }

  @Test
  public void testIDEA102800() {
    doTest();
  }

  @Test
  public void testReturnStatementsInsideNestedLambdasDuringVoidValueCompatibilityChecks() {
    doTest();
  }

  @Test
  public void testIgnoreNonFunctionalArgumentsWhenCheckIfFunctionalMoreSpecific() {
    doTest();
  }

  @Test
  public void testLambdaIsNotCongruentWithFunctionalTypeWithTypeParams() {
    doTest();
  }

  @Test
  public void testDetectPolyExpressionInReturnsOfExplicitlyTypedLambdaWhenPrimitiveCouldWin() {
    doTest();
  }

  @Test
  public void testDetectNotEqualParametersInFunctionalTypesForExactMethodReferences() {
    doTest();
  }

  @Test
  public void testPreferDefaultMethodsOverStatic() {
    doTest();
  }

  @Test
  public void testDefaultAbstractConflictResolution() {
    doTest();
  }

  @Test
  public void testLambdaValueCompatibleWithNestedTryWithResources() {
    doTest(false);
  }

  @Test
  public void testMultipleOverloadsWithNestedGeneric() {
    doTest(false);
  }

  @Test
  public void testSecondSearchPossibleForFunctionalInterfacesWithPrimitiveFisrtParameter() {
    doTest(false);
  }

  @Test
  public void testIDEA139875() {
    doTest();
  }

  @Test
  public void testMethodReferenceWithTypeArgs() {
    doTest();
  }

  @Test
  public void testPrimitiveVarargsAreNoMoreSpecificThanNonPrimitiveWhenNoArgIsActuallyProvided() {
    doTest();
  }

  @Test
  public void testProperUnrelatedFunctionalInterfacesTypesComparison() {
    doTest();
  }

  @Test
  public void testDoNotCheckConstantIfsDuringValueCompatibleChecks() {
    doTest();
  }

  @Test
  public void testFunctionalExpressionTypeErasure() {
    doTest();
  }

  @Test
  public void testOverrideObjectMethods() {
    doTest();
  }

  @Test
  public void testStaticImportOfObjectsToString() {
    doTest();
  }

  @Test
  public void testConflictsWithRawQualifier() {
    doTest();
  }

  @Test
  public void testIgnoreCandidatesWithLowerApplicabilityLevel() {
    doTest();
  }

  @Test
  public void testSiteSubstituteTypeParameterBoundsWhenCheckForMostSpecific() {
    doTest();
  }

  @Test
  public void testChooseAbstractMethodArbitrarily() {
    doTest();
  }

  @Test
  public void testFunctionalInterfaceIncompatibilityBasedOnAbsenceOfVoidToTypeConvertion() {
    doTest();
  }

  @Test
  public void testNoBoxingWithNullType() {
    doTest();
  }

  @Test
  public void testFunctionalInterfacesAtVarargsPositionMostSpecificCheck() {
    doTest();
  }

  @Test
  public void testIgnoreNumberOfParametersInPotentiallyCompatibleCheckNotToExcludeAllConflicts() {
    doTest(false);
  }

  @Test
  public void testPotentialCompatibilityInCaseWhenNoMethodHasValidNumberOfParameters() {
    doTest(false);
  }

  @Test
  public void testNoNeedToPreferGenericToRawSubstitution() {
    doTest();
  }

  @Test
  public void testLongerParamsWhenVarargs() {
    doTest();
  }

  @Test
  public void testPotentiallyCompatibleShouldCheckAgainstSubstitutedWithSiteSubstitutor() {
    doTest(false);
  }

  @Test
  public void testCompareFormalParametersWithNotionOfSiteSubstitutorInIsMoreSpecificCheck() {
    doTest(true);
  }

  @Test
  public void testDonotIncludeAdditionalConstraintsDuringApplicabilityChecksInsideOverloadResolution() {
    doTest(true);
  }

  @Test
  public void testPreserveErrorsFromOuterVariables() {
    doTest(true);
  }

  @Test
  public void testIDEA151823() {
    doTest();
  }

  @Test
  public void testTypeCalculationOfQualifierShouldNotDependOnOverloadResolutionOfContainingMethodCall() {
    doTest();
  }

  @Test
  public void testIDEA153076() {
    doTest();
  }

  //java 8 error
  @Test
  public void testNotPotentiallyCompatibleMethodReference() {
    doTest();
  }

  @Test
  public void testSpecificFunctionalInterfaces() {
    doTest();
  }

  @Test
  public void testIgnoreStaticCorrectnessDuringOverloadResolution() {
    doTest(false);
  }

  @Test
  public void testIgnoreLambdaVoidValueIncompatibilitiesPreferringMethodWithFunctionalTypeToNonFunctionalType() {
    doTest(false);
  }

  @Test
  public void testVarargComponentTypesShouldBeExcludedFromBoxingComparison() {
    doTest(false);
  }

  @Test
  public void testOverriddenVarargWithaArray() {
    doTest();
  }

  @Test
  public void testForceCleanupErrorsInConditionalWhenBothBranchesProduceError() {
    doTest();
  }

  @Test
  public void testStaticMethodInOuterClassConflictWithToString() {
    doTest();
  }

  @Test
  public void testPreserveStaticMethodConflictsWhenMethodsAreNotHidden() {
    doTest(false);
  }

  @Test
  public void testDontSkipInapplicableMethodsDuringSameSignatureCheck() {
    doTest(false);
  }

  @Test
  public void testInferenceErrorInArgumentWhenWrongOverloadWasChosen() {
    doTest(false);
  }

  @Test public void testAdaptReturnTypesOfSiblingMethods() { doTest(false);}

  @Test public void testOverriddenMethodWithOtherRawSignature() { doTest(false);}

  @Test public void testMoreSpecificForRawSignatureOnStaticProblem() { doTest(false);}
  @Test public void testSkipStaticInterfaceMethodCalledOnInheritorForMethodRefConflictResolving() { doTest(false);}

  @Test public void testUnqualifiedStaticInterfaceMethodCallsOnInnerClasses() { doTest(false);}

  @Test public void testStaticMethodInSuperInterfaceConflictWithCurrentStatic() { doTest(false);}
  @Test public void testPreferMethodInsideSameComb() { doTest(false);}
  @Test public void testFixedContainingClassTypeArguments() { doTest(false);}
  @Test public void testPotentialCompatibilityWithArrayCreation() { doTest(false);}
  @Test public void testOverloadsWithOneNonCompatible() { doTest(false);}
  @Test public void testOverloadedConstructors() { doTest(false);}
  @Test public void testIncompleteLambdasWithDifferentSignatures() { doTest(false);}
  @Test public void testTwoFunctionalInterfacesWithVarargs() { doTest(false);}
  @Test
  public void testVarargsAndBareInferenceVariable() { 
    doTest(false);
  
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    ReadAction.runBlocking(() -> {
      PsiMethodCallExpression getReference =
        PsiTreeUtil.getParentOfType(myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset()),
                                    PsiMethodCallExpression.class);
      assertNotNull(getReference);
      PsiType type = getReference.getType();
      assertInstanceOf(PsiArrayType.class, type);
      assertFalse(type instanceof PsiEllipsisType);
    });
  }
  @Test
  public void testSecondSearchOverloadsBoxing() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, myFixture.getModule(), myFixture.getTestRootDisposable());
    String filePath = BASE_PATH + "/" + myFixture.getTestName(false) + ".java";
    daemonFixture.get().configureByFile(filePath);
    ReadAction.runBlocking(() -> {
      PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertNotNull(reference);
      PsiElement resolve = reference.resolve();
      assertInstanceOf(PsiMethod.class, resolve);
      assertEquals("java.lang.StringBuilder append(int i)", 
                   PsiFormatUtil.formatMethod((PsiMethod)resolve, PsiSubstitutor.EMPTY, 
                                              PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_FQ_CLASS_NAMES | PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                                              PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_FQ_NAME));
    });
    
  }

  @Test
  public void testStaticAndDefaultMethodInInterfaces() {
    doTest();
  }
  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean warnings) {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, myFixture.getModule(), myFixture.getTestRootDisposable());
    daemonFixture.get().doTest(BASE_PATH + "/" + myFixture.getTestName(false) + ".java", warnings, false);
  }
}

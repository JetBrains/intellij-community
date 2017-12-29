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
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

public class NewMethodRefHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/newMethodRef/";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new UncheckedWarningLocalInspection()
    };
  }

  public void testIDEA93587() { doTest(true); }

  public void testIDEA106522() { doTest(); }
  public void testIDEA112574() { doTest(); }
  public void testIDEA113558() { doTest(true); }
  public void testAfterDistinctOps() { doTest(true); }
  public void testUncheckedWarningWithRawSecondSearchAndMethodFromSuper() { doTest(true); }
  public void testWildcardReturns() { doTest(false); }
  public void testInexactMethodReferencePrimitiveBound() { doTest(false); }
  public void testAfterCollectors1() { doTest(false); }
  public void testAfterCollectors2() { doTest(false); }
  public void testIDEA116359() { doTest(false); }
  public void testAfterSliceOps() { doTest(false); }
  public void testAssertNumberOfParameters() { doTest(false); }
  public void testGenericArrayCreation() { doTest(true); }
  public void testWildcardsInClassTypeQualifier() { doTest(); }
  public void testRawConstructorReferenceWithTypeParams() { doTest(); }
  public void testCheckReturnTypeForExactRefs() { doTest(); }
  public void testPotentialApplicability() { doTest(); }
  public void testLiftMethodReferenceTypeParams() { doTest(); }
  public void testHighlightReferenceWhenContradictBoundsAreInferred() { doTest(); }
  public void testAfterBounds() { doTest(); }
  public void testIncludeContainingClassParamsInResolveSetForConstructorRefs() { doTest(); }
  public void testContainingClassTypeParamsForBounds() { doTest(); }
  public void testCapturingReturnValue() { doTest(); }
  public void testIDEA117311() { doTest(); }
  public void testDeepWildcardEliminating() { doTest(); }
  //public void testLambdaExercise() { doTest(); }
  public void testIDEA118437() { doTest(); }
  public void testIDEA113297() { doTest(); }
  public void testIDEA120376() { doTest(); }
  public void testIDEA120370() { doTest(); }
  public void testExcludeContainingClassTypeParamsFromInference() { doTest(); }
  public void testEnsureTypeParametersAreNotModifiedDuringGroundTypeEvaluation() { doTest(); }
  public void testIncorrectArrayCreationSignature() { doTest(); }
  public void testRawTargetType() { doTest(); }
  public void testReturnTypeCheckForRawReceiver() { doTest(); }
  public void testStaticNonStaticReferenceTypeAmbiguity() { doTest(); }
  public void testSuperClassPotentiallyApplicableMembers() { doTest(); }
  public void testExactMethodReferencePertinentToApplicabilityCheck() { doTest(); }
  public void testAmbiguityVarargs() { doTest(); }
  public void testRawInnerClassQualifier() { doTest(); }
  public void testIDEA122100() { doTest(); }
  public void testIDEA122509() { doTest(); }
  public void testIDEA122681() { doTest(); }
  public void testIDEA112191() { doTest(); }
  public void testIDEA122018comment() { doTest(); }
  public void testIDEA123223() { doTest(); }
  public void testIDEA123248() { doTest(); }
  public void testIDEA123366() { doTest(); }
  public void testIDEA123366comment() { doTest(); }
  public void testFromReferenceWithTypeArgs() { doTest(); }
  public void testRefOnStaticInterfaceMethod() { doTest(); }
  public void testUncheckedMethodReference() { doTest(true); }
  public void testIDEA124148() { doTest(); }
  public void testIDEA124613() { doTest(); }
  public void testCollectingApplicabilityConstraints() { doTest(); }
  public void testIDEA126062() { doTest(); }
  public void testRejectReceiverTypesForConstructorRefs() { doTest(); }
  public void testEnumValuesMethod() { doTest(); }
  public void testMissedApplicableMemberContainingClassSubstitution() { doTest(); }
  public void testIDEA126969() { doTest(); }
  public void testIDEA127506() { doTest(); }
  public void testIDEA127275() { doTest(); }
  public void testIDEA127275_() { doTest(); }
  public void testUnresolvedMethodReference() { doTest(); }
  public void testIDEA128534() { doTest(); }
  public void testIDEA128712() { doTest(); }
  public void testAdditionalConstraints3Level() { doTest(); }
  public void testWildcardParametrization() { doTest(); }
  public void testReceiverTypeSubstitution() { doTest(); }
  public void testTypeParametersInitOrder() { doTest(); }
  public void testIDEA132560() { doTest(); }
  public void testEnsureThatCapturedWildcardsAreNotOpenWithoutAnyReason() { doTest(); }
  public void testVarargsParametersCountComparison() { doTest(true); }
  public void testPotentialCompatibilityWithInvalidShape() { doTest(true); }
  public void testSiteSubstitutionOfNonReceiverReference() { doTest(); }
  public void testRawReferenceTypeWithReceiver() { doTest(); }
  public void testMethodReferenceTypeArgumentsApplicability() { doTest(); }
  public void testTypeNameInterfaceSuperMethodReferenceApplicability() { doTest(); }
  public void testNewParameterizedReferenceOnRawType() { doTest(); }
  public void testArrayTypeNewReifiable() { doTest(); }
  public void testReturnTypeApplicabilityIfCapturedWildcardInferred() { doTest(); }
  public void testIDEA133935() { doTest(); }
  public void testIDEA132379() { doTest(); }
  public void testIDEA136581() { doTest(); }
  public void testSuperSubstitutorInNormalCase() { doTest(false); }
  public void testSecondSearchIfFirstParameterIsASubtypeOfReferenceTypeFromExpressionDeclaration() { doTest(); }
  public void testEnsureNotResolvedMethodRefsAreNotMarkedAsExact() { doTest(); }
  public void testReceiverSubstitutorForExactReferenceInMethodReferenceConstraint() { doTest(); }
  public void testSkipCheckedExceptionsHandledByInterfaceMethod() { doTest(); }
  public void testConstraintsFromNonRawReceiverType() { doTest(); }
  public void testSubstitutionForReturnTypeConstraintsForTargetGenericMethod() { doTest(); }
  public void testIDEA140586() { doTest(); }
  public void testUnhandledExceptionsInQualifier() { doTest(); }
  public void testRawClassTypeOnConstructorReference() { doTest(); }
  public void testIncompleteCodeWithMethodReferenceOverLambdaParameter() { doTest(); }
  public void testIncompleteMethodReferenceWithUncheckedWarningInspection() { doTest(); }
  public void testCapturedReturnTypeOfMethodReference() { doTest(); }
  public void testEnumConstantsAsContext() { doTest(); }
  public void testIDEA138752() { doTest(); }
  public void testIDEA136708() { doTest(); }
  public void testContainingClassTypeParametersShouldNotBeInferredDuringMethodReferenceProcessing() { doTest(); }
  public void testIDEA147511() { doTest(); }
  public void testRawInferredTypeCheck() { doTest(); }
  public void testIDEA147873() { doTest(); }
  public void testIDEA148553() { doTest(); }
  public void testIDEA148093() { doTest(); }
  public void testIDEA148841() { doTest(); }
  public void testCapturedTypesOfImplicitParameterTypes() { doTest(); }
  public void testApplicabilityConflictMessage() { doTest(); }
  public void testQualifierOfCapturedWildcardType() { doTest(); }
  public void testCapturedWildcardInReceiverPosition() { doTest(); }
  public void testGetClassReturnTypeInMethodReference() { doTest(); }
  public void testCaptureTypeOfNewArrayExpression() { doTest(); }
  public void testIDEA152659() { doTest(); }
  //removing one unsound capture conversion is not enough to leave the system consistent
  public void _testRegistryOptionToSkipUnsoundCaptureConversionInMethodReferenceReturnType() { doTest(); }
  public void testFreshVariableLowerBoundsDuringSuperTypeChecks() { doTest(); }
  public void testTypeParameterInstantiation() { doTest(); }
  public void testIgnoreForeignVariables() { doTest(); }
  public void testChainedCallsWithMethodReferenceInside() { doTest(); }
  public void testApplicabilityErrorVisibleWhenConstraintsFromFunctionalExpressionsProvideValidSubstitutor() { doTest(); }
  public void testMethodReferenceSecondSearchDontInfluenceTopSiteSubstitutor() { doTest(); }
  public void testCheckReturnTypeOfMethodReferenceWhenTheRestIsGood() { doTest(); }
  public void testEnsureResolveToClassInConstructorRefs() { doTest(); }
  public void testReturnTypeCompatibilityConstraintForSecondSearchCase() { doTest(); }
  public void testMethodInInheritorFoundBySecondSearch() { doTest(); }
  public void testNonExactMethodReferenceOnRawClassType() { doTest(); }
  public void testIncludeOnlyTypeParametersUsedInParameterTypesExcludeThoseUsedInReturnOnly() { doTest(); }
  public void testMethodREfToContainingMethodWithGenericParam() { doTest(); }
  public void testRawClassTypeOnConstructorWithVarargs() { doTest(); }
  public void testIDEA175280() { doTest(); }
  public void testDistinguishCapturedWildcardsByDifferentParameters() { doTest(); }
  public void testConstructorRefOnClassWithRecursiveTypeParameter() { doTest(); }
  public void testWildcardInCheckedCompatibilityConstraints() { doTest(); }
  public void testConstructorReferenceWithVarargsParameters() { doTest(); }
  public void testMethodReferenceSwallowedErrors() { doTest(); }
  public void testConflictingVarargsFromFirstSearchWithNArityOfTheSecondSearch() { doTest(); }
  public void testSkipInferenceForInapplicableMethodReference() { doTest(); }

  public void testPreferErrorOnTopLevelToFailedSubstitutorOnNestedLevel() { doTest(); }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean warnings) {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + getTestName(false) + ".java", warnings, false);
  }
}
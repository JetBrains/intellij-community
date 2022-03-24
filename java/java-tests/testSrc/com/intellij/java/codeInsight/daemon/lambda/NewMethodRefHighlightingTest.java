// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NewMethodRefHighlightingTest extends LightJavaCodeInsightFixtureTestCase5 {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/newMethodRef/";

  @NotNull
  @Override
  protected String getRelativePath() {
    return super.getRelativePath() + BASE_PATH;
  }

  @BeforeEach
  void setUp() {
    getFixture().enableInspections(new UnusedDeclarationInspection(), new UncheckedWarningLocalInspection());
  }

  @Test void testIDEA93587() { doTest(true); }
  @Test void testIDEA106522() { doTest(); }
  @Test void testIDEA112574() { doTest(); }
  @Test void testIDEA113558() { doTest(true); }
  @Test void testAfterDistinctOps() { doTest(true); }
  @Test void testUncheckedWarningWithRawSecondSearchAndMethodFromSuper() { doTest(true); }
  @Test void testWildcardReturns() { doTest(false); }
  @Test void testInexactMethodReferencePrimitiveBound() { doTest(false); }
  @Test void testAfterCollectors1() { doTest(false); }
  @Test void testAfterCollectors2() { doTest(false); }
  @Test void testIDEA116359() { doTest(false); }
  @Test void testAfterSliceOps() { doTest(false); }
  @Test void testAssertNumberOfParameters() { doTest(false); }
  @Test void testGenericArrayCreation() { doTest(true); }
  @Test void testWildcardsInClassTypeQualifier() { doTest(); }
  @Test void testRawConstructorReferenceWithTypeParams() { doTest(); }
  @Test void testCheckReturnTypeForExactRefs() { doTest(); }
  @Test void testPotentialApplicability() { doTest(); }
  @Test void testLiftMethodReferenceTypeParams() { doTest(); }
  @Test void testHighlightReferenceWhenContradictBoundsAreInferred() { doTest(); }
  @Test void testAfterBounds() { doTest(); }
  @Test void testIncludeContainingClassParamsInResolveSetForConstructorRefs() { doTest(); }
  @Test void testContainingClassTypeParamsForBounds() { doTest(); }
  @Test void testCapturingReturnValue() { doTest(); }
  @Test void testIDEA117311() { doTest(); }
  @Test void testDeepWildcardEliminating() { doTest(); }
  //public void testLambdaExercise() { doTest(); }
  @Test void testIDEA118437() { doTest(); }
  @Test void testIDEA113297() { doTest(); }
  @Test void testIDEA120376() { doTest(); }
  @Test void testIDEA120370() { doTest(); }
  @Test void testExcludeContainingClassTypeParamsFromInference() { doTest(); }
  @Test void testEnsureTypeParametersAreNotModifiedDuringGroundTypeEvaluation() { doTest(); }
  @Test void testIncorrectArrayCreationSignature() { doTest(); }
  @Test void testRawTargetType() { doTest(); }
  @Test void testReturnTypeCheckForRawReceiver() { doTest(); }
  @Test void testStaticNonStaticReferenceTypeAmbiguity() { 
    doTest();
    getFixture().doHighlighting()
      .stream()
      .filter(info -> info.type == HighlightInfoType.ERROR)
      .forEach(info -> Assert.assertEquals("<html>Reference to 'm' is ambiguous, both 'm(Test, String)' and 'm(String)' match</html>",
                                           info.getToolTip()));
  }
  @Test void testStaticWithVarargsNonStaticReferenceTypeAmbiguity() { doTest(); }
  @Test void testStaticNonStaticWithVarargsReferenceTypeAmbiguity() { doTest(); }
  @Test void testSuperClassPotentiallyApplicableMembers() { doTest(); }
  @Test void testExactMethodReferencePertinentToApplicabilityCheck() { doTest(); }
  @Test void testAmbiguityVarargs() { doTest(); }
  @Test void testRawInnerClassQualifier() { doTest(); }
  @Test void testIDEA122100() { doTest(); }
  @Test void testIDEA122509() { doTest(); }
  @Test void testIDEA122681() { doTest(); }
  @Test void testIDEA112191() { doTest(); }
  @Test void testIDEA122018comment() { doTest(); }
  @Test void testIDEA123223() { doTest(); }
  @Test void testIDEA123248() { doTest(); }
  @Test void testIDEA123366() { doTest(); }
  @Test void testIDEA123366comment() { doTest(); }
  @Test void testFromReferenceWithTypeArgs() { doTest(); }
  @Test void testRefOnStaticInterfaceMethod() { doTest(); }
  @Test void testUncheckedMethodReference() { doTest(true); }
  @Test void testIDEA124148() { doTest(); }
  @Test void testIDEA124613() { doTest(); }
  @Test void testCollectingApplicabilityConstraints() { doTest(); }
  @Test void testIDEA126062() { doTest(); }
  @Test void testRejectReceiverTypesForConstructorRefs() { doTest(); }
  @Test void testEnumValuesMethod() { doTest(); }
  @Test void testMissedApplicableMemberContainingClassSubstitution() { doTest(); }
  @Test void testIDEA126969() { doTest(); }
  @Test void testIDEA127506() { doTest(); }
  @Test void testIDEA127275() { doTest(); }
  @Test void testIDEA127275_() { doTest(); }
  @Test void testUnresolvedMethodReference() { doTest(); }
  @Test void testIDEA128534() { doTest(); }
  @Test void testIDEA128712() { doTest(); }
  @Test void testAdditionalConstraints3Level() { doTest(); }
  @Test void testWildcardParametrization() { doTest(); }
  @Test void testReceiverTypeSubstitution() { doTest(); }
  @Test void testTypeParametersInitOrder() { doTest(); }
  @Test void testIDEA132560() { doTest(); }
  @Test void testEnsureThatCapturedWildcardsAreNotOpenWithoutAnyReason() { doTest(); }
  @Test void testVarargsParametersCountComparison() { doTest(true); }
  @Test void testPotentialCompatibilityWithInvalidShape() { doTest(true); }
  @Test void testSiteSubstitutionOfNonReceiverReference() { doTest(); }
  @Test void testRawReferenceTypeWithReceiver() { doTest(); }
  @Test void testMethodReferenceTypeArgumentsApplicability() { doTest(); }
  @Test void testTypeNameInterfaceSuperMethodReferenceApplicability() { doTest(); }
  @Test void testNewParameterizedReferenceOnRawType() { doTest(); }
  @Test void testArrayTypeNewReifiable() { doTest(); }
  @Test void testReturnTypeApplicabilityIfCapturedWildcardInferred() { doTest(); }
  @Test void testIDEA133935() { doTest(); }
  @Test void testIDEA132379() { doTest(); }
  @Test void testIDEA136581() { doTest(); }
  @Test void testSuperSubstitutorInNormalCase() { doTest(false); }
  @Test void testSecondSearchIfFirstParameterIsASubtypeOfReferenceTypeFromExpressionDeclaration() { doTest(); }
  @Test void testEnsureNotResolvedMethodRefsAreNotMarkedAsExact() { doTest(); }
  @Test void testReceiverSubstitutorForExactReferenceInMethodReferenceConstraint() { doTest(); }
  @Test void testSkipCheckedExceptionsHandledByInterfaceMethod() { doTest(); }
  @Test void testConstraintsFromNonRawReceiverType() { doTest(); }
  @Test void testSubstitutionForReturnTypeConstraintsForTargetGenericMethod() { doTest(); }
  @Test void testIDEA140586() { doTest(); }
  @Test void testUnhandledExceptionsInQualifier() { doTest(); }
  @Test void testRawClassTypeOnConstructorReference() { doTest(); }
  @Test void testIncompleteCodeWithMethodReferenceOverLambdaParameter() { doTest(); }
  @Test void testIncompleteMethodReferenceWithUncheckedWarningInspection() { doTest(); }
  @Test void testCapturedReturnTypeOfMethodReference() { doTest(); }
  @Test void testEnumConstantsAsContext() { doTest(); }
  @Test void testIDEA138752() { doTest(); }
  @Test void testIDEA136708() { doTest(); }
  @Test void testContainingClassTypeParametersShouldNotBeInferredDuringMethodReferenceProcessing() { doTest(); }
  @Test void testIDEA147511() { doTest(); }
  @Test void testRawInferredTypeCheck() { doTest(); }
  @Test void testIDEA147873() { doTest(); }
  @Test void testIDEA148553() { doTest(); }
  @Test void testIDEA148093() { doTest(); }
  @Test void testIDEA148841() { doTest(); }
  @Test void testCapturedTypesOfImplicitParameterTypes() { doTest(); }
  @Test void testApplicabilityConflictMessage() { doTest(); }
  @Test void testQualifierOfCapturedWildcardType() { doTest(); }
  @Test void testCapturedWildcardInReceiverPosition() { doTest(); }
  @Test void testGetClassReturnTypeInMethodReference() { doTest(); }
  @Test void testCaptureTypeOfNewArrayExpression() { doTest(); }
  @Test void testIDEA152659() { doTest(); }
  //removing one unsound capture conversion is not enough to leave the system consistent
  void _testRegistryOptionToSkipUnsoundCaptureConversionInMethodReferenceReturnType() { doTest(); }
  @Test void testFreshVariableLowerBoundsDuringSuperTypeChecks() { doTest(); }
  @Test void testTypeParameterInstantiation() { doTest(); }
  @Test void testIgnoreForeignVariables() { doTest(); }
  @Test void testChainedCallsWithMethodReferenceInside() { doTest(); }
  @Test void testApplicabilityErrorVisibleWhenConstraintsFromFunctionalExpressionsProvideValidSubstitutor() { doTest(); }
  @Test void testMethodReferenceSecondSearchDontInfluenceTopSiteSubstitutor() { doTest(); }
  @Test void testCheckReturnTypeOfMethodReferenceWhenTheRestIsGood() { doTest(); }
  @Test void testEnsureResolveToClassInConstructorRefs() { doTest(); }
  @Test void testReturnTypeCompatibilityConstraintForSecondSearchCase() { doTest(); }
  @Test void testMethodInInheritorFoundBySecondSearch() { doTest(); }
  @Test void testNonExactMethodReferenceOnRawClassType() { doTest(); }
  @Test void testIncludeOnlyTypeParametersUsedInParameterTypesExcludeThoseUsedInReturnOnly() { doTest(); }
  @Test void testMethodREfToContainingMethodWithGenericParam() { doTest(); }
  @Test void testRawClassTypeOnConstructorWithVarargs() { doTest(); }
  @Test void testIDEA175280() { doTest(); }
  @Test void testDistinguishCapturedWildcardsByDifferentParameters() { doTest(); }
  @Test void testConstructorRefOnClassWithRecursiveTypeParameter() { doTest(); }
  @Test void testWildcardInCheckedCompatibilityConstraints() { doTest(); }
  @Test void testConstructorReferenceWithVarargsParameters() { doTest(); }
  @Test void testMethodReferenceSwallowedErrors() { doTest(); }
  @Test void testConflictingVarargsFromFirstSearchWithNArityOfTheSecondSearch() { doTest(); }
  @Test void testSkipInferenceForInapplicableMethodReference() { doTest(); }
  @Test void testRegisterVariablesForNonFoundParameterizations() { doTest(); }

  @Test void testConstructorReferenceOnRawTypeWithInferredSubtypes() { doTest(); }
  @Test void testPreferErrorOnTopLevelToFailedSubstitutorOnNestedLevel() { doTest(); }
  @Test void testDontIgnoreIncompatibilitiesDuringFirstApplicabilityCheck() { doTest(); }
  @Test void testCaptureOnDedicatedParameterOfSecondSearch() { doTest(); }
  @Test void testVoidConflict() { doTest(); }
  @Test void testCreateMethodFromMethodRefApplicability() { doTest(); }
  @Test void testErrorMessageOnTopCallWhenFunctionalInterfaceIsNotInferred() { doTest(); }
  @Test void testReferencesToPolymorphicMethod() { doTest(); }
  @Test void testTypeArgumentsOnFirstSearchAccessibleMethod() { doTest(); }
  @Test void testIDEA250434() { doTest(); }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean warnings) {
    getFixture().testHighlighting(warnings, false, false, getTestName(false) + ".java");
  }
}
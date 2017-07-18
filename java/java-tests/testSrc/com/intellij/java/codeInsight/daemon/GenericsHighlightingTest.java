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
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

public class GenericsHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/genericsHighlighting";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UncheckedWarningLocalInspection(), new UnusedImportLocalInspection()};
  }

  @Override
  protected Sdk getProjectJDK() {
    return getTestName(false).contains("Jdk14") ? IdeaTestUtil.getMockJdk14() : super.getProjectJDK();
  }

  private void doTest(@NotNull LanguageLevel languageLevel, @NotNull JavaSdkVersion sdkVersion, boolean checkWarnings) {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(languageLevel);
    IdeaTestUtil.setTestVersion(sdkVersion, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, false);
  }

  private void doTest5(boolean checkWarnings) { doTest(LanguageLevel.JDK_1_5, JavaSdkVersion.JDK_1_5, checkWarnings); }
  private void doTest6(boolean checkWarnings) { doTest(LanguageLevel.JDK_1_6, JavaSdkVersion.JDK_1_6, checkWarnings); }
  private void doTest7(boolean checkWarnings) { doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_7, checkWarnings); }
  private void doTest7Incompatibility() { doTest(LanguageLevel.JDK_1_5, JavaSdkVersion.JDK_1_7, false); }
  private void doTest8Incompatibility(boolean checkWarnings) { doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_8, checkWarnings); }

  public void testReferenceTypeParams() { doTest5(false); }
  public void testOverridingMethods() { doTest5(false); }
  public void testTypeParameterBoundsList() { doTest5(false); }
  public void testClassInheritance() { doTest5(false); }
  public void testTypeInference() { doTest5(false); }
  public void testRaw() { doTest5(true); }
  public void testExceptions() { doTest5(false); }
  public void testExplicitMethodParameters() { doTest5(false); }
  public void testExplicitMethodParameters1() { doTest5(false); }
  public void testInferenceWithBounds() { doTest5(false); }
  public void testInferenceWithSuperBounds() { doTest5(false); }
  public void testInferenceWithUpperBoundPromotion() { doTest7Incompatibility(); }
  public void testVariance() { doTest5(false); }
  public void testForeachTypes() { doTest5(false); }
  public void testRawOverridingMethods() { doTest5(false); }
  public void testAutoboxing() { doTest5(false); }
  public void testAutoboxingMethods() { doTest5(false); }
  public void testAutoboxingConstructors() { doTest5(false); }
  public void testEnumWithAbstractMethods() { doTest5(false); }
  public void testEnum() { doTest5(false); }
  public void testEnum56239() { doTest6(false); }
  public void testSameErasure() { doTest5(false); }
  public void testPairsWithSameErasure() { doTest5(false); }
  public void testMethods() { doTest5(false); }
  public void testFields() { doTest5(false); }
  public void testStaticImports() { doTest5(true); }
  public void testUncheckedCasts() { doTest5(true); }
  public void testUncheckedOverriding() { doTest5(true); }
  public void testWildcardTypes() { doTest5(true); }
  public void testConvertibleTypes() { doTest5(true); }
  public void testIntersectionTypes() { doTest6(true); }
  public void testVarargs() { doTest5(true); }
  public void testTypeArgsOnRaw() { doTest5(false); }
  public void testConditionalExpression() { doTest5(false); }
  public void testUnused() { doTest5(true); }
  public void testIDEADEV7337() { doTest5(true); }
  public void testIDEADEV10459() { doTest5(true); }
  public void testIDEADEV12951() { doTest5(true); }
  public void testIDEADEV13011() { doTest5(true); }
  public void testIDEADEV14006() { doTest5(true); }
  public void testIDEADEV14103() { doTest5(true); }
  public void testIDEADEV15534() { doTest5(true); }
  public void testIDEADEV23157() { doTest5(true); }
  public void testIDEADEV24166() { doTest5(true); }
  public void testIDEADEV25778() { doTest5(true); }
  public void testIDEADEV57343() { doTest5(false); }
  public void testSOE() { doTest5(true); }
  public void testGenericExtendException() { doTest5(false); }
  public void testSameErasureDifferentReturnTypes() { doTest7Incompatibility(); }
  public void testSameErasureDifferentReturnTypesJdk14() { doTest5(false); }
  public void testDeepConflictingReturnTypes() { doTest5(false); }
  public void testInheritFromTypeParameter() { doTest5(false); }
  public void testAnnotationsAsPartOfModifierList() { doTest5(false); }
  public void testImplementAnnotation() { doTest5(false); }
  public void testOverrideAtLanguageLevel5() { doTest5(false); }
  public void testOverrideAtLanguageLevel6() { doTest6(false); }
  public void testSuperMethodCallWithErasure() { doTest5(false); }
  public void testWildcardCastConversion() { doTest5(false); }
  public void testTypeWithinItsWildcardBound() { doTest5(false); }
  public void testMethodSignatureEquality() { doTest5(false); }
  public void testInnerClassRef() { doTest5(false); }
  public void testPrivateInnerClassRef() { doTest5(false); }
  public void testWideningCastToTypeParam() { doTest5(false); }
  public void testCapturedWildcardAssignments() { doTest5(false); }
  public void testTypeParameterBoundVisibility() { doTest7Incompatibility(); }
  public void testTypeParameterBoundVisibilityJdk14() { doTest5(false); }
  public void testUncheckedWarningsLevel6() { doTest6(true); }
  public void testIDEA77991() { doTest5(false); }
  public void testIDEA80386() { doTest5(false); }
  public void testIDEA66311() { doTest7Incompatibility(); }
  public void testIDEA67672() { doTest7Incompatibility(); }
  public void testIDEA88895() { doTest7Incompatibility(); }
  public void testIDEA67667() { doTest7Incompatibility(); }
  public void testIDEA66311_16() { doTest5(false); }
  public void testIDEA76283() { doTest5(false); }
  public void testIDEA74899() { doTest5(false); }
  public void testIDEA63291() { doTest5(false); }
  public void testIDEA72912() { doTest5(false); }
  public void testIllegalGenericTypeInInstanceof() { doTest5(false); }
  public void testIDEA57339() { doTest5(false); }
  public void testIDEA57340() { doTest5(false); }
  public void testIDEA89771() { doTest5(false); }
  public void testIDEA89801() { doTest5(false); }
  public void testIDEA67681() { doTest5(false); }
  public void testIDEA67599() { doTest5(false); }
  public void testIDEA57668() { doTest5(false); }
  public void testIDEA57667() { doTest7Incompatibility(); }
  public void testIDEA57650() { doTest7Incompatibility(); }
  public void testIDEA57378() { doTest5(false); }
  public void testIDEA57557() { doTest5(false); }
  public void testIDEA57563() { doTest5(false); }
  public void testIDEA57275() { doTest5(false); }
  public void testIDEA57533() { doTest5(false); }
  public void testIDEA57509() { doTest5(false); }
  public void testIDEA57410() { doTest5(false); }
  public void testIDEA57411() { doTest5(false); }
  public void testIDEA57484() { doTest5(false); }
  public void testIDEA57485() { doTest5(false); }
  public void testIDEA57486() { doTest5(false); }
  public void testIDEA57492() { doTest5(false); }
  public void testIDEA57493() { doTest5(false); }
  public void testIDEA57495() { doTest5(false); }
  public void testIDEA57494() { doTest5(false); }
  public void testIDEA57496() { doTest5(false); }
  public void testIDEA57264() { doTest5(false); }
  public void testIDEA57315() { doTest5(false); }
  public void testIDEA57346() { doTest5(false); }
  public void testIDEA57284() { doTest5(false); }
  public void testIDEA57286() { doTest5(false); }
  public void testIDEA57307() { doTest5(true); }
  public void testIDEA57308() { doTest5(false); }
  public void testIDEA57310() { doTest5(false); }
  public void testIDEA57311() { doTest5(false); }
  public void testIDEA57309() { doTest5(false); }
  public void testIDEA90802() { doTest5(false); }
  public void testIDEA70370() { doTest5(true); }
  public void testInaccessibleThroughWildcard() { doTest7Incompatibility();}
  public void testInconvertibleTypes() { doTest5(false); }
  public void testIncompatibleReturnType() { doTest5(false); }
  public void testContinueInferenceAfterFirstRawResult() { doTest5(false); }
  public void testDoNotAcceptLowerBoundIfRaw() { doTest5(false); }
  public void testStaticOverride() { doTest5(false); }
  public void testTypeArgumentsGivenOnRawType() { doTest7Incompatibility(); }
  public void testSelectFromTypeParameter() { doTest5(false); }
  public void testTypeArgumentsGivenOnAnonymousClassCreation() { doTest5(false); }
  public void testIDEA94011() { doTest5(false); }
  public void testDifferentTypeParamsInOverloadedMethods() { doTest5(true); }
  public void testIDEA91626() { doTest5(true); }
  public void testIDEA92022() { doTest5(false); }
  public void testRawOnParameterized() { doTest5(false); }
  public void testFailedInferenceWithBoxing() { doTest5(false); }
  public void testFixedFailedInferenceWithBoxing() { doTest7Incompatibility(); }
  public void testInferenceWithBoxingCovariant() { doTest7Incompatibility(); }
  public void testSuperWildcardIsNotWithinItsBound() { doTest7Incompatibility(); }
  public void testSpecificReturnType() { doTest7Incompatibility(); }
  public void testParameterizedParameterBound() { doTest7Incompatibility(); }
  public void testInstanceClassInStaticContextAccess() { doTest7Incompatibility(); }
  public void testFlattenIntersectionType() { doTest7Incompatibility(); }
  public void testIDEA97276() { doTest7Incompatibility(); }
  public void testWildcardsBoundsIntersection() { doTest7Incompatibility(); }
  public void testOverrideWithMoreSpecificReturn() { doTest7Incompatibility(); }
  public void testIDEA97888() { doTest7Incompatibility(); }
  public void testMethodCallParamsOnRawType() { doTest5(false); }
  public void testIDEA98421() { doTest5(false); }
  public void testErasureTypeParameterBound() { doTest5(false); }
  public void testThisAsAccessObject() { doTest5(false); }
  public void testIDEA67861() { doTest7Incompatibility(); }
  public void testIDEA67597() { doTest5(false); }
  public void testIDEA57539() { doTest5(false); }
  public void testIDEA67570() { doTest5(false); }
  public void testIDEA99061() { doTest5(false); }
  public void testIDEA99347() { doTest5(false); }
  public void testIDEA86875() { doTest5(false); }
  public void testIDEA103760(){ doTest5(false); }
  public void testIDEA105846(){ doTest5(false); }
  public void testIDEA105695(){ doTest5(false); }
  public void testIDEA104992(){ doTest5(false); }
  public void testIDEA57446(){ doTest5(false); }
  public void testIDEA67677(){ doTest5(false); }
  public void testIDEA67798(){ doTest5(false); }
  public void testIDEA57534(){ doTest5(false); }
  public void testIDEA57482(){ doTest5(false); }
  public void testIDEA67577(){ doTest5(false); }
  public void testIDEA57413(){ doTest5(false); }
  public void testIDEA57265(){ doTest5(false); }
  public void testIDEA57271(){ doTest5(false); }
  public void testIDEA57272(){ doTest5(false); }
  public void testIDEA57285(){ doTest5(false); }
  public void testIDEA65066(){ doTest5(false); }
  public void testIDEA67998(){ doTest5(false); }
  public void testIDEA18425(){ doTest5(false); }
  public void testIDEA27080(){ doTest5(false); }
  public void testIDEA22079(){ doTest5(false); }
  public void testIDEA21602(){ doTest5(false); }
  public void testIDEA21602_7(){ doTest7(false); }
  public void testIDEA21597() { doTest5(false);}
  public void testIDEA20573() { doTest5(false);}
  public void testIDEA20244() { doTest5(false);}
  public void testIDEA22005() { doTest5(false);}
  public void testIDEA57259() { doTest5(false);}
  public void testIDEA107957() { doTest6(false);}
  public void testIDEA109875() { doTest6(false);}
  public void testIDEA106964() { doTest5(false);}
  public void testIDEA107782() { doTest5(false);}
  public void testInheritedWithDifferentArgsInTypeParams() { doTest5(false);}
  public void testInheritedWithDifferentArgsInTypeParams1() { doTest5(false);}
  public void testIllegalForwardReferenceInTypeParameterDefinition() { doTest5(false);}
  public void testIDEA57877() { doTest5(false);}
  public void testIDEA110568() { doTest5(false);}
  public void testTypeParamsCyclicInference() { doTest5(false);}
  public void testCaptureTopLevelWildcardsForConditionalExpression() { doTest5(false);}
  public void testGenericsOverrideMethodInRawInheritor() { doTest5(false);}
  public void testIDEA107654() { doTest5(false); }
  public void testIDEA55510() { doTest5(false); }
  public void testIDEA27185(){ doTest6(false); }
  public void testIDEA67571(){ doTest7(false); }
  public void testTypeArgumentsOnRawType(){ doTest6(false); }
  public void testTypeArgumentsOnRawType17(){ doTest7(false); }
  public void testWildcardsOnRawTypes() { doTest5(false); }
  public void testDisableWithinBoundsCheckForSuperWildcards() { doTest7(false); }
  public void testIDEA108287() { doTest5(false); }
  public void testIDEA77128() { doTest7(false); }
  public void testDisableCastingToNestedWildcards() { doTest5(false); }
  public void testBooleanInferenceFromIfCondition() { doTest5(false); }
  public void testMethodCallOnRawTypesExtended() { doTest5(false); }
  public void testIDEA104100() {doTest7(false);}
  public void testIDEA104160() {doTest7(false);}
  public void testSOEInLeastUpperClass() {doTest7(false);}
  public void testIDEA57334() { doTest5(false); }
  public void testIDEA57325() { doTest5(false); }
  public void testIDEA67835() { doTest5(false); }
  public void testIDEA67744() { doTest5(false); }
  public void testIDEA67682() { doTest5(false); }
  public void testIDEA57391() { doTest5(false); }
  public void testIDEA110869() { doTest5(false); }
  /*public void testIDEA110947() { doTest5(false); }*/
  public void testIDEA112122() { doTest5(false); }
  public void testNoInferenceFromTypeCast() { doTest5(false); }
  public void testCaptureWildcardsInTypeCasts() { doTest5(false); }
  public void testIDEA111085() { doTest5(false); }
  public void testIDEA109556() { doTest5(false); }
  public void testIDEA107440() { doTest5(false); }
  public void testIDEA57289() { doTest5(false); }
  public void testIDEA57439() { doTest5(false); }
  public void testIDEA57312() { doTest5(false); }
  public void testIDEA67865() { doTest5(false); }
  public void testBoxingSpecific() { doTest5(false); }
  public void testIDEA67843() { doTest5(false); }
  public void testAmbiguousTypeParamVsConcrete() { doTest5(false); }
  public void testRawAssignments() { doTest5(false); }
  public void testIDEA87860() { doTest5(false); }
  public void testIDEA67584() { doTest5(false); }
  public void testIDEA113225() { doTest5(false); }
  public void testIDEA67518() { doTest5(false); }
  public void testIDEA57252() { doTest5(false); }
  public void testIDEA57274() { doTest7(false); }
  public void testIDEA67591() { doTest5(false); }
  public void testIDEA114894() { doTest5(false); }
  public void testIDEA60818() { doTest5(false); }
  public void testIDEA63331() { doTest5(false); }
  public void testIDEA60836() { doTest5(false); }
  public void testIDEA54197() { doTest5(false); }
  public void testIDEA71582() { doTest5(false); }
  public void testIDEA65377() { doTest5(false); }
  public void testIDEA113526() { doTest5(true); }
  public void testIncompatibleReturnTypeBounds() { doTest7(false); }
  public void testIDEA116493() { doTest7(false); }
  public void testIDEA117827() { doTest7(false); }
  public void testIDEA118037() { doTest7(false); }
  public void testIDEA119546() { doTest7(false); }
  public void testIDEA118527() { doTest7(false); }
  public void testIDEA120153() { doTest7(false); }
  public void testIDEA120563() { doTest7(false); }
  public void testIDEA121400() { doTest7(false); }
  public void testIDEA123316() { doTest7(false); }
  public void testUnrelatedReturnInTypeArgs() { doTest7(false); }
  public void testIDEA123352() { doTest7(false); }
  public void testIDEA123518() { doTest7(false); }
  public void testIDEA64103() { doTest7(false); }
  public void testIDEA123338() { doTest7(false); }
  public void testIDEA124271() { doTest7(false); }
  public void testIDEA124352() { doTest7(false); }
  public void testIDEA124019() { doTest7(false); }
  public void testIDEA123509() { doTest7(false); }
  public void testIDEA125031() { doTest7(false); }
  public void testIDEA24479() { doTest7(false); }
  public void testIDEA118536() { doTest7(false); }
  public void testIDEA125744() { doTest7(false); }
  public void testIDEA125423() { doTest7(false); }
  public void testIDEA118533() { doTest7(false); }
  public void testIDEA112117() { doTest7(false); }
  public void testIDEA24496() { doTest7(false); }
  public void testIDEA58692() { doTest7(false); }
  public void testIDEA57290() { doTest7(false); }
  public void testIDEA119757() { doTest7(false); }
  public void testIDEA67578() { doTest7(false); }
  public void testIDEA57388() { doTest7(false); }
  public void testIDEA125800() { doTest7(false); }
  public void testIDEA125816() { doTest7(false); }
  public void testIDEA57338() { doTest6(false); }
  public void testIDEA67600() { doTest7(false); }
  public void testIDEA126697() { doTest7(true); }
  public void testIDEA126633() { doTest7(false); }
  public void testIDEA124363() { doTest7(false); }
  public void testIDEA78402() { doTest7(false); }
  public void testIDEA106985() { doTest7(false); }
  public void testIDEA114797() { doTest7(false); }
  public void testCaptureWildcardFromUnboundCaptureWildcard() { doTest7(false); }
  public void testSuperCaptureSubstitutionWhenTypeParameterHasUpperBounds() { doTest7(false); }
  public void testParameterBoundsWithCapturedWildcard() { doTest7(false); }
  //jdk should propagate LL 1.4 but actually it provides LL 1.7?!
  public void testCastObjectToIntJdk14() { doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_4, false); }
  public void testSubstitutorCaptureBoundComposition() { doTest7(false); }
  public void testIDEA57508() { doTest7(false); }
  public void testIDEA57293() { doTest7(false); }
  public void testIDEA59283() { doTest7(false); }
  public void testIDEA127767() { doTest7(false); }
  public void testIDEA113631() { doTest7(false); }
  public void testIDEA57537() { doTest7(false); }
  public void testMethodCallTypeErasedWhenUncheckedConversionWasAppliedDuringApplicabilityCheck() { doTest7(false); }
  public void testMethodCallTypeNotErasedWhenUncheckedConversionWasAppliedButNoTypeParamsProvided() { doTest7(false); }
  public void testInferredParameterInBoundsInRecursiveGenerics() { doTest7(false); }
  public void testIDEA65386() { doTest7(true); }
  public void testHiddenMethodsOfAnonymousClass() { doTest7(false); }
  public void testNestedLevelsToCheckTypeArguments() { doTest7(false); }
  public void testExpectedTypeFromOuterArrayCreation() { doTest7(false); }
  public void testDistinguishWildcardCapturesAlsoByMethodCalls() { doTest7(false); }
  public void testSubstituteTypeParameterOfCapturedWildcardOnSubstitution() { doTest7(false); }
  public void testAssignabilityBetweenWildcardsAndArrays() { doTest7(false); }
  public void testCastConversionToTypeParameter() { doTest7(false); }
  public void testTypeDistinctProverForWildcardAndTypeParameter() { doTest7(false); }
  public void testIDEA58687() { doTest7(false); }
  public void testDontReportWeakerVisibilityProblemUpInHierarchy() { doTest7(false); }
  public void testSuperObjectCapturedWildcardEquality() { doTest7(false); }
  public void testSOEInInfiniteTypesWithSuperWildcards() { doTest7(false); }
  public void testMakeUseOfUpperBoundOfCaptureWildcardDuringNormalization() { doTest7(false); }
  public void testCastFromGenericTypeWithTypeParameterWithExtendsAsArgument() { doTest7(true); }
  public void testIDEA139067() { doTest7(true); }
  public void testIDEA57336() { doTest(LanguageLevel.JDK_1_8, JavaSdkVersion.JDK_1_8, true); }
  public void testPreserveCaptureWildcardsInUpperBounds() { doTest7(false); }
  public void testIDEA57361() { doTest7(false); }
  public void testRetrieveBoundFromCapturedWildcardUpperBoundOnNormalize() { doTest7(false); }
  public void testCapturedBoundOfCapture() { doTest7(false); }
  public void testWildcardsWithRawBound() { doTest7(false); }
  public void testIDEA98866() { doTest7(false); }
  public void testIDEA81318() { doTest7(false); }
  public void testIDEA138957() { doTest7(false); }
  public void testIDEA130453() { doTest7(false); }
  public void testReifiableTypeWithLocalClasses() { doTest7(false); }
  public void testStopBoundsPromotionInsideNestedWildcards() { doTest7(false); }
  public void testIDEA130243() { doTest7(false); }
  public void testCastingToPrimitive() { doTest7(false); }
  public void testProvablyDistinctForWildcardsWithArrayBounds() { doTest7(false); }
  public void testReturnTypeOverrideEquivalenceWithTypeHierarchy() { doTest7(false); }
  public void testIgnoreReturnTypeClashForObjectProtectedMethodsAndInterfaceMethodsWithSameSignature() { doTest7(false); }
  public void testIDEA150499() { doTest7(false); }
  public void testRecursiveTypesTypeArgumentDistinction() { doTest7(true); }
  public void testRecursiveBoundsDependencies() { doTest7(false); }
  public void testUnboxingFromTypeParameter() { doTest7(false); }

  public void testLeastUpperBoundWithRecursiveTypes() {
    PsiManager manager = getPsiManager();
    GlobalSearchScope scope = GlobalSearchScope.allScope(getProject());
    PsiType leastUpperBound = GenericsUtil.getLeastUpperBound(
      PsiType.INT.getBoxedType(manager, scope), PsiType.LONG.getBoxedType(manager, scope), manager);
    assertNotNull(leastUpperBound);
    assertEquals("Number & Comparable<? extends Number & Comparable<?>>", leastUpperBound.getPresentableText());
  }

  public void testReturnTypeSubstitutableForSameOverrideEquivalentMethods() { doTest7(false); }
  public void testCaptureConversionWithWildcardBounds() { doTest7(false); }
  public void testArrayContainsInTypeParameterWithSerializableBound() { doTest7(true); }
  public void testIntersectTypeParameterBounds() { doTest7(false); }
  public void testTopLevelCaptureConversion() { doTest7(false); }
  public void testNoCaptureConversionForArrayType() { doTest7(false); }
  public void testErasureOfMethodCallExpressionTypeIfItDoesntDependOnGenericsParameter() { doTest7(false); }
  public void testUncheckedConversionInReturnType() { doTest7(false); }
  public void testNotErasedReturnValueUnderJdk7() { doTest8Incompatibility(false); }
  public void testAvoidDblSubstitutionDuringErasureOfParameterTypesOfMethodSignature() { doTest8Incompatibility(false); }
  public void testUncheckedWarningWhenCastingFromCapturedWildcard() { doTest8Incompatibility(true); }
  public void testEnclosingRefInTopLevelClassExtendingInnerWhichExtendsItsOuter() { doTest8Incompatibility(true); }
  public void testGenericThrowTypes() { doTest5(false); }
  public void testRecursiveParamBoundsWhenSuperSubstitution() { doTest6(false); }
  public void testCaptureForBoundCheck() { doTest6(false); }
}
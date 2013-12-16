/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class GenericsHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/genericsHighlighting";

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UncheckedWarningLocalInspection(), new UnusedSymbolLocalInspection(), new UnusedImportLocalInspection()};
  }

  @Override
  protected Sdk getProjectJDK() {
    return getTestName(false).contains("Jdk14") ? IdeaTestUtil.getMockJdk14() : super.getProjectJDK();
  }

  private void doTest(@NotNull LanguageLevel languageLevel, @NotNull JavaSdkVersion sdkVersion, boolean checkWarnings) {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(languageLevel);
    IdeaTestUtil.setTestVersion(sdkVersion, getModule(), myTestRootDisposable);
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, false);
  }
  private void doTest5(boolean checkWarnings) { doTest(LanguageLevel.JDK_1_5, JavaSdkVersion.JDK_1_6, checkWarnings); }
  private void doTest6(boolean checkWarnings) { doTest(LanguageLevel.JDK_1_6, JavaSdkVersion.JDK_1_6, checkWarnings); }
  private void doTest7Incompatibility(boolean checkWarnings) { doTest(LanguageLevel.JDK_1_5, JavaSdkVersion.JDK_1_7, checkWarnings); }

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
  public void testInferenceWithUpperBoundPromotion() { doTest7Incompatibility(false); }
  public void testVariance() { doTest5(false); }
  public void testForeachTypes() { doTest5(false); }
  public void testRawOverridingMethods() { doTest5(false); }
  public void testAutoboxing() { doTest5(false); }
  public void testAutoboxingMethods() { doTest5(false); }
  public void testAutoboxingConstructors() { doTest5(false); }
  public void testEnumWithAbstractMethods() { doTest5(false); }
  public void testEnum() { doTest(LanguageLevel.JDK_1_5, JavaSdkVersion.JDK_1_5, false); }
  public void testEnum56239() { doTest(LanguageLevel.JDK_1_6, JavaSdkVersion.JDK_1_6, false); }
  public void testSameErasure() { doTest5(false); }
  public void testMethods() { doTest5(false); }
  public void testFields() { doTest5(false); }
  public void testStaticImports() { doTest5(true); }
  public void testUncheckedCasts() { doTest5(true); }
  public void testUncheckedOverriding() { doTest5(true); }
  public void testWildcardTypes() { doTest5(true); }
  public void testConvertibleTypes() { doTest5(true); }
  public void testIntersectionTypes() { doTest7Incompatibility(true); }
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
  public void testSameErasureDifferentReturnTypes() { doTest7Incompatibility(false); }
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
  public void testTypeParameterBoundVisibility() { doTest7Incompatibility(false); }
  public void testTypeParameterBoundVisibilityJdk14() { doTest5(false); }
  public void testUncheckedWarningsLevel6() { doTest6(true); }
  public void testIDEA77991() { doTest5(false); }
  public void testIDEA80386() { doTest5(false); }
  public void testIDEA66311() { doTest7Incompatibility(false); }
  public void testIDEA67672() { doTest7Incompatibility(false); }
  public void testIDEA88895() { doTest7Incompatibility(false); }
  public void testIDEA67667() { doTest7Incompatibility(false); }
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
  public void testIDEA57667() { doTest7Incompatibility(false); }
  public void testIDEA57650() { doTest7Incompatibility(false); }
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
  public void testInaccessibleThroughWildcard() { doTest7Incompatibility(false);}
  public void testInconvertibleTypes() { doTest5(false); }
  public void testIncompatibleReturnType() { doTest5(false); }
  public void testContinueInferenceAfterFirstRawResult() { doTest5(false); }
  public void testDoNotAcceptLowerBoundIfRaw() { doTest5(false); }
  public void testStaticOverride() { doTest5(false); }
  public void testTypeArgumentsGivenOnRawType() { doTest7Incompatibility(false); }
  public void testSelectFromTypeParameter() { doTest5(false); }
  public void testTypeArgumentsGivenOnAnonymousClassCreation() { doTest5(false); }

  public void testIDEA94011() { doTest5(false); }
  public void testDifferentTypeParamsInOverloadedMethods() { doTest5(true); }
  public void testIDEA91626() { doTest5(true); }
  public void testIDEA92022() { doTest5(false); }
  public void testRawOnParameterized() { doTest5(false); }
  public void testFailedInferenceWithBoxing() { doTest5(false); }
  public void testFixedFailedInferenceWithBoxing() { doTest7Incompatibility(false); }
  public void testInferenceWithBoxingCovariant() { doTest7Incompatibility(false); }
  public void testSuperWildcardIsNotWithinItsBound() { doTest7Incompatibility(false); }
  public void testSpecificReturnType() { doTest7Incompatibility(false); }
  public void testParameterizedParameterBound() { doTest7Incompatibility(false); }
  public void testInstanceClassInStaticContextAccess() { doTest7Incompatibility(false); }
  public void testFlattenIntersectionType() { doTest7Incompatibility(false); }
  public void testIDEA97276() { doTest7Incompatibility(false); }
  public void testWildcardsBoundsIntersection() { doTest7Incompatibility(false); }
  public void testOverrideWithMoreSpecificReturn() { doTest7Incompatibility(false); }
  public void testIDEA97888() { doTest7Incompatibility(false); }
  public void testMethodCallParamsOnRawType() { doTest5(false); }
  public void testIDEA98421() { doTest5(false); }
  public void testErasureTypeParameterBound() { doTest5(false); }
  public void testThisAsAccessObject() { doTest5(false); }
  public void testIDEA67861() { doTest7Incompatibility(false); }
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
  public void testIDEA21602_7(){ doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_7, false); }

  public void testIDEA21597() throws Exception { doTest5(false);}
  public void testIDEA20573() throws Exception { doTest5(false);}
  public void testIDEA20244() throws Exception { doTest5(false);}
  public void testIDEA22005() throws Exception { doTest5(false);}
  public void testIDEA57259() throws Exception { doTest5(false);}
  public void testIDEA107957() throws Exception { doTest6(false);}
  public void testIDEA109875() throws Exception { doTest6(false);}
  public void testIDEA106964() throws Exception { doTest5(false);}
  public void testIDEA107782() throws Exception { doTest5(false);}
  public void testInheritedWithDifferentArgsInTypeParams() throws Exception { doTest5(false);}
  public void testIllegalForwardReferenceInTypeParameterDefinition() throws Exception { doTest5(false);}

  public void testIDEA57877() throws Exception { doTest5(false);}
  public void testIDEA110568() throws Exception { doTest5(false);}
  public void testTypeParamsCyclicInference() throws Exception { doTest5(false);}
  public void testCaptureTopLevelWildcardsForConditionalExpression() throws Exception { doTest5(false);}
  public void testGenericsOverrideMethodInRawInheritor() throws Exception { doTest5(false);}

  public void testIDEA107654() throws Exception {
    doTest5(false);
  }

  public void testIDEA55510() throws Exception {
    doTest5(false);
  }

  public void testIDEA27185(){ doTest(LanguageLevel.JDK_1_6, JavaSdkVersion.JDK_1_6, false); }
  public void testIDEA67571(){ doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_7, false); }
  public void testTypeArgumentsOnRawType(){ doTest(LanguageLevel.JDK_1_6, JavaSdkVersion.JDK_1_6, false); }
  public void testTypeArgumentsOnRawType17(){ doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_7, false); }

  public void testWildcardsOnRawTypes() { doTest5(false); }
  public void testDisableWithinBoundsCheckForSuperWildcards() {
    doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_7, false);
  }

  public void testIDEA108287() throws Exception {
    doTest5(false);
  }

  public void testIDEA77128() throws Exception {
    doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_7, false);
  }

  public void testDisableCastingToNestedWildcards() throws Exception {
    doTest5(false);
  }

  public void testBooleanInferenceFromIfCondition() throws Exception {
    doTest5(false);
  }

  public void testMethodCallOnRawTypesExtended() throws Exception {
    doTest5(false);
  }

  public void testIDEA104100() {doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_7, false);}
  public void testIDEA104160() {doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_7, false);}
  public void testSOEInLeastUpperClass() {doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_7, false);}

  public void testIDEA57334() {
    doTest5(false);
  }

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
  public void testRawAssignments() throws Exception { doTest5(false); }
  public void testIDEA87860() throws Exception { doTest5(false); }
  public void testIDEA67584() throws Exception { doTest5(false); }
  public void testIDEA113225() throws Exception { doTest5(false); }
  public void testIDEA67518() throws Exception { doTest5(false); }
  public void testIDEA57252() throws Exception { doTest5(false); }
  public void testIDEA57274() throws Exception { doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_7, false); }
  public void testIDEA67591() throws Exception {
    doTest5(false);
  }

  public void testIDEA114894() { doTest5(false); }
  public void testIDEA60818() { doTest5(false); }
  public void testIDEA63331() { doTest5(false); }
  public void testIDEA60836() { doTest5(false); }
  public void testIDEA54197() { doTest5(false); }
  public void testIDEA71582() { doTest5(false); }
  public void testIDEA65377() { doTest5(false); }
  public void testIDEA113526() { doTest5(true); }
  public void testIDEA116493() { doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_7, false); }
  public void testIDEA117827() { doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_7, false); }
  public void testIDEA118037() { doTest(LanguageLevel.JDK_1_7, JavaSdkVersion.JDK_1_7, false); }

  public void testJavaUtilCollections_NoVerify() throws Exception {
    PsiClass collectionsClass = getJavaFacade().findClass("java.util.Collections", GlobalSearchScope.moduleWithLibrariesScope(getModule()));
    assertNotNull(collectionsClass);
    collectionsClass = (PsiClass)collectionsClass.getNavigationElement();
    final String text = collectionsClass.getContainingFile().getText();
    configureFromFileText("Collections.java", text.replaceAll("\r", "\n"));
    doTestConfiguredFile(false, false, null);
  }
}

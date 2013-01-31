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
import com.intellij.openapi.projectRoots.JavaVersionService;
import com.intellij.openapi.projectRoots.JavaVersionServiceImpl;
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

  private void doTest(LanguageLevel languageLevel, JavaSdkVersion sdkVersion, boolean checkWarnings) {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(languageLevel);
    ((JavaVersionServiceImpl)JavaVersionService.getInstance()).setTestVersion(sdkVersion, myTestRootDisposable);
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
  public void testEnum() { doTest5(false); }
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
  public void testTypeArgumentsGivenOnRawType() { doTest5(false); }
  public void testTypeArgumentsGivenOnAnonymousClassCreation() { doTest5(false); }
  //public void testIDEA94011() { doTest5(false); }
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

  public void testJavaUtilCollections_NoVerify() throws Exception {
    PsiClass collectionsClass = getJavaFacade().findClass("java.util.Collections", GlobalSearchScope.moduleWithLibrariesScope(getModule()));
    assertNotNull(collectionsClass);
    collectionsClass = (PsiClass)collectionsClass.getNavigationElement();
    final String text = collectionsClass.getContainingFile().getText();
    configureFromFileText("Collections.java", text.replaceAll("\r", "\n"));
    doTestConfiguredFile(false, false, null);
  }
}

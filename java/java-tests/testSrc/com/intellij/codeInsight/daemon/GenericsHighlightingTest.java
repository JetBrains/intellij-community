/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;


public class GenericsHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/genericsHighlighting";

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new UncheckedWarningLocalInspection(), new UnusedSymbolLocalInspection(), new UnusedImportLocalInspection()};
  }

  private void doTest(boolean checkWarnings) throws Exception {
    doTest(getTestName(false) + ".java", checkWarnings);
  }

  private void doTest(@NonNls String filePath, boolean checkWarnings) throws Exception {
    doTest(BASE_PATH + "/" + filePath, checkWarnings, false);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevel level;
    final String testName = getTestName(false);
    if (testName.contains("Level17")) {
      level = LanguageLevel.JDK_1_7;
    } else if (testName.contains("Level6")) {
      level = LanguageLevel.JDK_1_6;
    }
    else {
      level = LanguageLevel.JDK_1_5;
    }
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(level);
  }

  @Override
  protected Sdk getProjectJDK() {
    return getTestName(false).contains("Jdk14") ? JavaSdkImpl.getMockJdk14() : super.getProjectJDK();
  }

  public void testReferenceTypeParams() throws Exception { doTest(false); }
  public void testOverridingMethods() throws Exception { doTest(false); }
  public void testTypeParameterBoundsList() throws Exception { doTest(false); }
  public void testClassInheritance() throws Exception { doTest(false); }
  public void testTypeInference() throws Exception { doTest(false); }
  public void testRaw() throws Exception { doTest(true); }
  public void testExceptions() throws Exception { doTest(false); }
  public void testExplicitMethodParameters() throws Exception { doTest(false); }
  public void testExplicitMethodParameters1() throws Exception { doTest(false); }
  public void testInferenceWithBounds() throws Exception { doTest(false); }
  public void testInferenceWithSuperBounds() throws Exception { doTest(false); }
  public void testInferenceWithUpperBoundPromotion() throws Exception { doTest(false); }
  public void testVariance() throws Exception { doTest(false); }
  public void testForeachTypes() throws Exception { doTest(false); }
  public void testRawOverridingMethods() throws Exception { doTest(false); }
  public void testAutoboxing() throws Exception { doTest(false); }
  public void testAutoboxingMethods() throws Exception { doTest(false); }
  public void testAutoboxingConstructors() throws Exception { doTest(false); }
  public void testEnumWithAbstractMethods() throws Exception { doTest(false); }
  public void testEnum() throws Exception { doTest(false); }
  public void testSameErasure() throws Exception { doTest(false); }
  public void testMethods() throws Exception { doTest(false); }
  public void testFields() throws Exception { doTest(false); }
  public void testStaticImports() throws Exception { doTest(true); }
  public void testUncheckedCasts() throws Exception { doTest(true); }
  public void testUncheckedOverriding() throws Exception { doTest(true); }
  public void testWildcardTypes() throws Exception { doTest(true); }
  public void testConvertibleTypes() throws Exception { doTest(true); }
  public void testIntersectionTypes() throws Exception { doTest(true); }
  public void testVarargs() throws Exception { doTest(true); }
  public void testTypeArgsOnRaw() throws Exception { doTest(false); }
  public void testConditionalExpression() throws Exception { doTest(false); }
  public void testUnused() throws Exception { doTest(true); }
  public void testIDEADEV7337() throws Exception { doTest(true); }
  public void testIDEADEV10459() throws Exception { doTest(true); }
  public void testIDEADEV12951() throws Exception { doTest(true); }
  public void testIDEADEV13011() throws Exception { doTest(true); }
  public void testIDEADEV14006() throws Exception { doTest(true); }
  public void testIDEADEV14103() throws Exception { doTest(true); }
  public void testIDEADEV15534() throws Exception { doTest(true); }
  public void testIDEADEV23157() throws Exception { doTest(true); }
  public void testIDEADEV24166() throws Exception { doTest(true); }
  public void testIDEADEV25778() throws Exception { doTest(true); }
  public void testIDEADEV57343() throws Exception { doTest(false); }
  public void testSOE() throws Exception { doTest(true); }
  public void testGenericExtendException() throws Exception { doTest(false); }
  public void testSameErasureDifferentReturnTypes() throws Exception { doTest17Incompatibility(); }
  public void testSameErasureDifferentReturnTypesJdk14() throws Exception { doTest(false); }
  public void testDeepConflictingReturnTypes() throws Exception { doTest(false); }
  public void testInheritFromTypeParameter() throws Exception { doTest(false); }
  public void testAnnotationsAsPartOfModifierList() throws Exception { doTest(false); }
  public void testImplementAnnotation() throws Exception { doTest(false); }
  public void testOverrideAtLanguageLevel6() throws Exception { doTest(false); }
  public void testOverrideAtLanguageLevel5() throws Exception { doTest(false); }
  public void testSuperMethodCallWithErasure() throws Exception { doTest(false); }
  public void testWildcardCastConversion() throws Exception { doTest(false); }
  public void testTypeWithinItsWildcardBound() throws Exception { doTest(false); }
  public void testMethodSignatureEquality() throws Exception { doTest(false); }
  public void testInnerClassRef() throws Exception { doTest(false); }
  public void testPrivateInnerClassRef() throws Exception { doTest(false); }
  public void testWideningCastToTypeParam() throws Exception { doTest(false); }
  public void testCapturedWildcardAssignments() throws Exception { doTest(false); }
  public void testTypeParameterBoundVisibility() throws Exception { doTest17Incompatibility(); }
  public void testTypeParameterBoundVisibilityJdk14() throws Exception { doTest(false); }
  public void testUncheckedWarningsLevel6() throws Exception { doTest(true); }
  public void testIDEA77991() throws Exception { doTest(false); }
  public void testIDEA80386() throws Exception { doTest(false); }
  public void testIDEA66311() throws Exception { doTest17Incompatibility(); }
  public void testIDEA67672() throws Exception { doTest17Incompatibility(); }
  public void testIDEA88895() throws Exception { doTest17Incompatibility(); }
  public void testIDEA67667() throws Exception { doTest17Incompatibility(); }
  public void testIDEA66311_16() throws Exception { doTest(false); }
  public void testIDEA76283() throws Exception { doTest(false); }
  public void testIDEA74899() throws Exception { doTest(false); }
  public void testIDEA63291() throws Exception { doTest(false); }
  public void testIDEA72912() throws Exception { doTest(false); }
  public void testIllegalGenericTypeInInstanceof() throws Exception { doTest(false); }
  public void testIDEA57339() throws Exception { doTest(false); }
  public void testIDEA57340() throws Exception { doTest(false); }
  public void testIDEA89771() throws Exception { doTest(false); }
  public void testIDEA89801() throws Exception { doTest(false); }
  public void testIDEA67681() throws Exception { doTest(false); }
  public void testInconvertibleTypes() throws Exception { doTest(false); }
  public void testIncompatibleReturnType() throws Exception { doTest(false); }
  public void testContinueInferenceAfterFirstRawResult() throws Exception { doTest(false); }
  public void testStaticOverride() throws Exception { doTest(false); }
  public void testTypeArgumentsGivenOnRawType() throws Exception { doTest(false); }
  public void testTypeArgumentsGivenOnAnonymousClassCreation() throws Exception { doTest(false); }

  public void testJavaUtilCollections_NoVerify() throws Exception {
    PsiClass collectionsClass = getJavaFacade().findClass("java.util.Collections", GlobalSearchScope.moduleWithLibrariesScope(getModule()));

    assertNotNull(collectionsClass);
    collectionsClass = (PsiClass)collectionsClass.getNavigationElement();
    final String text = collectionsClass.getContainingFile().getText();
    configureFromFileText("Collections.java", text.replaceAll("\r","\n"));
    doTestConfiguredFile(false, false, null);
  }

  private void doTest17Incompatibility() throws Exception {
    final JavaVersionServiceImpl javaVersionService = (JavaVersionServiceImpl)JavaVersionService.getInstance();
    try {
      javaVersionService.setTestVersion(JavaSdkVersion.JDK_1_7);
      doTest(false);
    }
    finally {
      javaVersionService.setTestVersion(null);
    }
  }
}

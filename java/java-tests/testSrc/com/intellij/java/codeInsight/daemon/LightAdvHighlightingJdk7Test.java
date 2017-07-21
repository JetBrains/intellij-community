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

import com.intellij.ToolExtensionPoints;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LightAdvHighlightingJdk7Test extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting7";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTools(new UnusedDeclarationInspection(), new UncheckedWarningLocalInspection(), new JavacQuirksInspection(), new RedundantCastInspection());
    setLanguageLevel(LanguageLevel.JDK_1_7);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_7, getModule(), getTestRootDisposable());
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17(); // has to have src.zip and weird method handle signatures
  }

  private void doTest(boolean checkWarnings, boolean checkInfos, InspectionProfileEntry... inspections) {
    enableInspectionTools(inspections);
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  public void testAllJava15Features() { doTest(false, false); }
  public void testEnumSyntheticMethods() { doTest(false, false); }
  public void testDuplicateAnnotations() { doTest(false, false); }
  public void testSwitchByString() { doTest(false, false); }
  public void testSwitchByInaccessibleEnum() { doTest(false, false); }
  public void testDiamondPos1() { doTest(false, false); }
  public void testDiamondPos2() { doTest(false, false); }
  public void testDiamondPos3() { doTest(false, false); }
  public void testDiamondPos4() { doTest(false, false); }
  public void testDiamondPos5() { doTest(false, false); }
  public void testDiamondPos6() { doTest(false, false); }
  public void testDiamondPos7() { doTest(false, false); }
  public void testDiamondNeg15() { doTest(false, false); }
  public void testDiamondPos9() { doTest(false, false); }
  public void testDiamondNeg1() { doTest(false, false); }
  public void testDiamondNeg2() { doTest(false, false); }
  public void testDiamondNeg3() { doTest(false, false); }
  public void testDiamondNeg4() { doTest(false, false); }
  public void testDiamondNeg5() { doTest(false, false); }
  public void testDiamondNeg6() { doTest(false, false); }
  public void testDiamondNeg7() { doTest(false, false); }
  public void testDiamondNeg8() { doTest(false, false); }
  public void testDiamondNeg9() { doTest(false, false); }
  public void testDiamondNeg10() { doTest(false, false); }
  public void testDiamondNeg11() { doTest(false, false); }
  public void testDiamondNeg12() { doTest(false, false); }
  public void testDiamondNeg13() { doTest(false, false); }
  public void testDiamondNeg14() { doTest(false, false); }
  public void testDiamondMisc() { doTest(false, false); }
  public void testNestedDiamond() { doTest(false, false); }
  public void testMultipleConstructors() { doTest(false, false); }
  public void testHighlightInaccessibleFromClassModifierList() { doTest(false, false); }
  public void testInnerInTypeArguments() { doTest(false, false); }
  public void testRawSubstitutor() { doTest(false, false); }
  public void testIncompleteDiamonds() { doTest(false, false); }
  public void testResolveConflictDiamonds() { doTest(false, false); }

  public void testDynamicallyAddIgnoredAnnotations() {
    ExtensionPoint<EntryPoint> point = Extensions.getRootArea().getExtensionPoint(ToolExtensionPoints.DEAD_CODE_TOOL);
    EntryPoint extension = new EntryPoint() {
      @NotNull @Override public String getDisplayName() { return "duh"; }
      @Override public boolean isEntryPoint(@NotNull RefElement refElement, @NotNull PsiElement psiElement) { return false; }
      @Override public boolean isEntryPoint(@NotNull PsiElement psiElement) { return false; }
      @Override public boolean isSelected() { return false; }
      @Override public void setSelected(boolean selected) { }
      @Override public void readExternal(Element element) { }
      @Override public void writeExternal(Element element) { }
      @Override public String[] getIgnoreAnnotations() { return new String[]{"MyAnno"}; }
    };

    enableInspectionTool(new UnusedDeclarationInspectionBase(true));

    doTest(true, false);
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEquals(2, infos.size()); // unused class and unused method

    try {
      point.registerExtension(extension);

      infos = doHighlighting(HighlightSeverity.WARNING);
      HighlightInfo info = assertOneElement(infos);
      assertEquals("Class 'WithMain' is never used", info.getDescription());
    }
    finally {
      point.unregisterExtension(extension);
    }
  }

  public void testNumericLiterals() { doTest(false, false); }
  public void testMultiCatch() { doTest(false, false); }
  public void testTryWithResources() { doTest(false, false); }
  public void testTryWithResourcesWarn() { doTest(true, false, new DefUseInspection()); }
  public void testSafeVarargsApplicability() { doTest(true, false); }
  public void testUncheckedGenericsArrayCreation() { doTest(true, false); }
  public void testGenericsArrayCreation() { doTest(false, false); }
  public void testPreciseRethrow() { doTest(false, false); }
  public void testPreciseRethrowNonAssignableToException() { doTest(false, false); }
  public void testPreciseRethrowOfOneExceptionInTheBlock() { doTest(false, false); }
  public void testImprovedCatchAnalysis() { doTest(true, false); }
  public void testPolymorphicTypeCast() { doTest(true, false); }
  public void testTypeCastInInstanceof() { doTest(true, false); }
  public void testErasureClashConfusion() { doTest(true, false, new UnusedDeclarationInspectionBase(true)); }
  public void testUnused() { doTest(true, false, new UnusedDeclarationInspectionBase(true)); }
  public void testSuperBound() { doTest(false, false); }
  public void testExtendsBound() { doTest(false, false); }
  public void testIDEA84533() { doTest(false, false); }
  public void testClassLiteral() { doTest(false, false); }
  public void testUncheckedWarning() { doTest(true, false); }
  public void testUncheckedWarningIDEA59290() { doTest(true, false); }
  public void testUncheckedWarningIDEA70620() { doTest(true, false); }
  public void testUncheckedWarningIDEA60166() { doTest(true, false); }
  public void testUncheckedWarningIDEA21432() { doTest(true, false); }
  public void testUncheckedWarningIDEA99357() { doTest(true, false); }
  public void testUncheckedWarningIDEA26738() { doTest(true, false); }
  public void testUncheckedWarningIDEA99536() { doTest(true, false); }
  public void testEnclosingInstance() { doTest(false, false); }
  public void testIDEA122519EnclosingInstance() { doTest(false, false); }
  public void testWrongArgsAndUnknownTypeParams() { doTest(false, false); }
  public void testAmbiguousMethodCallIDEA97983() { doTest(false, false); }
  public void testAmbiguousMethodCallIDEA100314() { doTest(false, false); }
  public void testAmbiguousMethodCallIDEA67668() { doTest(false, false); }
  public void testAmbiguousMethodCallIDEA67671() { doTest(false, false); }
  public void testAmbiguousMethodCallIDEA67669() { doTest(false, false); }
  public void testInstanceMemberNotAccessibleInStaticContext() { doTest(false, false); }
  public void testRejectedTypeParamsForConstructor() { doTest(false, false); }
  public void testAnnotationArgs() { doTest(false, false);}
  public void testIDEA70890() { doTest(false, false); }
  public void testIDEA63731() { doTest(false, false); }
  public void testIDEA62056() { doTest(false, false); }
  public void testIDEA78916() { doTest(false, false); }
  public void testIDEA111420() { doTest(false, false); }
  public void testIDEA111450() { doTest(true, false); }
  public void testExternalizable() { doTest(true, false); }
  public void testAccessToStaticMethodsFromInterfaces() { doTest(true, false); }
  public void testUncheckedExtendedWarnings() { doTest(true, false); }
  public void testInaccessibleInferredTypeForVarargsArgument() { doTest(false, false);}
  public void testRuntimeClassCast() { doTest(true, false);}
  public void testTryWithResourcesWithMultipleCloseInterfaces() { doTest(false, false);}
  public void testIDEA138978() { doTest(false, false); }
  public void testIntersectionTypeCast() { doTest(false, false); }
  public void testUsedMethodCalledViaReflectionInTheSameFile() { doTest(true, false); }
  public void testCatchSubclassOfThrownException() { doTest(true, false); }

  public void testArrayInitializerTypeCheckVariableType() { doTest(false, false);}

  public void testJavaUtilCollections_NoVerify() throws Exception {
    PsiClass collectionsClass = getJavaFacade().findClass("java.util.Collections", GlobalSearchScope.moduleWithLibrariesScope(getModule()));
    assertNotNull(collectionsClass);
    collectionsClass = (PsiClass)collectionsClass.getNavigationElement();
    assertTrue(!(collectionsClass instanceof PsiCompiledElement));
    final String text = collectionsClass.getContainingFile().getText();
    configureFromFileText("Collections.java", StringUtil.convertLineSeparators(StringUtil.replace(text, "package java.util;", "package java.utilx; import java.util.*;")));
    doTestConfiguredFile(false, false, null);
  }
}
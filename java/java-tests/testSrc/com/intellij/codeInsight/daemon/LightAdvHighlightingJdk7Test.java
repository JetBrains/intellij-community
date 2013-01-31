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

import com.intellij.ExtensionPoints;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.codeInspection.deprecation.DeprecatedDefenderSyntaxInspection;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.codeInspection.reference.EntryPoint;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * This class is for "lightweight" tests only, i.e. those which can run inside default light project set up
 * For "heavyweight" tests use AdvHighlightingTest
 */
public class LightAdvHighlightingJdk7Test extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting7";

  private void doTest(boolean checkWarnings, boolean checkInfos, Class<?>... classes) {
    enableInspectionTools(classes);
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  private void doTest(boolean checkWarnings, boolean checkWeakWarnings, boolean checkInfos, Class<?>... classes) {
    enableInspectionTools(classes);
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkWeakWarnings, checkInfos);
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new UnusedSymbolLocalInspection(),
      new UncheckedWarningLocalInspection(),
      new JavacQuirksInspection(),
      new RedundantCastInspection()
    };
  }

  public void testAllJava15Features() throws Exception { doTest(false, false); }
  public void testEnumSyntheticMethods() throws Exception { doTest(false, false); }
  public void testDuplicateAnnotations() throws Exception { doTest(false, false); }
  public void testSwitchByString() throws Exception { doTest(false, false); }
  public void testSwitchByInaccessibleEnum() throws Exception { doTest(false, false); }
  public void testDiamondPos1() throws Exception { doTest(false, false); }
  public void testDiamondPos2() throws Exception { doTest(false, false); }
  public void testDiamondPos3() throws Exception { doTest(false, false); }
  public void testDiamondPos4() throws Exception { doTest(false, false); }
  public void testDiamondPos5() throws Exception { doTest(false, false); }
  public void testDiamondPos6() throws Exception { doTest(false, false); }
  public void testDiamondPos7() throws Exception { doTest(false, false); }
  public void testDiamondNeg15() throws Exception { doTest(false, false); }
  public void testDiamondPos9() throws Exception { doTest(false, false); }
  public void testDiamondNeg1() throws Exception { doTest(false, false); }
  public void testDiamondNeg2() throws Exception { doTest(false, false); }
  public void testDiamondNeg3() throws Exception { doTest(false, false); }
  public void testDiamondNeg4() throws Exception { doTest(false, false); }
  public void testDiamondNeg5() throws Exception { doTest(false, false); }
  public void testDiamondNeg6() throws Exception { doTest(false, false); }
  public void testDiamondNeg7() throws Exception { doTest(false, false); }
  public void testDiamondNeg8() throws Exception { doTest(false, false); }
  public void testDiamondNeg9() throws Exception { doTest(false, false); }
  public void testDiamondNeg10() throws Exception { doTest(false, false); }
  public void testDiamondNeg11() throws Exception { doTest(false, false); }
  public void testDiamondNeg12() throws Exception { doTest(false, false); }
  public void testDiamondNeg13() throws Exception { doTest(false, false); }
  public void testDiamondNeg14() throws Exception { doTest(false, false); }
  public void testDiamondMisc() throws Exception { setLanguageLevel(LanguageLevel.JDK_1_7); doTest(false, false); }
  public void testHighlightInaccessibleFromClassModifierList() throws Exception { doTest(false, false); }
  public void testInnerInTypeArguments() throws Exception { doTest(false, false); }

  public void testDynamicallyAddIgnoredAnnotations() throws Exception {
    ExtensionPoint<EntryPoint> point = Extensions.getRootArea().getExtensionPoint(ExtensionPoints.DEAD_CODE_TOOL);
    EntryPoint extension = new EntryPoint() {
      @NotNull @Override public String getDisplayName() { return "duh"; }
      @Override public boolean isEntryPoint(RefElement refElement, PsiElement psiElement) { return false; }
      @Override public boolean isEntryPoint(PsiElement psiElement) { return false; }
      @Override public boolean isSelected() { return false; }
      @Override public void setSelected(boolean selected) { }
      @Override public void readExternal(Element element) { }
      @Override public void writeExternal(Element element) { }
      @Override public String[] getIgnoreAnnotations() { return new String[]{"MyAnno"}; }
    };

    UnusedDeclarationInspection deadCodeInspection = new UnusedDeclarationInspection();
    enableInspectionTool(deadCodeInspection);

    doTest(true, false);
    List<HighlightInfo> infos = doHighlighting(HighlightSeverity.WARNING);
    assertEquals(2, infos.size()); // unused class and unused method

    try {
      point.registerExtension(extension);

      infos = doHighlighting(HighlightSeverity.WARNING);
      HighlightInfo info = assertOneElement(infos);
      assertEquals("Class 'WithMain' is never used", info.description);
    }
    finally {
      point.unregisterExtension(extension);
    }
  }

  public void testJavacQuirks() throws Exception { setLanguageLevel(LanguageLevel.JDK_1_6); doTest(true, false); }
  public void testNumericLiterals() throws Exception { doTest(false, false); }
  public void testMultiCatch() throws Exception { doTest(false, false); }
  public void testTryWithResources() throws Exception { doTest(false, false); }
  public void testTryWithResourcesWarn() throws Exception { doTest(true, false, DefUseInspection.class); }
  public void testSafeVarargsApplicability() throws Exception { doTest(true, false); }
  public void testUncheckedGenericsArrayCreation() throws Exception { doTest(true, false); }
  public void testGenericsArrayCreation() throws Exception { doTest(false, false); }
  public void testPreciseRethrow() throws Exception { doTest(false, false); }
  public void testImprovedCatchAnalysis() throws Exception { doTest(true, false); }
  public void testPolymorphicTypeCast() throws Exception { doTest(true, false); }
  public void testErasureClashConfusion() throws Exception { doTest(true, false, UnusedDeclarationInspection.class); }
  public void testUnused() throws Exception { doTest(true, false, UnusedDeclarationInspection.class); }
  public void testSuperBound() throws Exception { doTest(false, false); }
  public void testExtendsBound() throws Exception { doTest(false, false); }
  public void testIDEA84533() throws Exception { doTest(false, false); }
  public void testClassLiteral() throws Exception { doTest(false, false); }
  public void testExtensionMethods() throws Exception { doTest(false, false); }
  public void testExtensionMethodSyntax() throws Exception { doTest(true, false, DeprecatedDefenderSyntaxInspection.class); }
  public void testMethodReferences() throws Exception { doTest(false, true, false); }
  public void testUsedMethodsByMethodReferences() throws Exception { doTest(true, true, false); }
  public void testLambdaExpressions() throws Exception { doTest(false, true, false); }
  public void testJava7CastConventions() throws Exception { doTest(false, true, false); }
  public void testUncheckedWarning() throws Exception { doTest(true, false); }
  public void testUncheckedWarningIDEA59290() throws Exception { doTest(true, false); }
  public void testUncheckedWarningIDEA70620() throws Exception { doTest(true, false); }
  public void testUncheckedWarningIDEA60166() throws Exception { doTest(true, false); }
  public void testUncheckedWarningIDEA21432() throws Exception { doTest(true, false); }
  public void testUncheckedWarningIDEA99357() throws Exception { doTest(true, false); }
  public void testUncheckedWarningIDEA26738() throws Exception { doTest(true, false); }
  public void testUncheckedWarningIDEA99536() throws Exception { doTest(true, false); }
  public void testDefaultMethodVisibility() throws Exception { doTest(true, false); }
  public void testInheritUnrelatedDefaults() throws Exception { doTest(true, false); }
  public void testNotInheritFromUnrelatedDefault() throws Exception { doTest(true, false); }
  public void testEnclosingInstance() throws Exception { doTest(false, false); }
  public void testWrongArgsAndUnknownTypeParams() throws Exception { doTest(false, false); }
  public void testAmbiguousMethodCallIDEA97983() throws Exception { doTest(false, false); }
}

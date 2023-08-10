// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

public class LightAdvHighlightingJdk8Test extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting8";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTools(new JavacQuirksInspection());
    setLanguageLevel(LanguageLevel.JDK_1_8);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
  }

  private void doTest(boolean warnings, boolean weakWarnings) {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", warnings, weakWarnings, false);
  }

  public void testFinalVariableMightNotHaveBeenInitializedInsideLambda() { doTest(true, false); }
  public void testStrictfpInsideInterface() { doTest(true, false); }
  public void testMethodReferences() { doTest(false, true); }
  public void testUsedMethodsByMethodReferences() { enableInspectionTool(new UnusedDeclarationInspection()); doTest(true, true); }
  public void testUncheckedWarningForPolyConditional() { enableInspectionTool(new UncheckedWarningLocalInspection()); doTest(true, true); }
  public void testUncheckedWarningOnQualifierWithTypeParameterType() { enableInspectionTool(new UncheckedWarningLocalInspection()); doTest(true, true); }
  public void testLambdaExpressions() { doTest(false, true); }
  public void testUnsupportedFeatures() { doTest(false, false); }
  public void testModulesNotSupported() { doTest(false, false); }

  public void testTooManyVarargsPolyArguments() {
    doTest(true, false);
  }
  public void testNoArraySuperType() { doTest(true, true);}
  public void testCaptureItself() { doTest(true, true); }
  public void testNestedConditionalWithOverloads() { doTest(true, true); }
  public void testConditionalWithCompoundAssignment() { doTest(true, true); }
  public void testDeprecatedFunctionalInterface() {
    enableInspectionTool(new DeprecationInspection());
    doTest(true, true); 
  }
  public void testCyclicInheritanceOfTypeAnnotation() { doTest(true, true); }
}
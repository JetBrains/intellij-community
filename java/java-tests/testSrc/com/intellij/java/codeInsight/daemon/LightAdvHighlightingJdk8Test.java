// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.idea.TestFor;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

import java.util.List;

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
  public void testWrongNumberOfArguments() { doTest(false, false); }

  public void testTooManyVarargsPolyArguments() {
    doTest(true, false);
  }
  public void testNoArraySuperType() { doTest(true, true);}
  public void testNoAnnotationAfterArrayBrackets() { doTest(true, true);}
  public void testCaptureItself() { doTest(true, true); }
  public void testNestedConditionalWithOverloads() { doTest(true, true); }
  public void testConditionalWithCompoundAssignment() { doTest(true, true); }
  public void testDeprecatedFunctionalInterface() {
    enableInspectionTool(new DeprecationInspection());
    doTest(true, true); 
  }
  public void testCyclicInheritanceOfTypeAnnotation() { doTest(true, true); }
  public void testReferenceToPrivateClass() { doTest(true, true); }
  public void testReferenceToPrivateClassOuterThis() { doTest(true, true); }

  @TestFor(issues = "IDEA-389954")
  public void testIncompatibleTypeTooltipWithFewerProvidedTypeArguments() {
    configureFromFileText("a.java", """
      import java.util.List;
      import java.util.Map;
      class Test {
        void smth(List<String> list) { othr(list); }
        void othr(Map<String, Object> map) {}
      }
      """);
    List<HighlightInfo> errors = highlightErrors();
    HighlightInfo info = assertOneElement(errors);
    String tooltip = info.getToolTip();
    assertNotNull(tooltip);
    // The provided type "List<String>" must close its single type argument with ">", not a dangling ",".
    // In the buggy output no "&lt;String&gt;" exists anywhere (Map renders "&lt;String," + "Object&gt;",
    // List renders "&lt;String,"); the fix makes List render "&lt;String&gt;".
    assertTrue(tooltip, tooltip.contains("&lt;<span style=\"color: #333333\">String</span>&gt;"));
  }
}
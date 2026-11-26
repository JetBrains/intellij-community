// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.idea.TestFor;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class LightAdvHighlightingJdk8Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting8";

  @BeforeEach
  void setUp() {
    daemonFixture.get().getFixture().enableInspections(new JavacQuirksInspection());
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_8);
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, myFixture.getModule(), myFixture.getTestRootDisposable());
  }

  private void doTest(boolean warnings, boolean weakWarnings) {
    daemonFixture.get().doTest(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java", warnings, weakWarnings, false);
  }

  @Test public void testFinalVariableMightNotHaveBeenInitializedInsideLambda() { doTest(true, false); }
  @Test public void testStrictfpInsideInterface() { doTest(true, false); }
  @Test public void testMethodReferences() { doTest(false, true); }
  @Test
  public void testUsedMethodsByMethodReferences() {
    daemonFixture.get().getFixture().enableInspections(new UnusedDeclarationInspection());
    doTest(true, true);
  }
  @Test
  public void testUncheckedWarningForPolyConditional() {
    daemonFixture.get().getFixture().enableInspections(new UncheckedWarningLocalInspection());
    doTest(true, true);
  }
  @Test
  public void testUncheckedWarningOnQualifierWithTypeParameterType() {
    daemonFixture.get().getFixture().enableInspections(new UncheckedWarningLocalInspection());
    doTest(true, true);
  }
  @Test public void testLambdaExpressions() { doTest(false, true); }
  @Test public void testUnsupportedFeatures() { doTest(false, false); }
  @Test public void testModulesNotSupported() { doTest(false, false); }
  @Test public void testWrongNumberOfArguments() { doTest(false, false); }

  @Test
  public void testTooManyVarargsPolyArguments() {
    doTest(true, false);
  }
  @Test public void testNoArraySuperType() { doTest(true, true);}
  @Test public void testNoAnnotationAfterArrayBrackets() { doTest(true, true);}
  @Test public void testCaptureItself() { doTest(true, true); }
  @Test public void testNestedConditionalWithOverloads() { doTest(true, true); }
  @Test public void testConditionalWithCompoundAssignment() { doTest(true, true); }
  @Test
  public void testDeprecatedFunctionalInterface() {
    daemonFixture.get().getFixture().enableInspections(new DeprecationInspection());
    doTest(true, true); 
  }
  @Test public void testCyclicInheritanceOfTypeAnnotation() { doTest(true, true); }
  @Test public void testReferenceToPrivateClass() { doTest(true, true); }
  @Test public void testReferenceToPrivateClassOuterThis() { doTest(true, true); }

  @TestFor(issues = "IDEA-389954")
  @Test
  public void testIncompatibleTypeTooltipWithFewerProvidedTypeArguments() {
    daemonFixture.get().configureFromFileText("a.java", """
      import java.util.List;
      import java.util.Map;
      class Test {
        void smth(List<String> list) { othr(list); }
        void othr(Map<String, Object> map) {}
      }
      """);
    List<HighlightInfo> errors = daemonFixture.get().highlightErrors();
    assertEquals(1, errors.size(), errors.toString());
    HighlightInfo info = errors.getFirst();
    String tooltip = info.getToolTip();
    assertNotNull(tooltip);
    // The provided type "List<String>" must close its single type argument with ">", not a dangling ",".
    // In the buggy output no "&lt;String&gt;" exists anywhere (Map renders "&lt;String," + "Object&gt;",
    // List renders "&lt;String,"); the fix makes List render "&lt;String&gt;".
    assertTrue(tooltip.contains("&lt;<span style=\"color: #333333\">String</span>&gt;"), tooltip);
  }
}
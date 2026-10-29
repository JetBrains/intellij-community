// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

/**
 * Highlighting tests for the diamond form of a class instance creation expression (JLS 15.9).
 * The expected errors agree with javac.
 */
public class LightDiamondInferenceHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/diamondInference";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.JDK_21);
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk21();
  }

  private void doTest(boolean checkWarnings, InspectionProfileEntry... inspections) {
    enableInspectionTools(inspections);
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, false);
  }

  public void testAssignmentAndStandaloneContexts() { doTest(false); }
  public void testInvocationContext() { doTest(false); }
  public void testConstructorSelection() { doTest(false); }
  public void testInvalidDiamondUsages() { doTest(false); }
  public void testInnerAndNestedClasses() { doTest(false); }
  public void testAnonymousClasses() { doTest(false); }
  public void testThrownExceptions() { doTest(false); }
  public void testSyntaxErrors() { doTest(false); }
  public void testUncheckedInference() { doTest(true, new UncheckedWarningLocalInspection()); }

  public void testDiamondJava6() {
    setLanguageLevel(LanguageLevel.JDK_1_6);
    doTest(false);
  }

  public void testDiamondJava7() {
    setLanguageLevel(LanguageLevel.JDK_1_7);
    doTest(false);
  }

  public void testDiamondJava8() {
    setLanguageLevel(LanguageLevel.JDK_1_8);
    doTest(false);
  }
}

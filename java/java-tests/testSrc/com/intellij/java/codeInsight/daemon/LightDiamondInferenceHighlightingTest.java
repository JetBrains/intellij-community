// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaDescriptorOfTestName;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

/**
 * Highlighting tests for the diamond form of a class instance creation expression (JLS 15.9).
 * The expected errors agree with javac.
 */
@TestApplication
public class LightDiamondInferenceHighlightingTest {
  private static final LightProjectDescriptor DESCRIPTOR =
    lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk21());
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lightDaemonAnalyzerFixture(projectFixture, lightJavaDescriptorOfTestName(DESCRIPTOR));

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/diamondInference";

  @BeforeEach
  void setUp() {
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_21);
  }

  private void doTest(boolean checkWarnings, InspectionProfileEntry... inspections) {
    daemonFixture.get().getFixture().enableInspections(inspections);
    daemonFixture.get().doTest(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java", checkWarnings, false);
  }

  @Test public void testAssignmentAndStandaloneContexts() { doTest(false); }
  @Test public void testInvocationContext() { doTest(false); }
  @Test public void testConstructorSelection() { doTest(false); }
  @Test public void testInvalidDiamondUsages() { doTest(false); }
  @Test public void testInnerAndNestedClasses() { doTest(false); }
  @Test public void testAnonymousClasses() { doTest(false); }
  @Test public void testThrownExceptions() { doTest(false); }
  @Test public void testSyntaxErrors() { doTest(false); }
  @Test public void testUncheckedInference() { doTest(true, new UncheckedWarningLocalInspection()); }

  @Test
  public void testDiamondJava6() {
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_6);
    doTest(false);
  }

  @Test
  public void testDiamondJava7() {
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_7);
    doTest(false);
  }

  @Test
  public void testDiamondJava8() {
    daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_8);
    doTest(false);
  }
}

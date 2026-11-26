// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class AnnotationsHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/annotations";

  @Test public void testWrongPlace() { daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_7); doTest(); }
  @Test public void testNotValueNameOmitted() { doTest(); }
  @Test public void testCannotFindMethod() { doTest(); }
  @Test public void testIncompatibleType1() { doTest(); }
  @Test public void testIncompatibleType2() { doTest(); }
  @Test public void testIncompatibleType3() { doTest(); }
  @Test public void testIncompatibleType4() { doTest(); }
  @Test public void testIncompatibleType5() { doTest(); }
  @Test public void testIncompatibleWithUnresolvedAttribute() { doTest(); }
  @Test public void testMissingAttribute() { doTest(); }
  @Test public void testDuplicateAnnotation() { daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_7); doTest(); }
  @Test public void testNonConstantInitializer() { doTest(); }
  @Test public void testInvalidType() { doTest(); }
  @Test public void testInapplicable() { doTest(); }
  @Test public void testFalseTypeUse() { doTest(); }
  @Test public void testDuplicateAttribute() { doTest(); }
  @Test public void testDuplicateTarget() { doTest(); }
  @Test public void testPingPongAnnotationTypesDependencies() { doTest(); }
  @Test public void testClashMethods() { doTest(); }
  @Test public void testDupMethods() { doTest(); }
  @Test public void testPrivateInaccessibleConstant() { doTest(); }
  @Test public void testInvalidPackageAnnotationTarget() { daemonFixture.get().doTest(BASE_PATH + "/package-info.java", false, false); }
  @Test public void testPackageAnnotationNotInPackageInfo() { doTest(); }
  @Test public void testTypeAnnotations() { doTest(); }
  @Test public void testTypeAnnotationsWithCStyleArrays() { doTest(); }
  @Test public void testTypeAnnotationsWithVar() { doTest(); }
  @Test public void testRepeatable() { doTest(); }
  @Test public void testEnumValues() { doTest(); }
  @Test public void testReceiverParameters() { doTest(); }
  @Test public void testAnnotationOverIncompleteCode() { doTest(); }
  @Test public void testDeclarations() { doTest(); }
  @Test public void testErrorRecovery() { doTest(); }
  @Test public void testWrongTargetTypeUse() { doTest(); }
  @Test public void testIncomplete() { doTest(); }

  private void doTest() { doTest(daemonFixture.get().getFixture().getTestName(true) + ".java"); }
  private void doTest(String name) { daemonFixture.get().doTest(BASE_PATH + "/" + name, false, false); }
}
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.pom.java.LanguageLevel;

public class AnnotationsHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/annotations";

  public void testWrongPlace() { setLanguageLevel(LanguageLevel.JDK_1_7); doTest(); }
  public void testNotValueNameOmitted() { doTest(); }
  public void testCannotFindMethod() { doTest(); }
  public void testIncompatibleType1() { doTest(); }
  public void testIncompatibleType2() { doTest(); }
  public void testIncompatibleType3() { doTest(); }
  public void testIncompatibleType4() { doTest(); }
  public void testIncompatibleType5() { doTest(); }
  public void testIncompatibleWithUnresolvedAttribute() { doTest(); }
  public void testMissingAttribute() { doTest(); }
  public void testDuplicateAnnotation() { setLanguageLevel(LanguageLevel.JDK_1_7); doTest(); }
  public void testNonConstantInitializer() { doTest(); }
  public void testInvalidType() { doTest(); }
  public void testInapplicable() { doTest(); }
  public void testFalseTypeUse() { doTest(); }
  public void testDuplicateAttribute() { doTest(); }
  public void testDuplicateTarget() { doTest(); }
  public void testPingPongAnnotationTypesDependencies() { doTest(); }
  public void testClashMethods() { doTest(); }
  public void testDupMethods() { doTest(); }
  public void testPrivateInaccessibleConstant() { doTest(); }
  public void testInvalidPackageAnnotationTarget() { doTest(BASE_PATH + "/package-info.java", false, false); }
  public void testPackageAnnotationNotInPackageInfo() { doTest(); }
  public void testTypeAnnotations() { doTest(); }
  public void testTypeAnnotationsWithCStyleArrays() { doTest(); }
  public void testTypeAnnotationsWithVar() { doTest(); }
  public void testRepeatable() { doTest(); }
  public void testEnumValues() { doTest(); }
  public void testReceiverParameters() { doTest(); }
  public void testAnnotationOverIncompleteCode() { doTest(); }
  public void testDeclarations() { doTest(); }
  public void testErrorRecovery() { doTest(); }
  public void testWrongTargetTypeUse() { doTest(); }
  public void testIncomplete() { doTest(); }

  private void doTest() { doTest(getTestName(true) + ".java"); }
  private void doTest(String name) { doTest(BASE_PATH + "/" + name, false, false); }
}
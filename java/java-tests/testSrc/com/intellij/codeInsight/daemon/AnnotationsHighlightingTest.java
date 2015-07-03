/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.pom.java.LanguageLevel;

/**
 * @author ven
 */
public class AnnotationsHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/annotations";

  public void testWrongPlace() { doTest(); }
  public void testNotValueNameOmitted() { doTest(); }
  public void testCannotFindMethod() { doTest(); }
  public void testIncompatibleType1() { doTest(); }
  public void testIncompatibleType2() { doTest(); }
  public void testIncompatibleType3() { doTest(); }
  public void testIncompatibleType4() { doTest(); }
  public void testIncompatibleType5() { doTest(); }
  public void testMissingAttribute() { doTest(); }
  public void testDuplicateAnnotation() { doTest(); }
  public void testNonConstantInitializer() { doTest(); }
  public void testInvalidType() { doTest(); }
  public void testInapplicable() { doTest(); }
  public void testDuplicateAttribute() { doTest(); }
  public void testDuplicateTarget() { doTest(); }
  public void testPingPongAnnotationTypesDependencies() { doTest(); }
  public void testClashMethods() { doTest(); }
  public void testDupMethods() { doTest(); }
  public void testPrivateInaccessibleConstant() { doTest(); }
  public void testInvalidPackageAnnotationTarget() { doTest(BASE_PATH + "/package-info.java", false, false); }
  public void testPackageAnnotationNotInPackageInfo() { doTest(); }

  public void testTypeAnnotations() { doTest8(); }
  public void testRepeatable() { doTest8(); }
  public void testEnumValues() { doTest8(); }
  public void testReceiverParameters() { doTest8(); }

  private void doTest() {
    setLanguageLevel(LanguageLevel.JDK_1_7);
    doTest(BASE_PATH + "/" + getTestName(true) + ".java", false, false);
  }

  private void doTest8() {
    setLanguageLevel(LanguageLevel.JDK_1_8);
    doTest(BASE_PATH + "/" + getTestName(true) + ".java", false, false);
  }
}

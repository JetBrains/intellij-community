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

  public void testWrongPlace() { doTest(false); }
  public void testNotValueNameOmitted() { doTest(false); }
  public void testCannotFindMethod() { doTest(false); }
  public void testIncompatibleType1() { doTest(false); }
  public void testIncompatibleType2() { doTest(false); }
  public void testIncompatibleType3() { doTest(false); }
  public void testIncompatibleType4() { doTest(false); }
  public void testIncompatibleType5() { doTest(false); }
  public void testMissingAttribute() { doTest(false); }
  public void testDuplicateAnnotation() { doTest(false); }
  public void testNonConstantInitializer() { doTest(false); }
  public void testInvalidType() { doTest(false); }
  public void testInapplicable() { doTest(false); }
  public void testDuplicateAttribute() { doTest(false); }
  public void testDuplicateTarget() { doTest(false); }
  public void testPingPongAnnotationTypesDependencies() { doTest(false); }
  public void testClashMethods() { doTest(false); }
  public void testDupMethods() { doTest(false); }
  public void testPrivateInaccessibleConstant() { doTest(false); }

  public void testInvalidPackageAnnotationTarget() { doTest(BASE_PATH + "/" + getTestName(true) + "/package-info.java", false, false); }
  public void testPackageAnnotationNotInPackageInfo() { doTest(BASE_PATH + "/" + getTestName(true) + "/notPackageInfo.java", false, false); }

  public void testTypeAnnotations() { doTest8(false); }
  public void testRepeatable() { doTest8(false); }
  public void testEnumValues() { doTest8(false); }
  public void testReceiverParameters() { doTest8(false); }

  private void doTest(boolean checkWarnings) {
    setLanguageLevel(LanguageLevel.JDK_1_7);
    doTest(BASE_PATH + "/" + getTestName(true) + ".java", checkWarnings, false);
  }

  private void doTest8(boolean checkWarnings) {
    setLanguageLevel(LanguageLevel.JDK_1_8);
    doTest(BASE_PATH + "/" + getTestName(true) + ".java", checkWarnings, false);
  }
}

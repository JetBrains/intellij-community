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

import org.jetbrains.annotations.NonNls;

/**
 * @author ven
 */
public class AnnotationsHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/annotations";

  private void doTest(boolean checkWarnings) {
    doTest(BASE_PATH + "/" + getTestName(true) + ".java", checkWarnings, false);
  }

  public void testNotValueNameOmitted() { doTest(false); }
  public void testCannotFindMethod() { doTest(false); }
  public void testIncompatibleType1() { doTest(false); }
  public void testIncompatibleType2() { doTest(false); }
  public void testIncompatibleType3() { doTest(false); }
  public void testIncompatibleType4() { doTest(false); }
  public void testMissingAttribute() { doTest(false); }
  public void testDuplicateAnnotation() { doTest(false); }
  public void testNonConstantInitializer() { doTest(false); }
  public void testInvalidType() { doTest(false); }
  public void testInapplicable() { doTest(false); }
  public void testDuplicateAttribute() { doTest(false); }
  public void testDuplicateTarget() { doTest(false); }
  public void testTypeAnnotations() { doTest(false); }
  public void testInvalidPackageAnnotationTarget() { doTest(BASE_PATH + "/" + getTestName(true) + "/package-info.java", false, false); }
  public void testPackageAnnotationNotInPackageInfo() { doTest(BASE_PATH + "/" + getTestName(true) + "/notPackageInfo.java", false, false); }
}

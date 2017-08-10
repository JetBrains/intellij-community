/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
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

  public void testUnderscore() { doTest(true, false); }
  public void testFinalVariableMightNotHaveBeenInitializedInsideLambda() { doTest(true, false); }
  public void testStrictfpInsideInterface() { doTest(true, false); }
  public void testMethodReferences() { doTest(false, true); }
  public void testUsedMethodsByMethodReferences() { enableInspectionTool(new UnusedDeclarationInspection()); doTest(true, true); }
  public void testLambdaExpressions() { doTest(false, true); }
  public void testUnsupportedFeatures() { doTest(false, false); }
  public void testModulesNotSupported() { doTest(false, false); }

  public void testTooManyVarargsPolyArguments() {
    doTest(true, false);
  }
}
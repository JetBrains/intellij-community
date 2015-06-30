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
package com.intellij.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;

public class LightAdvHighlightingJdk8Test extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/advHighlighting";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTools(new JavacQuirksInspection());
    setLanguageLevel(LanguageLevel.JDK_1_8);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }

  private void doTest() {
    doTest(true, false, false);
  }

  private void doTest(boolean warnings, boolean weakWarnings, boolean infos, InspectionProfileEntry... inspections) {
    enableInspectionTools(inspections);
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", warnings, weakWarnings, infos);
  }

  public void testUnderscore() { doTest(); }
  public void testFinalVariableMightNotHaveBeenInitializedInsideLambda() { doTest(); }
  public void testStrictfpInsideInterface() { doTest(); }
  public void testMethodReferences() { doTest(false, true, false); }
  public void testUsedMethodsByMethodReferences() { doTest(true, true, false, new UnusedDeclarationInspection()); }
  public void testLambdaExpressions() { doTest(false, true, false); }
}

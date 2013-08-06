/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * This class is for "lightweight" tests only, i.e. those which can run inside default light project set up.
 * For "heavyweight" tests use AdvHighlightingTest.
 */
public class LightAdvHighlightingJdk6Test extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting6";

  private void doTest(boolean checkWarnings, boolean checkInfos, Class<?>... classes) {
    setLanguageLevel(LanguageLevel.JDK_1_6);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_6, getModule(), myTestRootDisposable);
    enableInspectionTools(classes);
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, checkInfos);
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new UnusedSymbolLocalInspection(),
      new UncheckedWarningLocalInspection(),
      new JavacQuirksInspection(),
      new RedundantCastInspection()
    };
  }

  public void testJavacQuirks() { setLanguageLevel(LanguageLevel.JDK_1_6); doTest(true, false); }
  public void testMethodReturnTypeSubstitutability() { setLanguageLevel(LanguageLevel.JDK_1_6); doTest(true, false); }
  public void testIDEADEV11877() throws Exception { setLanguageLevel(LanguageLevel.JDK_1_6); doTest(false, false); }
  public void testIDEA108285() throws Exception { setLanguageLevel(LanguageLevel.JDK_1_6); doTest(false, false); }
  public void testClassObjectAccessibility() throws Exception { setLanguageLevel(LanguageLevel.JDK_1_6); doTest(false, false); }
  public void testRedundantCastInConditionalExpression() throws Exception { setLanguageLevel(LanguageLevel.JDK_1_6); doTest(true, false); }
  public void testJava5CastConventions() { setLanguageLevel(LanguageLevel.JDK_1_5); doTest(true, false); }
  public void testUnhandledExceptions() { doTest(true, false); }
}

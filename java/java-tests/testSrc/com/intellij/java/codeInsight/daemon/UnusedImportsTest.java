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

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class UnusedImportsTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/unusedImports/";

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + BASE_PATH;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnusedImportInspection());
  }

  public void test1() { doTest(); }
  public void test2() { doTest(); }

  public void testWithHighlightingOff() {
    myFixture.configureByFile(getTestName(false) + ".java");
    final PsiFile file = getFile();
    final HighlightingSettingsPerFile settingsPerFile = HighlightingSettingsPerFile.getInstance(getProject());
    final FileHighlightingSetting oldSetting = settingsPerFile.getHighlightingSettingForRoot(file);
    try {
      settingsPerFile.setHighlightingSettingForRoot(file, FileHighlightingSetting.NONE);
      myFixture.checkHighlighting(true, false, false);
    }
    finally {
      settingsPerFile.setHighlightingSettingForRoot(file, oldSetting);
    }
  }

  public void testUnclosed() { doTest(); }

  public void testQualified() { doTest(); }

  public void testInnersOnDemand1() { doTest(); }
  public void testInnersOnDemand2() { doTest(); }
  public void testStaticImportingInner() {
    myFixture.addClass("package package1;\n" +
                       "/** @noinspection ALL*/ public interface c\n" +
                       "{\n" +
                       "    public interface MyInner\n" +
                       "    {\n" +
                       "        int a = 1;\n" +
                       "        int b = 2;\n" +
                       "    }\n" +
                       "}");
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testImportFromSamePackage1() {
    myFixture.addClass("package package1;\n" +
                       "import package1.*;" +
                       "class b {\n" +
                       " a a = null;\n" +
                       "}");
    myFixture.configureByFile("/package1/a.java");
    myFixture.checkHighlighting(true,false, false);
  }
  public void testImportFromSamePackage2() {
    myFixture.addClass("package package1;\n" +
                       "import package1.b;\n" +
                       "class a {\n" +
                       " b b = null;\n" +
                       "}");
    myFixture.configureByFile("/package1/b.java");
    myFixture.checkHighlighting(true,false, false);
  }

  public void testUnresolvedReferencesInsideAmbiguousCallToImportedMethod() {
    myFixture.addClass("package a; public class A {\n" +
                       " public static void foo(Object o) {}\n" +
                       " public static void foo(String s) {}\n" +
                       "}");
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting(true, false, false);
  }
}
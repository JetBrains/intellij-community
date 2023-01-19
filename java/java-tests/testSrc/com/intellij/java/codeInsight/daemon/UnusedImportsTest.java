// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    myFixture.addClass("""
                         package package1;
                         /** @noinspection ALL*/ public interface c
                         {
                             public interface MyInner
                             {
                                 int a = 1;
                                 int b = 2;
                             }
                         }""");
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting(true, false, false);
  }

  public void testImportFromSamePackage1() {
    myFixture.addClass("""
                         package package1;
                         import package1.*;class b {
                          a a = null;
                         }""");
    myFixture.configureByFile("/package1/a.java");
    myFixture.checkHighlighting(true,false, false);
  }
  public void testImportFromSamePackage2() {
    myFixture.addClass("""
                         package package1;
                         import package1.b;
                         class a {
                          b b = null;
                         }""");
    myFixture.configureByFile("/package1/b.java");
    myFixture.checkHighlighting(true,false, false);
  }

  public void testUnresolvedReferencesInsideAmbiguousCallToImportedMethod() {
    myFixture.addClass("""
                         package a; public class A {
                          public static void foo(Object o) {}
                          public static void foo(String s) {}
                         }""");
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting(true, false, false);
  }
}
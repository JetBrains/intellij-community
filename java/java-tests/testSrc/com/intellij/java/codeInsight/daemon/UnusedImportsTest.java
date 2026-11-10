// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class UnusedImportsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/unusedImports/";

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + BASE_PATH);
    myFixture.enableInspections(new UnusedImportInspection());
    myFixture.enableInspections(new UnusedDeclarationInspection());
  }

  @Test
  public void test1() { doTest(); }
  @Test
  public void test2() { doTest(); }

  @Test
  public void testWithHighlightingOff() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    final PsiFile file = myFixture.getFile();
    final HighlightingSettingsPerFile settingsPerFile = HighlightingSettingsPerFile.getInstance(myFixture.getProject());
    final FileHighlightingSetting oldSetting = ReadAction.computeBlocking(() -> settingsPerFile.getHighlightingSettingForRoot(file));
    try {
      settingsPerFile.setHighlightingSettingForRoot(file, FileHighlightingSetting.NONE);
      myFixture.checkHighlighting(true, false, false);
    }
    finally {
      settingsPerFile.setHighlightingSettingForRoot(file, oldSetting);
    }
  }

  @Test
  public void testUnclosed() { doTest(); }

  @Test
  public void testQualified() { doTest(); }

  @Test
  public void testInnersOnDemand1() { doTest(); }
  @Test
  public void testInnersOnDemand2() { doTest(); }
  @Test
  public void testStaticImportingInner() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting(true, false, false);
  }

  @Test
  public void testImportFromSamePackage1() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package package1;
                         import package1.*;class b {
                          a a = null;
                         }""");
    myFixture.configureByFile("/package1/a.java");
    myFixture.checkHighlighting(true,false, false);
  }
  @Test
  public void testImportFromSamePackage2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package package1;
                         import package1.b;
                         class a {
                          b b = null;
                         }""");
    myFixture.configureByFile("/package1/b.java");
    myFixture.checkHighlighting(true,false, false);
  }

  @Test
  public void testUnresolvedReferencesInsideAmbiguousCallToImportedMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package a; public class A {
                          public static void foo(Object o) {}
                          public static void foo(String s) {}
                         }""");
    doTest();
  }

  @Test
  public void testUnresolvedReferenceAsVariableAndClass() {
    doTest();
  }

  @Test
  public void testImportsForUsesAndProvidesInModuleInfo() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package pkg.main.api;
                         public interface Api {}
                         """);
    myFixture.addClass("""
                         package pkg.main.impl;
                         import module my.module;
                         public class Impl implements Api {}
                         """);

    @Language("JAVA")
    String moduleInfo = """
      import pkg.main.api.Api;
      import pkg.main.impl.Impl;
      
      module my.module {
         exports pkg.main.api;
      
         uses Api;
         provides Api with Impl;
      }
      """;
    myFixture.addFileToProject("module-info.java", moduleInfo);
    myFixture.configureByFile("module-info.java");
    myFixture.checkHighlighting(true,false, false);
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.checkHighlighting(true, false, false);
  }
}
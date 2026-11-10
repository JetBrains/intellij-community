// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.ImplicitToExplicitClassBackwardMigrationInspection;
import com.intellij.java.JavaBundle;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class ImplicitToExplicitClassBackwardMigrationInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/implicitToExplicitClassBackwardMigration/");
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package java.io;
                         
                         public final class IO {
                           public static void println(Object obj) {}
                         }
                         """);
    myFixture.enableInspections(new ImplicitToExplicitClassBackwardMigrationInspection());
    myFixture.testHighlighting(true, false, true, "before" + myFixture.getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(
        JavaBundle.message("inspection.implicit.to.explicit.class.backward.migration.fix.name")));
      myFixture.checkResultByFile("after" + myFixture.getTestName(false) + ".java");
    });
  }

  @Test
  public void testSimple() {
    doTest();
  }

  @Test
  public void testSimple2() {
    doTest();
  }

  @Test
  public void testAdjustComments() {
    doTest();
  }

  @Test
  public void testWithPrint() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_23_PREVIEW,
                           () -> doTest());
  }

  @Test
  public void testSimpleModuleImport() {
    doTest();
  }

  @Test
  public void testConflictModuleImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package test;
                         public class List {}
                         """);
    doTest();
  }

  @Test
  public void testConflictModuleImportDemandOverModule() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getStandardLevel(), () -> {
      myFixture.addClass("""
                           package test;
                           public class List {}
                           """);
      doTest();
    });
  }

  @Test
  public void testWithPackageStatement() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.IMPLICIT_CLASSES.getStandardLevel(), () -> {
      myFixture.enableInspections(new ImplicitToExplicitClassBackwardMigrationInspection());
      myFixture.testHighlighting(true, false, true, "foo/before" + myFixture.getTestName(false) + ".java");
      EdtTestUtil.runInEdtAndWait(() -> {
        myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(
          JavaBundle.message("inspection.implicit.to.explicit.class.backward.migration.fix.name")));
        myFixture.checkResultByFile("foo/after" + myFixture.getTestName(false) + ".java");
      });
    });
  }
}
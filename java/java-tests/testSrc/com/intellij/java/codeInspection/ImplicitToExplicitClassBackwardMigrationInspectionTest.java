// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.ImplicitToExplicitClassBackwardMigrationInspection;
import com.intellij.java.JavaBundle;
import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ImplicitToExplicitClassBackwardMigrationInspectionTest extends LightJavaCodeInsightFixtureTestCase {


  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_23;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/implicitToExplicitClassBackwardMigration/";
  }

  private void doTest() {
    myFixture.addClass("""
                         package java.io;
                         
                         public final class IO {
                           public static void println(Object obj) {}
                         }
                         """);
    myFixture.enableInspections(new ImplicitToExplicitClassBackwardMigrationInspection());
    myFixture.testHighlighting(true, false, true, "before" + getTestName(false) + ".java");
    myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(
      JavaBundle.message("inspection.implicit.to.explicit.class.backward.migration.fix.name")));
    myFixture.checkResultByFile("after" + getTestName(false) + ".java");
  }

  public void testSimple() { doTest(); }

  public void testSimple2() { doTest(); }

  public void testAdjustComments() { doTest(); }

  public void testWithPrint() { doTest(); }

  public void testSimpleModuleImport() { doTest(); }

  public void testConflictModuleImport() {
    myFixture.addClass("""
                         package test;
                         public class List {}
                         """);
    doTest();
  }

  public void testConflictModuleImportDemandOverModule() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PACKAGE_IMPORTS_SHADOW_MODULE_IMPORTS.getMinimumLevel(), () -> {
      myFixture.addClass("""
                           package test;
                           public class List {}
                           """);
      doTest();
    });
  }

  public void testWithPackageStatement() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.IMPLICIT_CLASSES.getMinimumLevel(), () -> {
      myFixture.enableInspections(new ImplicitToExplicitClassBackwardMigrationInspection());
      myFixture.testHighlighting(true, false, true, "foo/before" + getTestName(false) + ".java");
      myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(
        JavaBundle.message("inspection.implicit.to.explicit.class.backward.migration.fix.name")));
      myFixture.checkResultByFile("foo/after" + getTestName(false) + ".java");
    });
  }
}
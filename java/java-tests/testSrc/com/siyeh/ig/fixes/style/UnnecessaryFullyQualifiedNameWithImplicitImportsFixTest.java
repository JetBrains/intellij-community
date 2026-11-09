// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.style;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionFixture;
import com.siyeh.ig.style.UnnecessaryFullyQualifiedNameInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class UnnecessaryFullyQualifiedNameWithImplicitImportsFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_23);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new UnnecessaryFullyQualifiedNameInspection());

  @Test
  public void testSimpleImportWIthModule() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

  @Test
  public void testSimpleImportWIthModuleImplicitClasses() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

  @Test
  public void testSimpleImportJavaLangWithImplicitClass() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

  @Test
  public void testAlreadyImported() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.addClass("""
    package p;
    public class Date {}""");
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

  @Test
  public void testSimpleImportJavaLangWithImplicitClassWithConflict() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.addClass("""
    package p;
    public class String {}""");
    inspectionFixture.get().doTest();
    inspectionFixture.get().assertQuickFixNotAvailable(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

  @Test
  public void testSimpleImportWithModuleWithConflict() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.addClass("""
    package p;
    public class Date {}""");
    inspectionFixture.get().doTest();
    inspectionFixture.get().assertQuickFixNotAvailable(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

  @Test
  public void testSimpleImportWithModuleWithConflictWIthImplicitClass() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.addClass("""
    package p;
    public class Date {}""");
    inspectionFixture.get().doTest();
    inspectionFixture.get().assertQuickFixNotAvailable(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

  @Test
  public void testSimpleImportWithoutModuleWithoutConflict() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    myFixture.addClass("""
    package p;
    public class Date {}""");
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

}

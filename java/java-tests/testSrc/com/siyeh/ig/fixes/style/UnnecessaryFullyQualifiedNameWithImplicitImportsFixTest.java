// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.style.UnnecessaryFullyQualifiedNameInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryFullyQualifiedNameWithImplicitImportsFixTest extends LightJavaInspectionTestCase {
  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new UnnecessaryFullyQualifiedNameInspection();
  }

  public void testSimpleImportWIthModule() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

  public void testSimpleImportWIthModuleImplicitClasses() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

  public void testSimpleImportJavaLangWithImplicitClass() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

  public void testAlreadyImported() {
    myFixture.addClass("""
    package p;
    public class Date {}""");
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

  public void testSimpleImportJavaLangWithImplicitClassWithConflict() {
    myFixture.addClass("""
    package p;
    public class String {}""");
    doTest();
    assertQuickFixNotAvailable(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

  public void testSimpleImportWithModuleWithConflict() {
    myFixture.addClass("""
    package p;
    public class Date {}""");
    doTest();
    assertQuickFixNotAvailable(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

  public void testSimpleImportWithModuleWithConflictWIthImplicitClass() {
    myFixture.addClass("""
    package p;
    public class Date {}""");
    doTest();
    assertQuickFixNotAvailable(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }

  public void testSimpleImportWithoutModuleWithoutConflict() {
    myFixture.addClass("""
    package p;
    public class Date {}""");
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix"));
  }


  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_23;
  }
}

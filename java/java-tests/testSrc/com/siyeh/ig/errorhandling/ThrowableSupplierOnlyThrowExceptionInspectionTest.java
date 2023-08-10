// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThrowableSupplierOnlyThrowExceptionInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new ThrowableSupplierOnlyThrowExceptionInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected String getBasePath() {
    return "/java/java-tests/testData/ig/com/siyeh/igtest/errorhandling/throwable_supplier_only_throw_exception";
  }

  public void testSimple() {
    doTest();
    checkQuickFix(InspectionGadgetsBundle.message("throwable.supplier.only.throw.exception.quickfix"));
  }

  public void testSeveral() {
    doTest();
    checkQuickFixAll();
  }
}
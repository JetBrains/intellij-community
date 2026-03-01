// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.jdk;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.jdk.VarargParameterInspection;

public class VarargParameterFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new VarargParameterInspection());
    myFixture.addClass("""
                         package org.jetbrains.annotations;
                         @Documented
                         @Retention(RetentionPolicy.CLASS)
                         @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
                         public @interface NotNull {}
                         """);
    myRelativePath = "jdk/vararg_parameter";
    myDefaultHint = InspectionGadgetsBundle.message("variable.argument.method.quickfix");
  }

  public void testGenericType() { doTest(); }
  public void testEnumConstants() { doTest(); }
  public void testConstructorCall() { doTest(); }
  public void testJavadocReference() { doTest(); }
  public void testTypeParameter() { doTest(); }
}
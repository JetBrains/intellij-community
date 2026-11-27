// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.jdk;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.jdk.VarargParameterInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class VarargParameterFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new VarargParameterInspection()), "jdk/vararg_parameter",
                        InspectionGadgetsBundle.message("variable.argument.method.quickfix"));

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    myFixture.addClass("""
                         package org.jetbrains.annotations;
                         @Documented
                         @Retention(RetentionPolicy.CLASS)
                         @Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
                         public @interface NotNull {}
                         """);
  }

  @Test
  public void testGenericType() { ig.get().doTest(); }
  @Test
  public void testEnumConstants() { ig.get().doTest(); }
  @Test
  public void testConstructorCall() { ig.get().doTest(); }
  @Test
  public void testJavadocReference() { ig.get().doTest(); }
  @Test
  public void testTypeParameter() { ig.get().doTest(); }
}
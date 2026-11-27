// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.migration;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.migration.MethodCanBeVariableArityMethodInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ConvertToVarargsMethodFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new MethodCanBeVariableArityMethodInspection()), "migration/convert_to_varargs_method",
                        InspectionGadgetsBundle.message("convert.to.variable.arity.method.quickfix"));

  @Test
  public void testDeepArray() { ig.get().doTest(); }
  @Test
  public void testComment() { ig.get().doTest(); }
  @Test
  public void testFinal() { ig.get().doTest(); }
  @Test
  public void testCStyle() { ig.get().doTest(); }
  @Test
  public void testJavadocLink() { ig.get().doTest(); }

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
}

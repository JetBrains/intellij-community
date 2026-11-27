// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.asserttoif;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.asserttoif.IfCanBeAssertionInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class IfCanBeAssertionInspectionTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new IfCanBeAssertionInspection()), "asserttoif/if_to_assert",
                        InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.assertion.quickfix"));

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    myFixture.addClass("""
                         package java.util;
                         public class Objects {
                             public static <T> T requireNonNull(T obj) {
                                 if (obj == null)
                                     throw new NullPointerException();
                                 return obj;
                             }
                             public static <T> T requireNonNull(T obj, String msg) {
                                 if (obj == null)
                                     throw new NullPointerException();
                                 return obj;
                             }
                         }""");
    myFixture.addClass("""
                         package com.google.common.base;
                         public class Preconditions {
                             public static <T> T checkNotNull(T obj) {
                                 if (obj == null)
                                     throw new NullPointerException();
                                 return obj;
                             }
                             public static <T> T checkNotNull(T obj, Object msg) {
                                 if (obj == null)
                                     throw new NullPointerException();
                                 return obj;
                             }
                         }""");
  }

  @Test
  public void testRandomThrowable() { ig.get().doTest(); }
  @Test
  public void testParentheses() { ig.get().doTest(); }
  @Test
  public void testParenthesesRequireNonNull() { ig.get().doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  @Test
  public void testPreconditions1() { ig.get().doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  @Test
  public void testPreconditions2() { ig.get().doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  @Test
  public void testPreconditions3() { ig.get().doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  @Test
  public void testObjectsRequireNonNull() { ig.get().doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  @Test
  public void testObjectsRequireNonNullNoMessage() { ig.get().doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  @Test
  public void testNoCondition() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testInequalityCondition() { ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
}

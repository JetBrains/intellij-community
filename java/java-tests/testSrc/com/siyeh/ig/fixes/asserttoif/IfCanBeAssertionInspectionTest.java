// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.asserttoif;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.asserttoif.IfCanBeAssertionInspection;

/**
 * @author Bas Leijdekkers
 */
public class IfCanBeAssertionInspectionTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new IfCanBeAssertionInspection());
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
    myRelativePath = "asserttoif/if_to_assert";
    myDefaultHint = InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.assertion.quickfix");
  }

  public void testRandomThrowable() { doTest(); }
  public void testParentheses() { doTest(); }
  public void testParenthesesRequireNonNull() { doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  public void testPreconditions1() { doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  public void testPreconditions2() { doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  public void testPreconditions3() { doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  public void testObjectsRequireNonNull() { doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  public void testObjectsRequireNonNullNoMessage() { doTest(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
  public void testNoCondition() { assertQuickfixNotAvailable(); }
  public void testInequalityCondition() { assertQuickfixNotAvailable(InspectionGadgetsBundle.message("if.can.be.assertion.replace.with.objects.requirenonnull.quickfix")); }
}

// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.migration;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.migration.UnnecessaryBoxingInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class UnnecessaryBoxingFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnnecessaryBoxingInspection()), "migration/unnecessary_boxing");

  @Test
  public void testLiteral1() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                     "Long l = new /**/Long(1);",
                     "Long l = 1L;");
  }

  @Test
  public void testLiteral2() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "Float l = new /**/Float(1);",
                 "Float l = 1F;");
  }

  @Test
  public void testLiteral3() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "Float l = new /**/Float(1.0);",
                 "Float l = 1.0F;");
  }

  @Test
  public void testLiteral4() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "Float l = new /**/Float(1d);",
                 "Float l = 1F;");
  }

  @Test
  public void testLiteral5() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "Double l = new /**/Double(1);",
                 "Double l = 1.0;");
  }

  @Test
  public void testBooleanLiteral() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "final Boolean aBoolean = Boolean.valueOf/**/(true);",
                 "final Boolean aBoolean = Boolean.TRUE;");
  }

  @Test
  public void testStringConcatenation() {
    ig.get().doExpressionTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                     "\"a\" + Long./**/valueOf(1L + 2L) + \"b\"",
                     "\"a\" + (1L + 2L) + \"b\"");
  }

  @Test
  public void testStringConcatenation2() {
    ig.get().doExpressionTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                     "\"a\" + Long./**/valueOf(1L - 2L) + \"b\"",
                     "\"a\" + (1L - 2L) + \"b\"");
  }

  @Test
  public void testHex() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "float f = Float./**/valueOf(0x123);",
                 "float f = (float) 0x123;");
  }

  @Test
  public void testHexDouble() {
    //noinspection RedundantCast
    ig.get().doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "double f = Double./**/valueOf(0x123);",
                 "double f = (double) 0x123;");
  }

  @Test
  @SuppressWarnings("OctalInteger")
  public void testOctal() {
    ig.get().doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "float f = Float.valueOf/**/(0123);",
                 "float f = (float) 0123;");
  }

  @Test
  @SuppressWarnings("OctalInteger")
  public void testOctalDouble() {
    //noinspection RedundantCast
    ig.get().doMemberTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"),
                 "double f = Double.valueOf/**/(0123);",
                 "double f = (double) 0123;");
  }

  @Test
  public void testCast() {
    ig.get().doTest(InspectionGadgetsBundle.message("unnecessary.boxing.remove.quickfix"));
  }

  @Test
  public void testParseInt() {
    ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "Integer.parseInt()"));
  }

  @Test
  public void testStaticImport() {
    ig.get().doTest(CommonQuickFixBundle.message("fix.replace.with.x", "Integer.parseInt()"));
  }

  @Test
  public void testShadowImport() {
    ig.get().assertQuickfixNotAvailable("Fix all 'Unnecessary boxing' problems in file");
  }

  @Test
  public void testConstantReference() {
    ig.get().doTest("Fix all 'Unnecessary boxing' problems in file");
  }

}
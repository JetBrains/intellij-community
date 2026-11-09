// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class UnpredictableBigDecimalConstructorCallInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new UnpredictableBigDecimalConstructorCallInspection(), getEnvironmentClasses());

  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package java.math;" +
      "public class BigDecimal {" +
      "  public BigDecimal(double d) {}" +
      "  public BigDecimal(int i) {}" +
      "}"
    };
  }

  @Test
  public void testNotMathBigDecimal() {
    inspectionFixture.get().doTest("class X {" +
           "  void foo() {" +
           "    new BigDecimal(.1);" +
           "  }" +
           "  class BigDecimal {" +
           "    BigDecimal(double d) {}" +
           "  }" +
           "}");
  }

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("import java.math.*;" +
           "class X {" +
           "  void foo() {" +
           "    new /*Unpredictable 'new BigDecimal()' call*/BigDecimal/**/(.1);" +
           "    new BigDecimal(1);" +
           "  }" +
           "}");
  }

  @Test
  public void testReferences() {
    inspectionFixture.get().doTest("""
             import java.math.BigDecimal;
             class X {
               void one(double expectedMultiplier) {
                 final BigDecimal n = new BigDecimal(expectedMultiplier);
                 final BigDecimal m = new BigDecimal(getVal());
                 final BigDecimal o = new /*Unpredictable 'new BigDecimal()' call*/BigDecimal/**/(new Double[] {0.1}[0]);
                 final BigDecimal p = new /*Unpredictable 'new BigDecimal()' call*/BigDecimal/**/((0.1 + (0.2)));
               }
             
               private static double getVal() {
                 return 0.1;
               }
             
               protected void two(double... expectedMultipliers) {
                 for (int i = 0; i < expectedMultipliers.length; i++) {
                   final BigDecimal n = new BigDecimal(expectedMultipliers[i]);
                 }
               }
             }""");
  }
}
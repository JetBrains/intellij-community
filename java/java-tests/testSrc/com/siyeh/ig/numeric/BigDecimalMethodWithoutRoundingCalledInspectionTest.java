// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.numeric;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

@AnalysisHostTest
@SuppressWarnings({"BigDecimalMethodWithoutRoundingCalled", "BigDecimalLegacyMethod"})
public class BigDecimalMethodWithoutRoundingCalledInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_9);
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new BigDecimalMethodWithoutRoundingCalledInspection(), getEnvironmentClasses());

  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package java.math;" +
      "public class BigDecimal {" +
      "  public BigDecimal setScale(int newScale) {}" +
      "  public BigDecimal setScale(int newScale, int roundingMode) {}" +
      "  public BigDecimal divide(BigDecimal divisor) {}" +
      "  public BigDecimal divide(BigDecimal divisor, int roundingMode) {}" +
      "}"
    };
  }

  @Test
  public void testSetScale() {
    // noinspection BigDecimalLegacyMethod
    inspectionFixture.get().doTest("import java.math.BigDecimal;" +
           "class X {" +
           "  static void foo(BigDecimal value) {" +
           "    value = value./*'BigDecimal.setScale()' called without a rounding mode argument*/setScale/**/(2);" +
           "    System.out.println(value.setScale(2, 1));" +
           "  }" +
           "}");
  }

  @Test
  public void testDivide() {
    // noinspection BigDecimalLegacyMethod
    inspectionFixture.get().doTest("import java.math.BigDecimal;" +
           "class X {" +
           "  static void foo(BigDecimal value) {" +
           "    value = value./*'BigDecimal.divide()' called without a rounding mode argument*/divide/**/(value);" +
           "    System.out.println(value.divide(value, 1));" +
           "  }" +
           "}");
  }

  @Test
  public void testNoWarnOnOtherMethod() {
    inspectionFixture.get().doTest("""
             import java.math.BigDecimal;
             import java.math.RoundingMode;
             class B {
                 public static BigDecimal scaleValue(BigDecimal v) {
                     return setScale(v);
                 }

                 public static BigDecimal setScale(BigDecimal v) {
                     return v != null ? v.setScale(6, RoundingMode.HALF_EVEN) : null;
                 }
             }""");
  }
}
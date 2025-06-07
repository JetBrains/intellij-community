// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class UnpredictableBigDecimalConstructorCallInspectionTest extends LightJavaInspectionTestCase {

  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnpredictableBigDecimalConstructorCallInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.math;" +
      "public class BigDecimal {" +
      "  public BigDecimal(double d) {}" +
      "  public BigDecimal(int i) {}" +
      "}"
    };
  }

  public void testNotMathBigDecimal() {
    doTest("class X {" +
           "  void foo() {" +
           "    new BigDecimal(.1);" +
           "  }" +
           "  class BigDecimal {" +
           "    BigDecimal(double d) {}" +
           "  }" +
           "}");
  }

  public void testSimple() {
    doTest("import java.math.*;" +
           "class X {" +
           "  void foo() {" +
           "    new /*Unpredictable 'new BigDecimal()' call*/BigDecimal/**/(.1);" +
           "    new BigDecimal(1);" +
           "  }" +
           "}");
  }

  public void testReferences() {
    doTest("""
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
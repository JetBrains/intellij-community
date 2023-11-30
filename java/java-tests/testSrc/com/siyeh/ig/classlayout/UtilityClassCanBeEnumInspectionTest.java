// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class UtilityClassCanBeEnumInspectionTest extends LightJavaInspectionTestCase {

  public void testUtilityClassCanBeEnum() {
    doTest();
  }

  public void testQuickfix() {
    doTest("""
             final class /*Utility class 'Util' can be 'enum'*//*_*/Util/**/ {
               public static void driveCar() {}
             }""");
    checkQuickFix("Convert to 'enum'",
                  """
                    enum Util {
                        ;

                        public static void driveCar() {}
                    }""");
  }

  public void testUtilityClassInstantiation() {
    doTest("""
             class SmartStepClass {
               public static final int a = 1;
               public static final String b = String.valueOf(2);

               public static void main(String[] args) {
                 new SmartStepClass();
               }
             }""");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UtilityClassCanBeEnumInspection();
  }
}
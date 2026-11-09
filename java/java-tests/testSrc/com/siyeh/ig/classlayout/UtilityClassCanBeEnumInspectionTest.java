// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class UtilityClassCanBeEnumInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new UtilityClassCanBeEnumInspection());

  @Test
  public void testUtilityClassCanBeEnum() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testQuickfix() {
    inspectionFixture.get().doTest("""
             final class /*Utility class 'Util' can be 'enum'*//*_*/Util/**/ {
               public static void driveCar() {}
             }""");
    inspectionFixture.get().checkQuickFix("Convert to 'enum'",
                  """
                    enum Util {
                        ;

                        public static void driveCar() {}
                    }""");
  }

  @Test
  public void testUtilityClassInstantiation() {
    inspectionFixture.get().doTest("""
             class SmartStepClass {
               public static final int a = 1;
               public static final String b = String.valueOf(2);

               public static void main(String[] args) {
                 new SmartStepClass();
               }
             }""");
  }
}
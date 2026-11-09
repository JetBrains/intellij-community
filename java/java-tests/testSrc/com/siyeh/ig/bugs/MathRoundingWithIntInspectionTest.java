// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.siyeh.ig.bugs;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class MathRoundingWithIntInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new MathRoundingWithIntArgumentInspection(), getEnvironmentClasses());

  @Test
  public void testWarningMathRoundingWithInt() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
      package java.lang;
      public final class StrictMath{
        public static long round(float a) {
            return Math.round(a);
        }
        public static double floor(double a) {
            return Math.floor(a);
        }
        public static double ceil(double a) {
            return Math.ceil(a);
        }
        public static double rint(double a) {
            return Math.rint(a);
        }
        public static double sin(double a) {
            return Math.sin(a);
        }
      }
      """
    };
  }
}

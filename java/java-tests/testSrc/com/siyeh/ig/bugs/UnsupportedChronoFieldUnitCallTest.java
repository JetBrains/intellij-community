// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

@AnalysisHostTest
public class UnsupportedChronoFieldUnitCallTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new UnsupportedChronoFieldUnitCallInspection());

  @Test
  public void testUnsupportedField() {
    inspectionFixture.get().doTest("""
    import java.time.LocalDate;
    import java.time.LocalTime;
    import java.time.temporal.ChronoField;
    
    class UnsupportedField {
      public static void testLocalTime(LocalTime localTime) {
        int i1 = localTime.get(ChronoField.SECOND_OF_DAY);
        int i2 = localTime.get(/*Unsupported argument value: 'MONTH_OF_YEAR'*/ChronoField.MONTH_OF_YEAR/**/);
      }
      public static void testLocalDate(LocalDate localDate) {
        int i1 = localDate.get(/*Unsupported argument value: 'SECOND_OF_DAY'*/ChronoField.SECOND_OF_DAY/**/);
        int i2 = localDate.get(ChronoField.MONTH_OF_YEAR);
      }
    }""");
  }
  @Test
  public void testUnsupportedFieldTrace() {
    inspectionFixture.get().doTest("""
    import java.time.LocalDate;
    import java.time.LocalTime;
    import java.time.temporal.ChronoField;
    
    class UnsupportedFieldTrace {
      public static void testLocalTime(LocalTime localTime, int i) {
        ChronoField chronoField = ChronoField.SECOND_OF_DAY;
        if (i == 1) {
          chronoField = ChronoField.MONTH_OF_YEAR;
        }
        int i2 = localTime.get(/*Unsupported argument value: 'MONTH_OF_YEAR'*/chronoField/**/);
      }
    }""");
  }
  @Test
  public void testUnsupportedUnit() {
    inspectionFixture.get().doTest("""
    import java.time.LocalDate;
    import java.time.LocalTime;
    import java.time.temporal.ChronoUnit;
    
    class UnsupportedUnit {
      public static void testLocalTime(LocalTime localTime) {
        LocalTime l1 = localTime.minus(1, ChronoUnit.HOURS);
        LocalTime l2 = localTime.minus(1, /*Unsupported argument value: 'DAYS'*/ChronoUnit.DAYS/**/);
      }
    
      public static void testLocalDate(LocalDate localDate) {
        LocalDate l1 = localDate.minus(1, /*Unsupported argument value: 'HOURS'*/ChronoUnit.HOURS/**/);
        LocalDate l2 = localDate.minus(1, ChronoUnit.DAYS);
      }
    }""");
  }
}
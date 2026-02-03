// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnsupportedChronoFieldUnitCallTest extends LightJavaInspectionTestCase {
  public void testUnsupportedField() {
    doTest("""
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
  public void testUnsupportedFieldTrace() {
    doTest("""
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
  public void testUnsupportedUnit() {
    doTest("""
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

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnsupportedChronoFieldUnitCallInspection();
  }
}
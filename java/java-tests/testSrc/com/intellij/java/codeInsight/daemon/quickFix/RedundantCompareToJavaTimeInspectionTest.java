// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.RedundantCompareToJavaTimeInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;


public class RedundantCompareToJavaTimeInspectionTest extends LightJavaCodeInsightFixtureTestCase {


  public void testCompareTo() {
    myFixture.addClass("""
                         package java.time.chrono;
                         interface ChronoLocalDateTime<T>{}
                         interface ChronoLocalDate{}
                         """);
    //ignore because we mock these classes
    //noinspection MethodOverloadsMethodOfSuperclass
    myFixture.addClass("""
                         package java.time;
                         import java.time.chrono.*;class LocalDate implements ChronoLocalDate, Comparable<ChronoLocalDate>{
                           public static LocalDate now() {
                             return new LocalDate();
                           }
                           @Override
                           public int compareTo(ChronoLocalDate o) {
                             return 0;
                           }
                           public boolean isAfter(LocalDate localDate) {
                             return true;
                           }
                           public boolean isBefore(LocalDate localDate) {
                             return true;
                           }
                           public boolean isEqual(LocalDate localDate) {
                             return true;
                           }
                         }
                         class LocalDateTime implements ChronoLocalDateTime<LocalDateTime>, Comparable<ChronoLocalDateTime>{
                           public static LocalDateTime now() {
                             return new LocalDateTime();
                           }
                           @Override
                           public int compareTo(ChronoLocalDateTime<?> o) {
                             return 0;
                           }
                           public boolean isAfter(LocalDateTime localDateTime) {
                             return true;
                           }
                           public boolean isBefore(LocalDateTime localDateTime) {
                             return true;
                           }
                           public boolean isEqual(LocalDateTime localDateTime) {
                             return true;
                           }
                         }
                         class LocalTime implements Comparable<LocalTime>{
                           public static LocalTime now() {
                             return new LocalTime();
                           }
                           @Override
                           public int compareTo(LocalTime o) {
                             return 0;
                           }
                           public boolean isAfter(LocalTime localTime) {
                             return true;
                           }
                           public boolean isBefore(LocalTime localTime) {
                             return true;
                           }
                         }
                         class OffsetTime implements Comparable<OffsetTime> {
                           public static OffsetTime now() {
                             return new OffsetTime();
                           }

                           @Override
                           public int compareTo(OffsetTime o) {
                             return 0;
                           }
                           public boolean isAfter(OffsetTime offsetTime) {
                             return true;
                           }
                           public boolean isBefore(OffsetTime offsetTime) {
                             return true;
                           }
                           public boolean isEqual(OffsetTime offsetTime) {
                             return true;
                           }
                         }
                         class OffsetDateTime implements Comparable<OffsetDateTime>{
                           public static OffsetDateTime now() {
                             return new OffsetDateTime();
                           }
                           @Override
                           public int compareTo(OffsetDateTime o) {
                             return 0;
                           }
                           public boolean isAfter(OffsetDateTime offsetDateTime) {
                             return true;
                           }
                           public boolean isBefore(OffsetDateTime offsetDateTime) {
                             return true;
                           }
                           public boolean isEqual(OffsetDateTime offsetDateTime) {
                             return true;
                           }
                         }""");
    final LocalInspectionTool inspection = new RedundantCompareToJavaTimeInspection();
    myFixture.enableInspections(inspection);

    myFixture.configureByFile("beforeCompareTo.java");
    myFixture.launchAction(myFixture.findSingleIntention("Fix all 'Expression with 'java.time' 'compareTo()' call can be simplified' problems in file"));
    myFixture.checkResultByFile("afterCompareTo.java");
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() +
     "/codeInsight/daemonCodeAnalyzer/quickFix/redundantCompareToJavaTime";
  }
}
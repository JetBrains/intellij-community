// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.RedundantCompareToJavaTimeInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;


public class RedundantCompareToJavaTimeInspectionTest extends LightJavaCodeInsightFixtureTestCase {


  public void testCompareTo() {
    myFixture.addClass("package java.time.chrono;\n" +
                       "interface ChronoLocalDateTime<T>{}\n" +
                       "interface ChronoLocalDate{}\n");
    //ignore because we mock these classes
    //noinspection MethodOverloadsMethodOfSuperclass
    myFixture.addClass("package java.time;\n" +
                       "import java.time.chrono.*;" +
                       "class LocalDate implements ChronoLocalDate, Comparable<ChronoLocalDate>{\n" +
                       "  public static LocalDate now() {\n" +
                       "    return new LocalDate();\n" +
                       "  }\n" +
                       "  @Override\n" +
                       "  public int compareTo(ChronoLocalDate o) {\n" +
                       "    return 0;\n" +
                       "  }\n" +
                       "  public boolean isAfter(LocalDate localDate) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "  public boolean isBefore(LocalDate localDate) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "  public boolean isEqual(LocalDate localDate) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "}\n" +
                       "class LocalDateTime implements ChronoLocalDateTime<LocalDateTime>, Comparable<ChronoLocalDateTime>{\n" +
                       "  public static LocalDateTime now() {\n" +
                       "    return new LocalDateTime();\n" +
                       "  }\n" +
                       "  @Override\n" +
                       "  public int compareTo(ChronoLocalDateTime<?> o) {\n" +
                       "    return 0;\n" +
                       "  }\n" +
                       "  public boolean isAfter(LocalDateTime localDateTime) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "  public boolean isBefore(LocalDateTime localDateTime) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "  public boolean isEqual(LocalDateTime localDateTime) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "}\n" +
                       "class LocalTime implements Comparable<LocalTime>{\n" +
                       "  public static LocalTime now() {\n" +
                       "    return new LocalTime();\n" +
                       "  }\n" +
                       "  @Override\n" +
                       "  public int compareTo(LocalTime o) {\n" +
                       "    return 0;\n" +
                       "  }\n" +
                       "  public boolean isAfter(LocalTime localTime) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "  public boolean isBefore(LocalTime localTime) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "}\n" +
                       "class OffsetTime implements Comparable<OffsetTime> {\n" +
                       "  public static OffsetTime now() {\n" +
                       "    return new OffsetTime();\n" +
                       "  }\n" +
                       "\n" +
                       "  @Override\n" +
                       "  public int compareTo(OffsetTime o) {\n" +
                       "    return 0;\n" +
                       "  }\n" +
                       "  public boolean isAfter(OffsetTime offsetTime) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "  public boolean isBefore(OffsetTime offsetTime) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "  public boolean isEqual(OffsetTime offsetTime) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "}\n" +
                       "class OffsetDateTime implements Comparable<OffsetDateTime>{\n" +
                       "  public static OffsetDateTime now() {\n" +
                       "    return new OffsetDateTime();\n" +
                       "  }\n" +
                       "  @Override\n" +
                       "  public int compareTo(OffsetDateTime o) {\n" +
                       "    return 0;\n" +
                       "  }\n" +
                       "  public boolean isAfter(OffsetDateTime offsetDateTime) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "  public boolean isBefore(OffsetDateTime offsetDateTime) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "  public boolean isEqual(OffsetDateTime offsetDateTime) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "}");
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
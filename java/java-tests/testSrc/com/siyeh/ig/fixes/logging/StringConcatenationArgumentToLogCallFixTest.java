// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.logging;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.logging.StringConcatenationArgumentToLogCallInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class StringConcatenationArgumentToLogCallFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(), "logging/string_concatenation_argument_to_log_call",
                        InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.quickfix"));

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    myFixture.addClass("""
                         package org.slf4j; public interface Logger {
                           void info(String format);
                           void info(String format, Exception e);
                         }""");
    myFixture.addClass("package org.slf4j; public final class LoggerFactory { public static Logger getLogger(Class clazz) { return null; }}");
    myFixture.addClass("package org.apache.logging.log4j; public interface LogBuilder { void log(String format); LogBuilder withLocation(); }");
    myFixture.addClass("package org.apache.logging.log4j; public interface Logger { LogBuilder atInfo(); void info(String format, Object... arguments); LogBuilder withLocation(); }");
    myFixture.addClass("""
                         package org.apache.logging.log4j;
                         public final class LogManager {
                           public static Logger getLogger(Class clazz) {
                             return null;
                           }
                           public static Logger getFormatterLogger(Class clazz) {
                             return null;
                           }
                         }""");
    myFixture.addClass(      """
      package java.text;
      public final class MessageFormat {
        public static String format(String format, Object... params);
      }"""
    );
    StringConcatenationArgumentToLogCallInspection inspection = new StringConcatenationArgumentToLogCallInspection();
    myFixture.enableInspections(inspection);
    String name = ig.get().getTestName(false);
    if (name.endsWith("UnknownLogger")) {
      inspection.isLog4JParameterizedLogger = false;
    }
  }

  @Test
  public void testUseOfConstant() { ig.get().doTest(); }
  @Test
  public void testCharLiteral() { ig.get().doTest(); }
  @Test
  public void testQuoteCharLiteral() { ig.get().doTest(); }
  @Test
  public void testLog4jFormatted() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.quickfix"));
  }
  @Test
  public void testLog4jFormattedUnknownLogger() {
    BaseInspection inspection = getInspection();
    if (inspection instanceof StringConcatenationArgumentToLogCallInspection stringConcatenationArgumentToLogCallInspection) {
      stringConcatenationArgumentToLogCallInspection.isLog4JParameterizedLogger = false;
    }
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.quickfix"));
  }

  @Test
  public void testLog4jAmbitiousDefaultLogger() {
    ig.get().doTest();
  }

  @Test
  public void testLog4jAmbitiousUnknownLogger() {
    ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.quickfix"));
  }

  @Test
  public void testLog4JLogBuilder() { ig.get().doTest(); }
  @Test
  public void testTextBlocks() {
    ig.get().doTest(
    InspectionsBundle.message("fix.all.inspection.problems.in.file",
                              InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.display.name")));
  }

  @Test
  public void testSimpleMessageFormat() { ig.get().doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.message.format.call.quickfix")); }
  @Test
  public void testMessageFormatMissingParameter() { ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.message.format.call.quickfix")); }
  @Test
  public void testMessageFormatMoreArguments() { ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.message.format.call.quickfix")); }
  @Test
  public void testMessageFormatFormatter() { ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.message.format.call.quickfix")); }
  @Test
  public void testSimpleConcatenationInsideMethod() { ig.get().doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.quickfix")); }
  @Test
  public void testConcatenationMessageFormat() { ig.get().doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.message.format.call.quickfix")); }
  @Test
  public void testSimpleMessageFormatWithException() { ig.get().doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.message.format.call.quickfix")); }

  @Test
  public void testSimpleStringFormat() { ig.get().doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  @Test
  public void testStringFormatWithWidth() { ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  @Test
  public void testNumberedStringFormat() { ig.get().doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  @Test
  public void testWrongStringFormat() { ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  @Test
  public void testLessArgumentsStringFormat() { ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  @Test
  public void testMoreArgumentsStringFormat() { ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  @Test
  public void testPreviousArgumentStringFormat() { ig.get().assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  @Test
  public void testConcatenationStringFormat() { ig.get().doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  @Test
  public void testSimpleStringFormatWithException() { ig.get().doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  @Test
  public void testStringFormatWithNewLine() { ig.get().doTest( InspectionsBundle.message("fix.all.inspection.problems.in.file",
                                                                                InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.display.name"))); }

  private static BaseInspection getInspection() {
    return null;
  }
}

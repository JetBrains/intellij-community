// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.logging;

import com.intellij.codeInspection.InspectionsBundle;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.logging.StringConcatenationArgumentToLogCallInspection;

public class StringConcatenationArgumentToLogCallFixTest extends IGQuickFixesTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDefaultHint = InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.quickfix");
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
                           public static Logger getFormattedLogger(Class clazz) {
                             return null;
                           }
                         }""");

    myFixture.addClass(      """
      package java.text;
      public final class MessageFormat {
        public static String format(String format, Object... params);
      }"""
    );

    myFixture.enableInspections(new StringConcatenationArgumentToLogCallInspection());
  }

  public void testUseOfConstant() { doTest(); }
  public void testCharLiteral() { doTest(); }
  public void testQuoteCharLiteral() { doTest(); }
  public void testLog4jFormatted() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.quickfix"));
  }
  public void testLog4JLogBuilder() { doTest(); }
  public void testTextBlocks() {
    doTest(
    InspectionsBundle.message("fix.all.inspection.problems.in.file",
                              InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.display.name")));
  }

  public void testSimpleMessageFormat() { doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.message.format.call.quickfix")); }
  public void testMessageFormatMissingParameter() { assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.message.format.call.quickfix")); }
  public void testMessageFormatMoreArguments() { assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.message.format.call.quickfix")); }
  public void testMessageFormatFormatter() { assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.message.format.call.quickfix")); }
  public void testSimpleConcatenationInsideMethod() { doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.quickfix")); }
  public void testConcatenationMessageFormat() { doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.message.format.call.quickfix")); }
  public void testSimpleMessageFormatWithException() { doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.message.format.call.quickfix")); }

  public void testSimpleStringFormat() { doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  public void testStringFormatWithWidth() { assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  public void testNumberedStringFormat() { doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  public void testWrongStringFormat() { assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  public void testLessArgumentsStringFormat() { assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  public void testMoreArgumentsStringFormat() { assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  public void testPreviousArgumentStringFormat() { assertQuickfixNotAvailable(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  public void testConcatenationStringFormat() { doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }
  public void testSimpleStringFormatWithException() { doTest(InspectionGadgetsBundle.message("string.concatenation.argument.to.log.string.format.call.quickfix")); }


  @Override
  protected String getRelativePath() {
    return "logging/string_concatenation_argument_to_log_call";
  }
}

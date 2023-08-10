// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    myFixture.addClass("package org.slf4j; public interface Logger { void info(String format); }");
    myFixture.addClass("package org.slf4j; public class LoggerFactory { public static Logger getLogger(Class clazz) { return null; }}");
    myFixture.addClass("package org.apache.logging.log4j; public interface LogBuilder { void log(String format); LogBuilder withLocation(); }");
    myFixture.addClass("package org.apache.logging.log4j; public interface Logger { LogBuilder atInfo(); }");
    myFixture.addClass("package org.apache.logging.log4j; public class LogManager { public static Logger getLogger(Class clazz) { return null; }}");
    myFixture.enableInspections(new StringConcatenationArgumentToLogCallInspection());
  }

  public void testUseOfConstant() { doTest(); }
  public void testCharLiteral() { doTest(); }
  public void testQuoteCharLiteral() { doTest(); }
  public void testLog4JLogBuilder() { doTest(); }
  public void testTextBlocks() {
    doTest(
    InspectionsBundle.message("fix.all.inspection.problems.in.file",
                              InspectionGadgetsBundle.message("string.concatenation.argument.to.log.call.display.name")));
  }

  @Override
  protected String getRelativePath() {
    return "logging/string_concatenation_argument_to_log_call";
  }
}

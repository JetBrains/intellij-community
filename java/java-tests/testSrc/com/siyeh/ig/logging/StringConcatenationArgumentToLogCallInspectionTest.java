// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.logging;

import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class StringConcatenationArgumentToLogCallInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected LocalInspectionTool getInspection() {
    final StringConcatenationArgumentToLogCallInspection inspection = new StringConcatenationArgumentToLogCallInspection();
    inspection.warnLevel = "WarnLevel".equals(getTestName(false))? 3 : 0; // debug level and lower
    return inspection;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package org.slf4j;" +
      "public interface Logger { " +
      "  void debug(String format, Object... arguments); " +
      "  void info(String format, Object... arguments);" +
      "}",

      "package org.slf4j; " +
      "public class LoggerFactory {" +
      "  public static Logger getLogger(Class clazz) {" +
      "    return null; " +
      "  }" +
      "}",

      "package org.apache.logging.log4j;" +
      "public interface Logger {" +
      "  void info(String var2);" +
      "  void fatal(String var1);" +
      "  LogBuilder atDebug();" +
      "  LogBuilder atInfo();" +
      "}",

      "package org.apache.logging.log4j;" +
      "public class LogManager {" +
      "  public static Logger getLogger() {" +
      "    return null;" +
      "  }" +
      "}",

      "package org.apache.logging.log4j;" +
      "public interface LogBuilder {" +
      "  void log(String format, Object p0);" +
      "  void log(String format, Object... params);" +
      "}"
    };
  }

  public void testBasic() {
    doTest("""
             import org.slf4j.*;
             class X {
               void foo() {
                 Logger logger = LoggerFactory.getLogger(X.class);
                 final String CONST = "const";
                 String var = "var";
                 logger./*Non-constant string concatenation as argument to 'debug()' logging call*/debug/**/("string " + var + CONST);
               }
             }"""
           );
  }

  public void testWarnLevel() {
    doTest("import org.slf4j.*;" +
           "class X {" +
           "  Logger LOG = LoggerFactory.getLogger(X.class);" +
           "  void foo(String s) {" +
           "    LOG.info(\"value: \" + s);" +
           "  }" +
           "}");
  }

  public void testLog4j2() {
    doTest("import org.apache.logging.log4j.*;" +
           "class Logging {" +
           "  private static final Logger LOG = LogManager.getLogger();" +
           "  void m(int i) {" +
           "    LOG./*Non-constant string concatenation as argument to 'info()' logging call*/info/**/(\"hello? \" + i);" +
           "    LOG./*Non-constant string concatenation as argument to 'fatal()' logging call*/fatal/**/(\"you got me \" + i);" +
           "  }" +
           "}");
  }

  public void testLog4j2LogBuilder() {
    doTest("import org.apache.logging.log4j.*;" +
           "class Logging {" +
           "  private static final Logger LOG = LogManager.getLogger();" +
           "  void m(int i) {" +
           "    LOG.atDebug()./*Non-constant string concatenation as argument to 'log()' logging call*/log/**/(\"hello? \" + i);" +
           "    LOG.atInfo()./*Non-constant string concatenation as argument to 'log()' logging call*/log/**/(\"you got me \" + i);" +
           "  }" +
           "}");
  }
}
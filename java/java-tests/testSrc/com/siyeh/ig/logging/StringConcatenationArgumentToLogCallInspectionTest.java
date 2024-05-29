// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.logging;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class StringConcatenationArgumentToLogCallInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected LocalInspectionTool getInspection() {
    final StringConcatenationArgumentToLogCallInspection inspection = new StringConcatenationArgumentToLogCallInspection();
    inspection.warnLevel = "WarnLevel".equals(getTestName(false))? 3 : 0; // debug level and lower
    return inspection;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    HighlightDisplayKey displayKey = HighlightDisplayKey.find(getInspection().getShortName());
    Project project = getProject();
    InspectionProfileImpl currentProfile = ProjectInspectionProfileManager.getInstance(project).getCurrentProfile();
    currentProfile.setErrorLevel(displayKey, HighlightDisplayLevel.WARNING, project);
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      """
      package org.slf4j;
      public interface Logger {\s
        void debug(String format, Object... arguments);
        void info(String format, Object... arguments);
      }""",

      """
      package org.slf4j;\s
      public final class LoggerFactory {
        public static Logger getLogger(Class clazz) {
          return null;\s
        }
      }""",

      """
      package org.apache.logging.log4j;
      public interface Logger {
        void info(String var2);
        void fatal(String var1);
        LogBuilder atDebug();
        LogBuilder atInfo();
      }""",

      """
      package org.apache.logging.log4j;
      public final class LogManager {
        public static Logger getLogger() {
          return null;
        }
        public static Logger getFormattedLogger() {
          return null;
        }
      }""",

      """
      package org.apache.logging.log4j;
      public interface LogBuilder {
        void log(String format, Object p0);
        void log(String format, Object... params);
      }""",
      """
      package java.text;
      public final class MessageFormat {
        public static String format(String format, Object... params);
      }"""
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
                 logger./*Non-constant string as argument to 'debug()' logging call*/debug/**/("string " + var + CONST);
               }
             }"""
           );
  }

  public void testWarnLevel() {
    doTest("""
             import org.slf4j.*;
             class X {
               Logger LOG = LoggerFactory.getLogger(X.class);
               void foo(String s) {
                 LOG.info("value: " + s);
               }
             }""");
  }

  public void testLog4j2() {
    doTest("""
             import org.apache.logging.log4j.*;
             class Logging {
               private static final Logger LOG = LogManager.getLogger();
               void m(int i) {
                 LOG./*Non-constant string as argument to 'info()' logging call*/info/**/("hello? " + i);
                 LOG./*Non-constant string as argument to 'fatal()' logging call*/fatal/**/("you got me " + i);
               }
             }""");
  }

  public void testLog4j2FormattedLogger() {
    doTest("""
             import org.apache.logging.log4j.*;
             
             final class Log4JFormatted {
             
               private static final Logger logger = LogManager.getFormattedLogger();
             
               public static void m(String a) {
                 logger./*Non-constant string as argument to 'info()' logging call*/info/**/("1" + "2" + a);
                                       }
             }""");
  }

  public void testLog4j2LogBuilder() {
    doTest("""
             import org.apache.logging.log4j.*;
             class Logging {
               private static final Logger LOG = LogManager.getLogger();
               void m(int i) {
                 LOG.atDebug()./*Non-constant string as argument to 'log()' logging call*/log/**/("hello? " + i);
                 LOG.atInfo()./*Non-constant string as argument to 'log()' logging call*/log/**/("you got me " + i);
               }
             }""");
  }


  public void testSlfStringFormat() {
    doTest("""
             import org.slf4j.*;
             class X {
               void foo() {
                 Logger log = LoggerFactory.getLogger(X.class);
                 log./*Non-constant string as argument to 'info()' logging call*/info/**/(String.format("%s %d", "1", 1));
               }
             }"""
    );
  }
  public void testSlfMessageFormat() {
    doTest("""
             import org.slf4j.*;
             import java.text.MessageFormat;

             class X {
               void foo() {
                 Logger log = LoggerFactory.getLogger(X.class);
                 log./*Non-constant string as argument to 'info()' logging call*/info/**/(MessageFormat.format("{1}, {0}", "1", 2));
               }
             }"""
    );
  }
}
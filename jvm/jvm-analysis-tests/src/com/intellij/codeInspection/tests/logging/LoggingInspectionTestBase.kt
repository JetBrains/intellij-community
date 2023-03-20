package com.intellij.codeInspection.tests.logging

import com.intellij.codeInspection.tests.JvmInspectionTestBase

abstract class LoggingInspectionTestBase : JvmInspectionTestBase() {

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      package org.slf4j.spi;
      public interface LoggingEventBuilder {
         void log(String format, Object... arguments);
      }
    """.trimIndent())
    myFixture.addClass("""
      package org.slf4j; 
      import org.slf4j.spi.LoggingEventBuilder; 
      public class LoggerFactory { 
      public static Logger getLogger(Class clazz) { return null; }
      public static Logger getLogger() { return null; }
      }
      public interface Logger { 
         void info(String format, Object... arguments); 
         void debug(String format, Object... arguments); 
         void warn(String format, Object... arguments); 
         boolean isDebugEnabled();
         boolean isInfoEnabled();
         LoggingEventBuilder atInfo(); 
         LoggingEventBuilder atDebug(); 
         LoggingEventBuilder atWarn(); 
         LoggingEventBuilder atError(); 
      }
    """.trimIndent())
    myFixture.addClass("""
      package org.apache.logging.log4j;
      import org.apache.logging.log4j.util.Supplier;
      public interface Logger {
        boolean isDebugEnabled();
        boolean isInfoEnabled();
        void info(String message, Object... params);
        void debug(String message, Object... params);
        void warn(String message, Object... params);
        void info(String message);
        void debug(String message);
        void warn(String message);
        void fatal(String message, Object... params);
        void error(Supplier<?> var1, Throwable var2);
        void info(String message, Supplier<?>... params);
        LogBuilder atInfo();
        LogBuilder atDebug();
        LogBuilder atWarn();
        LogBuilder atFatal();
        LogBuilder atError();
      }
    """.trimIndent())
    myFixture.addClass("""
      package org.apache.logging.log4j;
      public class LogManager {
        public static Logger getLogger() {
          return null;
        }
        public static Logger getFormatterLogger() {
          return null;
        }
      }
    """.trimIndent())
    myFixture.addClass("""
      package org.apache.logging.log4j.util;
      public interface Supplier<T> {
          T get();
      }
    """.trimIndent())
    myFixture.addClass("""
      package org.apache.logging.log4j;
      import org.apache.logging.log4j.util.Supplier;
      public interface LogBuilder {
        void log(String format);
        void log(String format, Object p0);
        void log(String format, Object... params);
        void log(String format, Supplier<?>... params);
      }
    """.trimIndent())
  }
}
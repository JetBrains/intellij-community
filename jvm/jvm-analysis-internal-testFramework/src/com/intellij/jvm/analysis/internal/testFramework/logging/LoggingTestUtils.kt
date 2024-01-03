package com.intellij.jvm.analysis.internal.testFramework.logging

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture

object LoggingTestUtils {
  fun addAkka(fixture: JavaCodeInsightTestFixture) {
    fixture.addClass("""
        package akka.event;
        public interface LoggingAdapter {
            void info(final String template, final Object arg1, final Object arg2);
            void log(final int level, final String template, final Object arg1, final Object arg2, final Object arg3);
            void log(final int level, final String template, final Object arg1, final Object arg2);
            void log(final int level, final String template, final Object arg1);
            void error(final Throwable cause, final String template, final Object arg1);
            void error(final Throwable cause, final String template, final Object arg1, final Object arg2);
            void error(final Throwable cause, final String template, final Object arg1, final Object arg2, final Object arg3);
            void error(final String template, final Object arg1);
            void error(final String template, final Object arg1, final Object arg2);
            void error(final String template, final Object arg1, final Object arg2, final Object arg3);
        }
      """.trimIndent())
  }

  fun addKotlinAdapter(fixture: JavaCodeInsightTestFixture) {
    fixture.addClass("""
        package kotlin.jvm.functions;
        public interface Function0<T>  {
            T invoke();
        }
      """.trimIndent())
  }

  fun addJUL(fixture: JavaCodeInsightTestFixture) {
    fixture.addClass("""
        package java.util.logging;
        public class Logger {
          public static Logger getLogger(String name) {
            return null;
          }
          public void warning(String msg) {}
          public boolean isLoggable(Level level) {}
        }
      """.trimIndent())
    fixture.addClass("""
        package java.util.logging;
        @SuppressWarnings("ALL") public class Level {
          public static final Level FINE = new Level();
          public static final Level WARNING = new Level();
        }
      """.trimIndent())
  }

  fun addLog4J(fixture: JavaCodeInsightTestFixture) {
    fixture.addClass("""
        package org.apache.logging.log4j;
        import org.apache.logging.log4j.util.Supplier;
        public interface Logger {
          boolean isDebugEnabled();
          boolean isInfoEnabled();
          boolean isWarnEnabled();
          void info(String message, Object... params);
          void info();
          void debug(String message, Object... params);
          void warn(String message, Object... params);
          void error(String message, Object... params);
          void trace(String message, Object... params);
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
          boolean isInfoEnabled(){return true;}
        }
      """.trimIndent())
    fixture.addClass("""
        package org.apache.logging.log4j;
        @SuppressWarnings("ALL") public class LogManager {
          public static Logger getLogger() {
            return null;
          }
          public static Logger getFormatterLogger() {
            return null;
          }
        }
      """.trimIndent())
    fixture.addClass("""
        package org.apache.logging.log4j.util;
        public interface Supplier<T> {
            T get();
        }
      """.trimIndent())
    fixture.addClass("""
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

  fun addIdeaLog(fixture: JavaCodeInsightTestFixture) {
    fixture.addClass("""
package com.intellij.openapi.diagnostic;

public interface Logger {
           void info(String format, Object... arguments); 
           void debug(String format, Object... arguments); 
           void warn(String format, Object... arguments); 
           void trace(String format, Object... arguments); 
           void error(String format, Object... arguments); 
}
""".trimIndent())
  }

  fun addSlf4J(fixture: JavaCodeInsightTestFixture) {
    fixture.addClass("""
        package org.slf4j.spi;
        public interface LoggingEventBuilder {
          LoggingEventBuilder addArgument(Object object);
          LoggingEventBuilder setMessage(String message);
          LoggingEventBuilder addKeyValue(String key, Object object);
           void log(String format, Object... arguments);
           void log();
        }
      """.trimIndent())
    fixture.addClass("""
        package org.slf4j; 
        import org.slf4j.spi.LoggingEventBuilder; 
        @SuppressWarnings("ALL") public class LoggerFactory { 
        public static Logger getLogger(Class clazz) { return null; }
        public static Logger getLogger() { return null; }
        }
        public interface Logger { 
           void info(String format, Object... arguments); 
           void debug(String format, Object... arguments); 
           void warn(String format, Object... arguments); 
           void trace(String format, Object... arguments); 
           void error(String format, Object... arguments); 
           boolean isDebugEnabled();
           boolean isInfoEnabled();
           LoggingEventBuilder atInfo(); 
           LoggingEventBuilder atDebug(); 
           LoggingEventBuilder atWarn(); 
           LoggingEventBuilder atError(); 
        }
      """.trimIndent())
  }
}
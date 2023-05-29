package com.intellij.codeInspection.tests.java.logging

import com.intellij.codeInspection.logging.LoggingPlaceholderCountMatchesArgumentCountInspection
import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.logging.LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase

class JavaLoggingPlaceholderCountMatchesArgumentCountInspectionTest : LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase() {

  fun `test log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
       private static final Logger LOG = LogManager.getLogger();
       void m(int i) {
         LOG.info(<warning descr="Fewer arguments provided (1) than placeholders specified (3)">"test? {}{}{}"</warning>, i);
         LogManager.getLogger().fatal(<warning descr="More arguments provided (1) than placeholders specified (0)">"test"</warning>, i);
         LOG.error(() -> "", new Exception());
       }
     }
    """.trimIndent())
  }

  fun `test log4j2LogBuilder`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
       private static final Logger LOG = LogManager.getLogger();
       void m(int i) {
         LOG.atInfo().log(<warning descr="Fewer arguments provided (1) than placeholders specified (3)">"test? {}{}{}"</warning>, i);
         LOG.atFatal().log(<warning descr="More arguments provided (2) than placeholders specified (0)">"test "</warning>, i, i);
         LOG.atError().log(<warning descr="More arguments provided (1) than placeholders specified (0)">"test? "</warning>, () -> "");
       }
     }
    """.trimIndent())
  }

  fun `test 1 exception and 1 placeholder`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        void foo() {
          RuntimeException e = new RuntimeException();
          LoggerFactory.getLogger(X.class).info(<warning descr="Fewer arguments provided (0) than placeholders specified (1)">"this: {}"</warning>, e);
        }
      }    
        """.trimIndent())
  }

  fun `test 1 exception and 2 placeholder`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        void foo() {
          RuntimeException e = new RuntimeException();
          LoggerFactory.getLogger(X.class).info("1: {} e: {}", 1, e);
        }
      }
        """.trimIndent())
  }

  fun `test 1 exception and 3 placeholder`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        void foo() {
          RuntimeException e = new RuntimeException();
          LoggerFactory.getLogger(X.class).info(<warning descr="Fewer arguments provided (1) than placeholders specified (3)">"1: {} {} {}"</warning>, 1, e);
        }
      }
        """.trimIndent())
  }

  fun `test no warn`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        void foo() {
          Logger logger = LoggerFactory.getLogger(X.class);
          logger.info("string {}", 1);
        }
      }
        """.trimIndent())
  }

  fun `test more placeholders`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        void foo() {
          Logger logger = LoggerFactory.getLogger(X.class);
          logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">"string {}{}"</warning>, 1);
        }
      }
        """.trimIndent())
  }

  fun `test fewer placeholders`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        void foo() {
          Logger logger = LoggerFactory.getLogger(X.class);
          logger.info(<warning descr="More arguments provided (1) than placeholders specified (0)">"string"</warning>, 1);
        }
      }
        """.trimIndent())
  }

  fun `test throwable`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        void foo() {
          Logger logger = LoggerFactory.getLogger(X.class);
          logger.info("string {}", 1, new RuntimeException());
        }
      }
        """.trimIndent())
  }

  fun `test multi catch`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.slf4j.*;
     class X {
         private static final Logger logger = LoggerFactory.getLogger( X.class );
         public void multiCatch() {
             try {
                 method();
             } catch ( FirstException|SecondException e ) {
                 logger.info( "failed with first or second", e );
             }
         }
         public void method() throws FirstException, SecondException {}
         public static class FirstException extends Exception { }
         public static class SecondException extends Exception { }
         }
        """.trimIndent())
  }

  fun `test no slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class FalsePositiveSLF4J {
         public void method( DefinitelyNotSLF4J definitelyNotSLF4J ) {
             definitelyNotSLF4J.info( "not a trace message", "not a trace parameter" );
         }
         public interface DefinitelyNotSLF4J {
             void info( String firstParameter, Object secondParameter );
         }
     }
         """.trimIndent())
  }

  fun `test array argument`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        Logger LOG = LoggerFactory.getLogger( X.class );
        void m(String a, int b, Object c) {
          LOG.info("test {} for test {} in test {}", new Object[] {a, b, c});
        }
      }
         """.trimIndent())
  }

  fun `test uncountable array`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        Logger LOG = LoggerFactory.getLogger( X.class );
        void m(Object[] objects) {
          LOG.info("test {} test text {} text {}", objects);
        }
      }
         """.trimIndent())
  }

  fun `test constant`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        Logger LOG = LoggerFactory.getLogger(X.class);
        private static final String message = "HELLO {}";
        void m() {
          LOG.info(<warning descr="Fewer arguments provided (0) than placeholders specified (1)">message</warning>);
        }
      }
      """.trimIndent())
  }
  fun `test non constant string`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        Logger LOG = LoggerFactory.getLogger(X.class);
        private static final String S = "{}";
        void m() {
          LOG.info(<warning descr="Fewer arguments provided (0) than placeholders specified (3)">S +"{}" + (1 + 2) + '{' + '}' +Integer.class</warning>);
        }
      }
      """.trimIndent())
  }

  fun `test escaping 1`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        Logger LOG = LoggerFactory.getLogger(X.class);
        void m() {
          LOG.info("Created key {}\\\\{}", 1, 2);
        }
      }
      """.trimIndent())
  }

  fun `test escaping 2`() {
    inspection.slf4jToLog4J2Type = LoggingPlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.NO
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        Logger LOG = LoggerFactory.getLogger(X.class);
        void m() {
          LOG.info(<warning descr="More arguments provided (2) than placeholders specified (1)">"Created key {}\\{}"</warning>, 1, 2);
        }
      }
      """.trimIndent())
  }

  fun `test null argument`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        Logger LOG = LoggerFactory.getLogger(X.class);
        void m() {
          LOG.info(null, new Exception());
          LOG.info("", new Exception());
        }
      }
      """.trimIndent())
  }

  fun `test log4j2 with text variables`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final String FINAL_TEXT = "const";
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          String text = "test {}{}{}";
          LOG.info(<warning descr="Fewer arguments provided (1) than placeholders specified (3)">text</warning>, i);
          final String text2 = "test ";
          LOG.fatal(<warning descr="More arguments provided (1) than placeholders specified (0)">text2</warning>, i);
          LOG.fatal(<warning descr="Fewer arguments provided (1) than placeholders specified (6)">text + text</warning>, i);
          LOG.fatal(<warning descr="Fewer arguments provided (1) than placeholders specified (18)">text + text + text + text + text + text</warning>, i);
          LOG.info(<warning descr="More arguments provided (1) than placeholders specified (0)">FINAL_TEXT</warning>, i);
          String sum = "first {}" + "second {}" + 1;
          LOG.info(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">sum</warning>, i);
        }
      }
      """.trimIndent())
  }

  fun `test log4j2 builder`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
         try {
           throw new RuntimeException();
         } catch (Throwable t) {
          LOG.atError().log(<warning descr="More arguments provided (2) than placeholders specified (1)">"'{}'"</warning>, "bar", new Exception());
          LOG.atError().log("'{}' '{}'", "bar", new Exception());
          LOG.atError().log("'{}'", "bar");
         }
        }
      }
      """.trimIndent())
  }
  fun `test slf4j disable slf4jToLog4J2Type`() {
    inspection.slf4jToLog4J2Type = LoggingPlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.NO

    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        void foo() {
          Logger logger = LoggerFactory.getLogger(X.class);
          logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">"string {} {}"</warning>, 1, new RuntimeException());
          logger.atError().log(<warning descr="Fewer arguments provided (0) than placeholders specified (1)">"{}"</warning>, new RuntimeException("test"));
          LoggerFactory.getLogger(X.class).atError().log(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">"{} {}"</warning>, 1, new RuntimeException("test"));
          LoggerFactory.getLogger(X.class).atError().log("{}", 1, new RuntimeException("test"));
        }
      }
      """.trimIndent())
  }

  fun `test variable`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
          private final static Logger logger = LoggerFactory.getLogger();
          private final static String con = "{}";

          private static void slf4jVariable(String number) {
              logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">number + "1 {} {}"</warning>, 1);
              logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">number + "1 {} {}"</warning>, 1);
              String t = "{}";
              logger.info(con + t + "1", 1);
              t = "1";
              logger.    info(con + t + "1", 1);
          }
          private static void slf4jVariable2(String number) {
              logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">number + "1 {} {}"</warning>, 1);
              logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">number + "1 {} {}"</warning>, 1);
              String t = "{}";
              logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">con + t + "1"</warning>, 1);
              logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">con + t + "1"</warning>, 1);
          }
      }
      """.trimIndent())
  }
  fun `test slf4j auto slf4jToLog4J2Type`() {
    inspection.slf4jToLog4J2Type = LoggingPlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.AUTO

    myFixture.addClass("""
      package org.apache.logging.slf4j;
      public interface Log4jLogger {
      }
    """.trimIndent())

    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        void foo() {
          Logger logger = LoggerFactory.getLogger(X.class);
          logger.info("string {} {}", 1, new RuntimeException());
          logger.atError().log("{}", new RuntimeException("test"));
          LoggerFactory.getLogger(X.class).atError().log("{} {}", 1, new RuntimeException("test"));
          LoggerFactory.getLogger(X.class).atError().log(<warning descr="More arguments provided (2) than placeholders specified (1)">"{}"</warning>, 1, new RuntimeException("test"));
        }
      }
      """.trimIndent())
  }
  fun `test slf4j builder`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      import org.slf4j.spi.*;
      class X {
          private final static Logger logger2 = LoggerFactory.getLogger(X.class);
          private final static LoggingEventBuilder builder =logger2.atError();

        void foo() {
          Logger logger = LoggerFactory.getLogger(X.class);
          LoggerFactory.getLogger(X.class).atError().log("{}", new RuntimeException("test"));
          LoggerFactory.getLogger(X.class).atError().log("{} {}", 1, new RuntimeException("test"));
          LoggerFactory.getLogger(X.class).atError().log(<warning descr="More arguments provided (2) than placeholders specified (1)">"{}"</warning>, 1, new RuntimeException("test"));

          builder.log(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">"{} {}"</warning>, 1);
        }
      }
      """.trimIndent())
  }

  fun `test formatted log4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.apache.logging.log4j.*;
      class X {
        private static final Logger LOG = LogManager.getFormatterLogger();
        void m() {
         try {
           throw new RuntimeException();
         } catch (Throwable t) {
            Logger LOG2 = LogManager.getFormatterLogger();
            LOG.info("My %s text", "test", t);
            LOG.info(<warning descr="Illegal format string specifier">"My %i text"</warning>, "test");
            LOG.info(<warning descr="More arguments provided (2) than placeholders specified (1)">"My %s text"</warning>, "test1", "test2");
            LOG2.info("My %s text, %s", "test1"); //skip because LOG2 is not final
            LogManager.getFormatterLogger().info(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">"My %s text, %s"</warning>, "test1");
          }
        }
      }
      """.trimIndent())
  }

  fun `test Log4j2 with exception in suppliers`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.apache.logging.log4j.*;
      import org.apache.logging.log4j.util.Supplier;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
         try {
           throw new RuntimeException();
         } catch (IllegalArgumentException | IllegalStateException t) {
             LOG.info(<warning descr="More arguments provided (3) than placeholders specified (1)">"test {}"</warning>, () -> "test", () -> "test", () -> t);
         } catch (Throwable t) {
             LOG.info("test {}", () -> "test", () -> t);
             LOG.info(<warning descr="More arguments provided (3) than placeholders specified (1)">"test {}"</warning>, () -> "test", () -> "test", () -> t);
             Supplier<Throwable> s = () -> t;
             LOG.info("test {}", () -> "test", s);
             Supplier<?> s2 = () -> t;
             LOG.info("test {}", () -> "test", s2);
             Supplier s3 = () -> t;
             LOG.info("test {}", () -> "test", s3);
             LOG.info("test {}", () -> "test", RuntimeException::new);
             LOG.info("test {}", () -> "test");
         }
        }
      }
      """.trimIndent())
  }

  fun `test slf4j with partial known strings`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      import java.util.Random;
      class X {
       Logger logger = LoggerFactory.getLogger(X.class);

       private static final String logText = "{} {}" + getSomething();
       private static final String logText2 = "{} {}" + 1 + "{}" + getSomething();
       private static final String logText3 = "{} {}" + 1 + "{}";

       private static String getSomething(){
         return new Random().nextBoolean() ? "{}" : "";
       }

       void m(String t) {
        logger.info("{} {}", 1, 2);
        logger.info(<warning descr="Fewer arguments provided (0) than placeholders specified (at least 2)">"{}" + t + 1 + "{}"</warning>);
        logger.info(<warning descr="Fewer arguments provided (0) than placeholders specified (at least 1)">"{}" + t + 1</warning>);
        logger.info(<warning descr="Fewer arguments provided (0) than placeholders specified (at least 1)">"{}" + t + "{}"</warning>);
        logger.info("{}" + t + "{}", 1, 2);
        logger.info("{}" + t + "{}", 1, 2, 3);
        String temp1 = "{} {}" + t;
        String temp = "{} {}" + t;
        logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">temp1</warning>, 1);
        logger.info(temp, 1, 2, 3);
        logger.info(logText, 1, 2, 3);
        logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">logText</warning>, 1);
        logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 3)">logText2</warning>, 1);
        logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (3)">logText3</warning>, 1);
        temp = "{}" + t;
        logger.info(temp , 1);
       }

       void m(int i, String s) {
        logger.info(<warning descr="Fewer arguments provided (0) than placeholders specified (1)">"test1 {}"</warning>);
        logger.info(<warning descr="Fewer arguments provided (0) than placeholders specified (at least 1)">"test1 {}" + s</warning>);
        logger.info(<warning descr="Fewer arguments provided (0) than placeholders specified (1)">"test1 {}" + i</warning>);
       }
      }
      """.trimIndent())
  }

  fun `test log4j builder with partial known strings`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.apache.logging.log4j.*;
      import java.util.Random;
      class Logging {
       private static final Logger LOG = LogManager.getLogger();
       private static final String logText = "{} {}" + getSomething();
       private static final String logText2 = "{} {}" + 1 + "{}" + getSomething();
       private static final String logText3 = "{} {}" + 1 + "{}";
       private static String getSomething(){
         return new Random().nextBoolean() ? "{}" : "";
       }
       static {
        LOG.atError().log(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">logText</warning>, 1);
        LOG.atError().log(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 4)">logText + logText2</warning>, 1);
       }
       public static void test(String t) {
        LogBuilder logger = LOG.atError();
        logger.log("{} {}", 1, 2);
        logger.log(<warning descr="Fewer arguments provided (0) than placeholders specified (at least 2)">"{}" + t + 1 + "{}"</warning>);
        logger.log(<warning descr="Fewer arguments provided (0) than placeholders specified (at least 1)">"{}" + t + 1</warning>);
        logger.log(<warning descr="Fewer arguments provided (0) than placeholders specified (at least 1)">"{}" + t + "{}"</warning>);
        logger.log("{}" + t + "{}", 1, 2);
        logger.log("{}" + t + "{}", 1, 2, 3);
        String temp = "{} {}" + t;
        String temp1 = "{} {}" + t;
        logger.log(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">temp1</warning>, 1);
        logger.log(temp, 1, 2, 3);
        logger.log(logText, 1, 2, 3);
        logger.log(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">logText</warning>, 1);
        logger.log(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 3)">logText2</warning>, 1);
        logger.log(<warning descr="Fewer arguments provided (1) than placeholders specified (3)">logText3</warning>, 1);
        temp = "{}" + t;
        logger.log(temp , 1);
       }
      }
      """.trimIndent())
  }

  fun `test formatted log4j with partial known strings`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.apache.logging.log4j.*;
      class Logging {
       private static final Logger logger = LogManager.getFormatterLogger();
       public static void test(String t) {
        logger.info(<warning descr="Fewer arguments provided (0) than placeholders specified (at least 1)">"%s" + t + 1 + "%s "</warning>);
        logger.info(<warning descr="Fewer arguments provided (0) than placeholders specified (at least 2)">"%s %s" + t + 1</warning>);
        logger.info("%s" + t + "%s", 1);
        logger.atDebug().log("%s t", 1);
        final LogBuilder logBuilder = logger.atDebug();
        logBuilder.log(<warning descr="More arguments provided (2) than placeholders specified (1)">"%s t"</warning>, 1, 2); //warn
        logger.info(<warning descr="More arguments provided (2) than placeholders specified (1)">"%s t"</warning>, 2, 2); //warn
        logger.info("%s t", 2);
        logger.info(<warning descr="More arguments provided (2) than placeholders specified (1)">"%s t"</warning>, 2, 3); //warn
       }
      }
      """.trimIndent())
  }

  fun `test log4j with partial known strings`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.apache.logging.log4j.*;
      class Logging {
       private static final Logger logger = LogManager.getLogger();
       public static void test(String t) {
         logger.info(<warning descr="Fewer arguments provided (0) than placeholders specified (at least 2)">"{}" + t + 1 + "{}"</warning>);
         logger.info(<warning descr="Fewer arguments provided (0) than placeholders specified (at least 2)">"{}" + t + 1 + "{}"</warning>, new RuntimeException());
         logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 3)">"{} {}" + t + 1 + "{}"</warning>, 1, new RuntimeException());
         logger.info("{}" + t + 1 + "{}", 1, new RuntimeException());
         logger.info("{}" + t + 1 + "{}", 1, 1);
       }
      }
      """.trimIndent())
  }

  fun `test many variables`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      import java.util.Random;
      class X {
       Logger LOGGER = LoggerFactory.getLogger(X.class);

           void m(String s) {
               String a1 = " {} " + s;
               String a2 = (a1 + a1) + a1 + a1 + a1 + a1 + a1 + a1 + a1;
               String a3 = a2 + a2 + a2 + a2 + a2 + a2 + a2 + a2 + a2;
               String a4 = a3 + a3 + a3 + a3 + a3 + a3 + a3 + a3 + a3;
               String a5 = a4 + a4 + a4 + a4 + a4 + a4 + a4 + a4 + a4;
               String a6 = a5 + a5 + a5 + a5 + a5 + a5 + a5 + a5 + a5;
               String a7 = a6 + a6 + a6 + a6 + a6 + a6 + a6 + a6 + a6;
               String a8 = a7 + a7 + a7 + a7 + a7 + a7 + a7 + a7 + a7;
               String a9 = a8 + a8 + a8 + a8 + a8 + a8 + a8 + a8 + a8;
               String a10 = a9 + a9 + a9 + a9 + a9+ a9 + a9 + a9 + a9;
               LOGGER.info("abd" + a10, 1);
               LOGGER.info("abd" + a10, 1);
               LOGGER.info("abd" + a10, 1);
               LOGGER.info("abd" + a10, 1);
               LOGGER.info("abd" + a10, 1);
               LOGGER.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 9)">"abd" + a2</warning>, 1);
               LOGGER.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 9)">"abd" + a2</warning>, 1);
               LOGGER.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 9)">"abd" + a2</warning>, 1);
               LOGGER.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 9)">"abd" + a2</warning>, 1);
               LOGGER.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 9)">"abd" + a2</warning>, 1);
           }
      }
      """.trimIndent())
  }

  fun `test akka`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import akka.event.LoggingAdapter;
      
      class MyActor {

        public void preStart(LoggingAdapter log) {
            log.info("Starting 1 {} {}", 1, new RuntimeException());
            log.log(2,"Starting 2 {} {}", 1, new RuntimeException());
            log.log(2,<warning descr="Fewer arguments provided (1) than placeholders specified (2)">"Starting 3 {} {}"</warning>, 1);  //warn
            log.log(2,<warning descr="More arguments provided (3) than placeholders specified (2)">"Starting 4 {} {}"</warning>, 1, 2, 3);  //warn
            log.error(new RuntimeException(), "Starting 5 {} {}", 1, 2);
            log.error(new RuntimeException(), <warning descr="More arguments provided (3) than placeholders specified (2)">"Starting 6 {} {}"</warning>, 1, 2, 3);  //warn
            log.error( <warning descr="More arguments provided (2) than placeholders specified (1)">"Starting 7 {3} {}"</warning>, 1, 2);  //warn
            log.error( new RuntimeException(), <warning descr="Fewer arguments provided (1) than placeholders specified (2)">"Starting 8 {} {}"</warning>, 1); //warn
            log.error( "Starting 9 \\{} {}", 1, 2);
        }
      }
      """.trimIndent())
  }
}


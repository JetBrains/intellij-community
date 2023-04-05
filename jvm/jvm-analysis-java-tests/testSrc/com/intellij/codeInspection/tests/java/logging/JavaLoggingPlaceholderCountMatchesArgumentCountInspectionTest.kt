package com.intellij.codeInspection.tests.java.logging

import com.intellij.codeInspection.logging.LoggingPlaceholderCountMatchesArgumentCountInspection
import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.logging.LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager

class JavaLoggingPlaceholderCountMatchesArgumentCountInspectionTest : LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase() {

  fun `test log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
       private static final Logger LOG = LogManager.getLogger();
       void m(int i) {
         LOG.info(/*Fewer arguments provided (1) than placeholders specified (3)*/"test? {}{}{}"/**/, i);
         LogManager.getLogger().fatal(/*More arguments provided (1) than placeholders specified (0)*/"test"/**/, i);
         LOG.error(() -> "", new Exception());
       }
     }
    """.trimIndent().commentsToWarn())
  }

  fun `test log4j2LogBuilder`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
       private static final Logger LOG = LogManager.getLogger();
       void m(int i) {
         LOG.atInfo().log(/*Fewer arguments provided (1) than placeholders specified (3)*/"test? {}{}{}"/**/, i);
         LOG.atFatal().log(/*More arguments provided (2) than placeholders specified (0)*/"test "/**/, i, i);
         LOG.atError().log(/*More arguments provided (1) than placeholders specified (0)*/"test? "/**/, () -> "");
       }
     }
    """.trimIndent().commentsToWarn())
  }

  fun `test 1 exception and 1 placeholder`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        void foo() {
          RuntimeException e = new RuntimeException();
          LoggerFactory.getLogger(X.class).info(/*Fewer arguments provided (0) than placeholders specified (1)*/"this: {}"/**/, e);
        }
      }    
        """.trimIndent().commentsToWarn())
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
        """.trimIndent().commentsToWarn())
  }

  fun `test 1 exception and 3 placeholder`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        void foo() {
          RuntimeException e = new RuntimeException();
          LoggerFactory.getLogger(X.class).info(/*Fewer arguments provided (1) than placeholders specified (3)*/"1: {} {} {}"/**/, 1, e);
        }
      }
        """.trimIndent().commentsToWarn())
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
        """.trimIndent().commentsToWarn())
  }

  fun `test more placeholders`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        void foo() {
          Logger logger = LoggerFactory.getLogger(X.class);
          logger.info(/*Fewer arguments provided (1) than placeholders specified (2)*/"string {}{}"/**/, 1);
        }
      }
        """.trimIndent().commentsToWarn())
  }

  fun `test fewer placeholders`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        void foo() {
          Logger logger = LoggerFactory.getLogger(X.class);
          logger.info(/*More arguments provided (1) than placeholders specified (0)*/"string"/**/, 1);
        }
      }
        """.trimIndent().commentsToWarn())
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
        """.trimIndent().commentsToWarn())
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
        """.trimIndent().commentsToWarn())
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
         """.trimIndent().commentsToWarn())
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
         """.trimIndent().commentsToWarn())
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
         """.trimIndent().commentsToWarn())
  }

  fun `test constant`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        Logger LOG = LoggerFactory.getLogger(X.class);
        private static final String message = "HELLO {}";
        void m() {
          LOG.info(/*Fewer arguments provided (0) than placeholders specified (1)*/message/**/);
        }
      }
      """.trimIndent().commentsToWarn())
  }
  fun `test non constant string`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        Logger LOG = LoggerFactory.getLogger(X.class);
        private static final String S = "{}";
        void m() {
          LOG.info(/*Fewer arguments provided (0) than placeholders specified (3)*/S +"{}" + (1 + 2) + '{' + '}' +Integer.class/**/);
        }
      }
      """.trimIndent().commentsToWarn())
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
      """.trimIndent().commentsToWarn())
  }

  fun `test escaping 2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        Logger LOG = LoggerFactory.getLogger(X.class);
        void m() {
          LOG.info(/*More arguments provided (2) than placeholders specified (1)*/"Created key {}\\{}"/**/, 1, 2);
        }
      }
      """.trimIndent().commentsToWarn())
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
      """.trimIndent().commentsToWarn())
  }

  fun `test log4j2 with text variables`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final String FINAL_TEXT = "const";
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          String text = "test {}{}{}";
          LOG.info(/*Fewer arguments provided (1) than placeholders specified (3)*/text/**/, i);
          final String text2 = "test ";
          LOG.fatal(/*More arguments provided (1) than placeholders specified (0)*/text2/**/, i);
          LOG.fatal(/*Fewer arguments provided (1) than placeholders specified (6)*/text + text/**/, i);
          LOG.fatal(/*Fewer arguments provided (1) than placeholders specified (18)*/text + text + text + text + text + text/**/, i);
          LOG.info(/*More arguments provided (1) than placeholders specified (0)*/FINAL_TEXT/**/, i);
          String sum = "first {}" + "second {}" + 1;
          LOG.info(/*Fewer arguments provided (1) than placeholders specified (2)*/sum/**/, i);
        }
      }
      """.trimIndent().commentsToWarn())
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
          LOG.atError().log(/*More arguments provided (2) than placeholders specified (1)*/"'{}'"/**/, "bar", new Exception());
          LOG.atError().log("'{}' '{}'", "bar", new Exception());
          LOG.atError().log("'{}'", "bar");
         }
        }
      }
      """.trimIndent().commentsToWarn())
  }
  fun `test slf4j disable slf4jToLog4J2Type`() {
    val currentProfile = ProjectInspectionProfileManager.getInstance(project).currentProfile
    val inspectionTool = currentProfile.getInspectionTool(inspection.shortName, project)
    val tool = inspectionTool?.tool
    if (tool is LoggingPlaceholderCountMatchesArgumentCountInspection) {
      tool.slf4jToLog4J2Type = LoggingPlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.NO
    }
    else {
      fail()
    }
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class X {
        void foo() {
          Logger logger = LoggerFactory.getLogger(X.class);
          logger.info(/*Fewer arguments provided (1) than placeholders specified (2)*/"string {} {}"/**/, 1, new RuntimeException());
          logger.atError().log(/*Fewer arguments provided (0) than placeholders specified (1)*/"{}"/**/, new RuntimeException("test"));
          LoggerFactory.getLogger(X.class).atError().log(/*Fewer arguments provided (1) than placeholders specified (2)*/"{} {}"/**/, 1, new RuntimeException("test"));
          LoggerFactory.getLogger(X.class).atError().log("{}", 1, new RuntimeException("test"));
        }
      }
      """.trimIndent().commentsToWarn())
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
      """.trimIndent().commentsToWarn())
  }
  fun `test slf4j auto slf4jToLog4J2Type`() {
    val currentProfile = ProjectInspectionProfileManager.getInstance(project).currentProfile
    val inspectionTool = currentProfile.getInspectionTool(inspection.shortName, project)
    val tool = inspectionTool?.tool
    if (tool is LoggingPlaceholderCountMatchesArgumentCountInspection) {
      tool.slf4jToLog4J2Type = LoggingPlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.AUTO
    }
    else {
      fail()
    }
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
          LoggerFactory.getLogger(X.class).atError().log(/*More arguments provided (2) than placeholders specified (1)*/"{}"/**/, 1, new RuntimeException("test"));
        }
      }
      """.trimIndent().commentsToWarn())
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
          LoggerFactory.getLogger(X.class).atError().log(/*More arguments provided (2) than placeholders specified (1)*/"{}"/**/, 1, new RuntimeException("test"));

          builder.log(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">"{} {}"</warning>, 1);
        }
      }
      """.trimIndent().commentsToWarn())
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
            LOG.info(/*Illegal format string specifier*/"My %i text"/**/, "test");
            LOG.info(/*More arguments provided (2) than placeholders specified (1)*/"My %s text"/**/, "test1", "test2");
            LOG2.info("My %s text, %s", "test1"); //skip because LOG2 is not final
            LogManager.getFormatterLogger().info(/*Fewer arguments provided (1) than placeholders specified (2)*/"My %s text, %s"/**/, "test1");
          }
        }
      }
      """.trimIndent().commentsToWarn())
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
             LOG.info(/*More arguments provided (3) than placeholders specified (1)*/"test {}"/**/, () -> "test", () -> "test", () -> t);
         } catch (Throwable t) {
             LOG.info("test {}", () -> "test", () -> t);
             LOG.info(/*More arguments provided (3) than placeholders specified (1)*/"test {}"/**/, () -> "test", () -> "test", () -> t);
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
      """.trimIndent().commentsToWarn())
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
        logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 2)*/"{}" + t + 1 + "{}"/**/);
        logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 1)*/"{}" + t + 1/**/);
        logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 1)*/"{}" + t + "{}"/**/);
        logger.info("{}" + t + "{}", 1, 2);
        logger.info("{}" + t + "{}", 1, 2, 3);
        String temp1 = "{} {}" + t;
        String temp = "{} {}" + t;
        logger.info(/*Fewer arguments provided (1) than placeholders specified (at least 2)*/temp1/**/, 1);
        logger.info(temp, 1, 2, 3);
        logger.info(logText, 1, 2, 3);
        logger.info(/*Fewer arguments provided (1) than placeholders specified (at least 2)*/logText/**/, 1);
        logger.info(/*Fewer arguments provided (1) than placeholders specified (at least 3)*/logText2/**/, 1);
        logger.info(/*Fewer arguments provided (1) than placeholders specified (3)*/logText3/**/, 1);
        temp = "{}" + t;
        logger.info(temp , 1);
       }

       void m(int i, String s) {
        logger.info(/*Fewer arguments provided (0) than placeholders specified (1)*/"test1 {}"/**/);
        logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 1)*/"test1 {}" + s/**/);
        logger.info(/*Fewer arguments provided (0) than placeholders specified (1)*/"test1 {}" + i/**/);
       }
      }
      """.trimIndent().commentsToWarn())
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
        LOG.atError().log(/*Fewer arguments provided (1) than placeholders specified (at least 2)*/logText/**/, 1);
        LOG.atError().log(/*Fewer arguments provided (1) than placeholders specified (at least 4)*/logText + logText2/**/, 1);
       }
       public static void test(String t) {
        LogBuilder logger = LOG.atError();
        logger.log("{} {}", 1, 2);
        logger.log(/*Fewer arguments provided (0) than placeholders specified (at least 2)*/"{}" + t + 1 + "{}"/**/);
        logger.log(/*Fewer arguments provided (0) than placeholders specified (at least 1)*/"{}" + t + 1/**/);
        logger.log(/*Fewer arguments provided (0) than placeholders specified (at least 1)*/"{}" + t + "{}"/**/);
        logger.log("{}" + t + "{}", 1, 2);
        logger.log("{}" + t + "{}", 1, 2, 3);
        String temp = "{} {}" + t;
        String temp1 = "{} {}" + t;
        logger.log(/*Fewer arguments provided (1) than placeholders specified (at least 2)*/temp1/**/, 1);
        logger.log(temp, 1, 2, 3);
        logger.log(logText, 1, 2, 3);
        logger.log(/*Fewer arguments provided (1) than placeholders specified (at least 2)*/logText/**/, 1);
        logger.log(/*Fewer arguments provided (1) than placeholders specified (at least 3)*/logText2/**/, 1);
        logger.log(/*Fewer arguments provided (1) than placeholders specified (3)*/logText3/**/, 1);
        temp = "{}" + t;
        logger.log(temp , 1);
       }
      }
      """.trimIndent().commentsToWarn())
  }

  fun `test formatted log4j with partial known strings`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.apache.logging.log4j.*;
      class Logging {
       private static final Logger logger = LogManager.getFormatterLogger();
       public static void test(String t) {
        logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 1)*/"%s" + t + 1 + "%s "/**/);
        logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 2)*/"%s %s" + t + 1/**/);
        logger.info("%s" + t + "%s", 1);
        logger.atDebug().log("%s t", 1);
        final LogBuilder logBuilder = logger.atDebug();
        logBuilder.log(<warning descr="More arguments provided (2) than placeholders specified (1)">"%s t"</warning>, 1, 2); //warn
        logger.info(<warning descr="More arguments provided (2) than placeholders specified (1)">"%s t"</warning>, 2, 2); //warn
        logger.info("%s t", 2);
        logger.info(<warning descr="More arguments provided (2) than placeholders specified (1)">"%s t"</warning>, 2, 3); //warn
       }
      }
      """.trimIndent().commentsToWarn())
  }

  fun `test log4j with partial known strings`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.apache.logging.log4j.*;
      class Logging {
       private static final Logger logger = LogManager.getLogger();
       public static void test(String t) {
         logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 2)*/"{}" + t + 1 + "{}"/**/);
         logger.info(/*Fewer arguments provided (0) than placeholders specified (at least 2)*/"{}" + t + 1 + "{}"/**/, new RuntimeException());
         logger.info(/*Fewer arguments provided (1) than placeholders specified (at least 3)*/"{} {}" + t + 1 + "{}"/**/, 1, new RuntimeException());
         logger.info("{}" + t + 1 + "{}", 1, new RuntimeException());
         logger.info("{}" + t + 1 + "{}", 1, 1);
       }
      }
      """.trimIndent().commentsToWarn())
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
               LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 9)*/"abd" + a2/**/, 1);
               LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 9)*/"abd" + a2/**/, 1);
               LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 9)*/"abd" + a2/**/, 1);
               LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 9)*/"abd" + a2/**/, 1);
               LOGGER.info(/*Fewer arguments provided (1) than placeholders specified (at least 9)*/"abd" + a2/**/, 1);
           }
      }
      """.trimIndent().commentsToWarn())
  }
}


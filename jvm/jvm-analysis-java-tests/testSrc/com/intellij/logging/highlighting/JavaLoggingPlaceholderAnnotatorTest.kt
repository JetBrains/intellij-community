package com.intellij.logging.highlighting

import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingPlaceholderAnnotatorTestBase

class JavaLoggingPlaceholderAnnotatorTest : LoggingPlaceholderAnnotatorTestBase() {
  override val fileName: String = "Logging.java"

  fun `test log4j2 default log`() = doTest(
    """
       import org.apache.logging.log4j.*;
       class Logging {
           public static final Logger LOG = LogManager.getLogger();
           void m(Integer fst, Integer snd) {
               LOG.debug("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
               LOG.info("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
               LOG.trace("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
               LOG.warn("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
               LOG.error("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
               LOG.fatal("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
               LOG.log(Level.ALL, "<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
           }
       }""")

  fun `test log4j2 formatter log`() = doTest("""
      import org.apache.logging.log4j.*;
      
      class Logging {
          public static final Logger LOG = LogManager.getFormatterLogger();
          void m(Integer fst, Integer snd) {
              LOG.debug("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd);
              LOG.info("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd);
              LOG.trace("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd);
              LOG.warn("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd);
              LOG.error("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd);
              LOG.fatal("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd);
              LOG.log(Level.ALL, "<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd);
          }
      }
    """.trimIndent())

  fun `test log4j2 default log builder`() = doTest("""
      import org.apache.logging.log4j.*;
      class Logging {
          public static final Logger LOG = LogManager.getLogger();
          void m(Integer fst, Integer snd) {
              LOG.atDebug().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
              LOG.atInfo().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
              LOG.atWarn().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
              LOG.atError().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
              LOG.atFatal().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
              LOG.atTrace().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
              LOG.atLevel(Level.ALL).log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
          }
      }
    """.trimIndent())

  fun `test log4j2 formatter log builder`() = doTest("""
      import org.apache.logging.log4j.*;
      class Logging {
          public static final Logger LOG = LogManager.getFormatterLogger();
          void m(Integer fst, Integer snd) {
              LOG.atDebug().log("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd);
              LOG.atInfo().log("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd);
              LOG.atWarn().log("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd);
              LOG.atError().log("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd);
              LOG.atFatal().log("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd);
              LOG.atTrace().log("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd);
              LOG.atLevel(Level.ALL).log("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd);
          }
      }
    """.trimIndent())


  fun `test log4j2 formatter log with previous placeholder`() = doTest("""
    import org.apache.logging.log4j.*;
      class Logging {
          public static final Logger LOG = LogManager.getFormatterLogger();
          void m(Integer fst, Integer snd) {
              LOG.atDebug().log("<placeholder>%s</placeholder> <placeholder>%<s</placeholder>", fst);
              LOG.debug("<placeholder>%s</placeholder> <placeholder>%<s</placeholder>", fst);
          }
      }
    ""${'"'}.trimIndent())
  """.trimIndent())

  fun `test log4j2 formatter log with numbered placeholder`() {
    val dollar = "$"
    doTest("""
      import org.apache.logging.log4j.*;
      class Logging {
          private static final Logger LOG = LogManager.getFormatterLogger();

          void foo(Integer fst, Integer snd, Integer thrd) {
              LOG.info("<placeholder>%1${dollar}s</placeholder> <placeholder>%2${dollar}s</placeholder> <placeholder>%3${dollar}s</placeholder>", fst, snd, thrd);
              LOG.info("<placeholder>%2${dollar}s</placeholder> <placeholder>%3${dollar}s</placeholder> <placeholder>%1${dollar}s</placeholder> <placeholder>%s</placeholder> <placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd, thrd);
          }
      }
    """.trimIndent())
  }

  fun `test log4j2 respects exception`() = doTest("""
      import org.apache.logging.log4j.*;
      class Logging {
        public static final Logger LOG = LogManager.getLogger();
        void m(Integer fst) {
          LOG.info("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, new Exception());
          LOG.info("{}", new Exception());
        }
     }
    """.trimIndent())

  fun `test log4j2 builder respects exception`() = doTest("""
      import org.apache.logging.log4j.*;
      class Logging {
        public static final Logger LOG = LogManager.getLogger();
        void m(Integer fst) {
          LOG.atInfo().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, new Exception());
          LOG.atInfo().log("<placeholder>{}</placeholder>", new Exception());
          LOG.atInfo().withThrowable(new Exception()).log("{}");
        }
     }
      """.trimIndent())


  fun `test slf4j default log`() = doTest("""
      import org.slf4j.*;

      class Logging {
          public static final Logger LOG = LoggerFactory.getLogger(Logging.class);
          void m(Integer fst, Integer snd) {
              LOG.debug("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
              LOG.info("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
              LOG.trace("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
              LOG.warn("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
              LOG.error("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
          }
      }
    """.trimIndent())

  fun `test slf4j default log builder`() = doTest("""
      import org.slf4j.*;
      
      class Logging {
          public static final Logger LOG = LoggerFactory.getLogger(Logging.class);
          void m(Integer fst, Integer snd) {
              LOG.atInfo().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
              LOG.atDebug().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
              LOG.atWarn().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
              LOG.atError().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
              LOG.atTrace().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd);
          }
      }
    """.trimIndent())

  fun `test slf4j default log builder with setMessage and addArgument`() = doTest("""
      import org.slf4j.*;

      class Logging {
          public static final Logger LOG = LoggerFactory.getLogger(Logging.class);
          void m(Integer fst, Integer snd) {
              LOG.atInfo().addArgument(fst).addArgument(snd).setMessage("<placeholder>{}</placeholder> <placeholder>{}</placeholder>").log();
              LOG.atInfo().addArgument(fst).addArgument(snd).setMessage("{} {}").setMessage("<placeholder>{}</placeholder> <placeholder>{}</placeholder>").log();
              LOG.atInfo().addArgument(fst).addArgument(snd).log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>");
              LOG.atInfo().addArgument(fst).log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", snd);
          }
      }
    """.trimIndent())


  fun `test slf4j respects exception`() = doTest("""
      import org.slf4j.*;

      class Logging {
          public static final Logger LOG = LoggerFactory.getLogger(Logging.class);
          void m(Integer fst, Integer snd) {
              LOG.info("<placeholder>{}</placeholder> {}", fst, new Exception());
              LOG.info("{}", new Exception());
          }
      }
    """.trimIndent())


  fun `test slf4j builder respects exception`() = doTest(
    """
      import org.slf4j.*;

      class Logging {
          public static final Logger LOG = LoggerFactory.getLogger(Logging.class);
          void m(Integer fst, Integer snd) {
              LOG.atInfo().log("<placeholder>{}</placeholder> {}", fst, new Exception());
              LOG.atInfo().log("{}", new Exception());
              LOG.atInfo().addArgument(fst).addArgument(new Exception()).setMessage("<placeholder>{}</placeholder> <placeholder>{}</placeholder>").log();
              LOG.atInfo().addArgument(fst).setCause(new Exception()).setMessage("<placeholder>{}</placeholder> {}").log();
              LOG.atInfo().addArgument(fst).setCause(new Exception()).log("<placeholder>{}</placeholder> {}");
          }
      }
    """.trimIndent()
  )

  fun `test support multiline string`() {
    val multilineString = "\"\"\"       \n" +
                          "    <placeholder>{}</placeholder> <placeholder>{}</placeholder> {}\n  \n" +
                          "      \n \n  \"\"\""

    doTest("""
    import org.slf4j.*;

    class Logging {
        public static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(Integer fst, Integer snd) {
            LOG.info($multilineString, fst, snd);
        }
    }
  """.trimIndent())
  }

  fun `test does not support string concatenation`() = doTest("""
    import org.slf4j.*

    class Logging {
        public static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(Integer fst, Integer snd) {
            LOG.info("{} {}" + "  foo bar", fst, snd);
        }
    }
  """.trimIndent())

  fun `test lazy init`() = doTest("""
      import org.apache.logging.log4j.LogBuilder;
      import org.apache.logging.log4j.LogManager;
      import org.apache.logging.log4j.Logger;

      class LazyInitializer {
      
          static class StaticInitializer {
              private static final Logger log;
      
              static {
                  log = LogManager.getLogger();
              }
      
              public StaticInitializer() {
                log.info("<placeholder>{}</placeholder>", 1);
              }
          }
      
          static class StaticInitializerBuilder {
              private static final LogBuilder log;
      
              static {
                  log = LogManager.getLogger().atDebug();
              }
      
              public StaticInitializerBuilder() {
                log.log("<placeholder>{}</placeholder>", "arg");
              }
          }
      
          static class StaticInitializerBuilder2 {
              private static final LogBuilder log;
      
              static {
                  if (1 == 1) {
                      log = LogManager.getLogger().atDebug();
                  } else {
                      log = LogManager.getFormatterLogger().atDebug();
                  }
              }
      
              public StaticInitializerBuilder2() {
                  log.log("{}", 1);
              }
          }
      
          static class ConstructorInitializer {
              private final Logger log;
      
      
              public ConstructorInitializer() {
                  log = LogManager.getLogger();
              }
      
              public ConstructorInitializer(int i) {
                  log = LogManager.getLogger();
              }
      
              public void test() {
                log.info("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", 2, 1);
              }
          }
      
          static class ConstructorInitializer2 {
              private final Logger log;
      
      
              public ConstructorInitializer2() {
                  log = LogManager.getFormatterLogger();
              }
      
              public ConstructorInitializer2(int i) {
                  log = LogManager.getLogger();
              }
      
              public void test() {
                  log.info("{}", 1);
              }
          }
      }
  """.trimIndent())

}
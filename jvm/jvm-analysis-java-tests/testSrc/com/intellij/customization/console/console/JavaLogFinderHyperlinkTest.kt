package com.intellij.customization.console.console

import com.intellij.jvm.analysis.internal.testFramework.internal.LogFinderHyperlinkTestBase
import com.intellij.jvm.analysis.internal.testFramework.internal.LogItem
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils
import com.intellij.openapi.editor.LogicalPosition

class JavaLogFinderHyperlinkTest : LogFinderHyperlinkTestBase() {
  fun testSimpleLog4j2() {
    LoggingTestUtils.addLog4J(myFixture)
    checkColumnFinderJava(
      fileName = "Log4jk",
      classText = """
package com.example.loggingjava.java;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Log4j {

    private static final Logger logger = LogManager.getLogger();

    public static void main(String[] args) {
        System.out.println("1");
        log4j();
        log4j2(1);
    }

    private static void log4j2(int i) {
        String message = "Second" + " Request {}" + i;
        logger.error(message, i);
    }

    private static void log4j() {
        logger.info("First request");
    }
}
""".trimIndent(),
      logItems = listOf(
        LogItem("java.exe", null),
        LogItem("1", null),
        LogItem("19:50:03.422 [main] INFO c.e.l.j.Log4j - First request", LogicalPosition(21, 8)),
        LogItem("19:50:03.430 [main] ERROR com.example.loggingjava.java.Log4j - Second Request 11", LogicalPosition(17, 8)),
      )
    )
  }

  fun testSimpleSlf4j() {
    LoggingTestUtils.addSlf4J(myFixture)
    checkColumnFinderJava(
      fileName = "Slf4J",
      classText = """
package com.example.loggingjava.java;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Slf4J {
    private final static Logger log = LoggerFactory.getLogger(Slf4J.class);

    public static void main(String[] args) {
        System.out.println(2);
        log1(1);
        log2(2);
    }

    private static void log1(int i) {
        String msg = getMsg(i);
        log.info(msg);
    }

    private static void log2(int i) {
        String msg = "log2" + i;
        log.info(msg);
    }

    private static String getMsg(int i) {
        return "test" + i;
    }
}
""".trimIndent(),
      logItems = listOf(
        LogItem("java.exe", null),
        LogItem("1", null),
        LogItem("20:37:13.351 [main] INFO com.example.loggingjava.java.Slf4J - test1", LogicalPosition(5, 19)),
        LogItem("20:37:13.356 [main] INFO com.e.l.j.Slf4J - log22", LogicalPosition(21, 8)),
      )
    )
  }

  fun testIdeaLog() {
    LoggingTestUtils.addIdeaLog(myFixture)
    checkColumnFinderJava(
      fileName = "IdeaLog",
      classText = """
package com.example.loggingjava.java;

import com.intellij.openapi.diagnostic.Logger;

public final class IdeaLog {

    public static void start(Logger log) {
        System.out.println(2);
        log1(1, log);
        log2(2, log);
    }

    private static void log1(int i, Logger log) {
        String msg = getMsg(i);
        log.info(msg);
    }

    private static void log2(int i, Logger log) {
        String msg = "log2" + i;
        log.info(msg);
    }

    private static String getMsg(int i) {
        return "test" + i;
    }
}
""".trimIndent(),
      logItems = listOf(
        LogItem("java.exe", null),
        LogItem("1", null),
        LogItem("20:37:13.351 [main] INFO com.example.loggingjava.java.IdeaLog - test1", LogicalPosition(4, 19)),
        LogItem("20:37:13.356 [main] INFO com.e.l.j.IdeaLog - log22", LogicalPosition(19, 8)),
      )
    )
  }

  fun testSlf4JFluentApi() {
    LoggingTestUtils.addSlf4J(myFixture)
    checkColumnFinderJava(
      fileName = "Slf4JFluent",
      classText = """
package com.example.loggingjava.java;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Slf4JFluent {
    private final static Logger log = LoggerFactory.getLogger(Slf4JFluent.class);

    public static void main(String[] args) {
        System.out.println(2);
        log1(1);
        log2(2);
    }

    private static void log1(int i) {
        String msg = "test" + i;
        log.atInfo()
                .setMessage(msg)
                .log();
    }

    private static void log2(int i) {
        String msg = "log2" + i;
        log.atInfo()
                .log(msg);
    }
}
""".trimIndent(),
      logItems = listOf(
        LogItem("java.exe", null),
        LogItem("1", null),
        LogItem("com.example.log.java.SomethingSimilarToClass", null),
        LogItem("20:13:25.878 [main] INFO com.example.log.java.Slf4JFluent - test1", LogicalPosition(16, 8)),
        LogItem("20:13:25.883 [main] INFO com.example.logging.java.Slf4JFluent - log22", LogicalPosition(23, 8)),
      )
    )
  }

  fun testSkipException() {
    LoggingTestUtils.addSlf4J(myFixture)
    checkColumnFinderJava(
      fileName = "EmptySpringApplication",
      classText = """
package org.example.emptyspring;

import org.slf4j.Logger;

public final class EmptySpringApplication {


    private static final Logger log = org.slf4j.LoggerFactory.getLogger(EmptySpringApplication.class);

    public static void main(String[] args) {
        request2("1");
    }

    private static void request2(Object number) {
        log.info("new request {}", number);
        throw new RuntimeException();
    }
}
""".trimIndent(),
      logItems = listOf(
        LogItem("java.exe", null),
        LogItem("1", null),
        LogItem("10:27:22.721 [main] INFO org.example.emptyspring.EmptySpringApplication -- new request 1\n", LogicalPosition(14, 8)),
        LogItem("Exception in thread \"main\" java.lang.RuntimeException\n", null),
        LogItem("\tat org.example.emptyspring.EmptySpringApplication.request2(EmptySpringApplication.java:16)\n", null),
      )
    )
  }
}
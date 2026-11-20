// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.maturity;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
@SuppressWarnings("ThrowablePrintedToSystemOut")
public class ThrowablePrintedToSystemOutInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new ThrowablePrintedToSystemOutInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doMemberTest("""
                   void foo() {
                     final RuntimeException x = new RuntimeException();
                     System.out.println(/*'Throwable' argument 'x' to 'System.out.println()' call*/x/**/);
                   }
                   """);
  }

  @Test
  public void testEvenSimpler() {
    inspectionFixture.get().doStatementTest("System.out.println(/*'Throwable' argument 'new RuntimeException()' to 'System.out.println()' call*/new RuntimeException()/**/);");
  }

  @Test
  public void testSimpleLogFix() {
    addSlf4j();

    inspectionFixture.get().doTest(
        """
        class Test{
          void foo() {
            final RuntimeException x = new RuntimeException();
            System.out.println(/*'Throwable' argument 'x' to 'System.out.println()' call*/x/*_*//**/);
          }
        }
        """);

    inspectionFixture.get().checkQuickFix("Convert 'System.out' call to call of 'Slf4j'",
        """
        import org.slf4j.Logger;
        import org.slf4j.LoggerFactory;
        
        class Test{
            private static final Logger log = LoggerFactory.getLogger(Test.class);
        
            void foo() {
            final RuntimeException x = new RuntimeException();
            log.error("e: ", x);
          }
        }
        """);
  }

  private void addSlf4j() {
    inspectionFixture.get().addEnvironmentClass("""
        package org.slf4j;
        @SuppressWarnings("ALL") public class LoggerFactory {
        public static Logger getLogger(Class clazz) { return null; }
        }
        public interface Logger {
           void error(String format, Object... arguments);
        }
    """);
  }

  @Test
  public void testSimpleExistedLogFix() {
    addSlf4j();

    inspectionFixture.get().doTest(
        """
        import org.slf4j.Logger;
        import org.slf4j.LoggerFactory;
        
        class Test{
          private static final Logger logger = LoggerFactory.getLogger(Test.class);
        
          void foo() {
            final RuntimeException x = new RuntimeException();
            System.out.println(/*'Throwable' argument 'x' to 'System.out.println()' call*/x/*_*//**/);
          }
        }
        """);

    inspectionFixture.get().checkQuickFix("Convert 'System.out' call to call of 'Slf4j'",
        """
        import org.slf4j.Logger;
        import org.slf4j.LoggerFactory;
        
        class Test{
          private static final Logger logger = LoggerFactory.getLogger(Test.class);
      
          void foo() {
            final RuntimeException x = new RuntimeException();
            logger.error("e: ", x);
          }
        }
        """);
  }
}

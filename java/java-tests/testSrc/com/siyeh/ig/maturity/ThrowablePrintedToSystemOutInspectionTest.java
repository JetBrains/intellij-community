// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("ThrowablePrintedToSystemOut")
public class ThrowablePrintedToSystemOutInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doMemberTest("""
                   void foo() {
                     final RuntimeException x = new RuntimeException();
                     System.out.println(/*'Throwable' argument 'x' to 'System.out.println()' call*/x/**/);
                   }
                   """);
  }

  public void testEvenSimpler() {
    doStatementTest("System.out.println(/*'Throwable' argument 'new RuntimeException()' to 'System.out.println()' call*/new RuntimeException()/**/);");
  }

  public void testSimpleLogFix() {
    addSlf4j();

    doTest(
        """
        class Test{
          void foo() {
            final RuntimeException x = new RuntimeException();
            System.out.println(/*'Throwable' argument 'x' to 'System.out.println()' call*/x/*_*//**/);
          }
        }
        """);

    checkQuickFix("Convert 'System.out' call to call of 'Slf4j'",
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
    addEnvironmentClass("""
        package org.slf4j;
        @SuppressWarnings("ALL") public class LoggerFactory {
        public static Logger getLogger(Class clazz) { return null; }
        }
        public interface Logger {
           void error(String format, Object... arguments);
        }
    """);
  }

  public void testSimpleExistedLogFix() {
    addSlf4j();

    doTest(
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

    checkQuickFix("Convert 'System.out' call to call of 'Slf4j'",
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

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ThrowablePrintedToSystemOutInspection();
  }
}

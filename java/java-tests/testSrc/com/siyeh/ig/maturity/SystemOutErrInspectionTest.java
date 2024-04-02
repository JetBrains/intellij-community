/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class SystemOutErrInspectionTest extends LightJavaInspectionTestCase {

  public void testSimple() {
    doStatementTest("/*Uses of 'System.out' should probably be replaced with more robust logging*/System.out/**/.println(\"debugging\");");
  }

  public void testMultiple() {
    doMemberTest("public void foo() {" +
                 "  /*Uses of 'System.out' should probably be replaced with more robust logging*/System.out/**/.println(0);" +
                 "  /*Uses of 'System.err' should probably be replaced with more robust logging*/System.err/**/.println(0);" +
                 "  final java.io.PrintStream out = /*Uses of 'System.out' should probably be replaced with more robust logging*/System.out/**/;" +
                 "  final java.io.PrintStream err = /*Uses of 'System.err' should probably be replaced with more robust logging*/System.err/**/;" +
                 "}");
  }

  private void addSlf4j() {
    addEnvironmentClass("""
        package org.slf4j;
        @SuppressWarnings("ALL") public class LoggerFactory {
        public static Logger getLogger(Class clazz) { return null; }
        }
        public interface Logger {
           void error(String format, Object... arguments);
           void info(String format, Object... arguments);
        }
    """);
  }

  private void addMessageFormat() {
    addEnvironmentClass("""
        package java.text;
        public final class MessageFormat{
          public static String format(String format, Object... arguments) {return format;}
        }
        """);
  }

  public void testSimpleLogFix() {
    addSlf4j();

    doTest(
      """
      class Test{
        void foo(Object o) {
            /*Uses of 'System.out' should probably be replaced with more robust logging*/System.out<caret>/**/.println("Something " + o);
        }
      }
      """);

    checkQuickFix("Convert 'System.out' call to call of 'Slf4j'",
  """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      
      class Test{
          private static final Logger log = LoggerFactory.getLogger(Test.class);
      
          void foo(Object o) {
              log.info("Something {}", o);
        }
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
        private static final Logger log = LoggerFactory.getLogger(Test.class);

        void foo(Exception o) {
          /*Uses of 'System.out' should probably be replaced with more robust logging*/System.out<caret>/**/.println("Something " + o);
        }
      }
      """);

    checkQuickFix("Convert 'System.out' call to call of 'Slf4j'",
  """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      
      class Test{
        private static final Logger log = LoggerFactory.getLogger(Test.class);
    
        void foo(Exception o) {
            log.info("Something {}", String.valueOf(o));
        }
      }
      """);
  }

  public void testSimpleExistedLogWithRecordFix() {
    addSlf4j();

    doTest(
      """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;

      class Test{
        private static final Logger log = LoggerFactory.getLogger(Test.class);
      
        record R(){}
  
        void foo(R r) {
          /*Uses of 'System.out' should probably be replaced with more robust logging*/System.out<caret>/**/.println(r);
        }
      }
      """);

    checkQuickFix("Convert 'System.out' call to call of 'Slf4j'",
  """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      
      class Test{
        private static final Logger log = LoggerFactory.getLogger(Test.class);
      
        record R(){}
      
        void foo(R r) {
          log.info(String.valueOf(r));
        }
      }
      """);
  }

  public void testSimpleExistedLogWithStringFormatFix() {
    addSlf4j();

    doTest(
      """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;

      class Test{
        private static final Logger log = LoggerFactory.getLogger(Test.class);
      
        void foo(Object r) {
          /*Uses of 'System.out' should probably be replaced with more robust logging*/System.out<caret>/**/.println(String.format("test %s test", r));
        }
      }
      """);

    checkQuickFix("Convert 'System.out' call to call of 'Slf4j'",
  """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      
      class Test{
        private static final Logger log = LoggerFactory.getLogger(Test.class);
      
        void foo(Object r) {
          log.info("test {} test", r);
        }
      }
      """);
  }

  public void testSimpleExistedLogWithMessageFormatFix() {
    addSlf4j();
    addMessageFormat();

    doTest(
      """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      import java.text.MessageFormat;

      class Test{
        private static final Logger log = LoggerFactory.getLogger(Test.class);
      
        void foo(Object r) {
          /*Uses of 'System.out' should probably be replaced with more robust logging*/System.out<caret>/**/.println(MessageFormat.format("test {0} test", r));
        }
      }
      """);

    checkQuickFix("Convert 'System.out' call to call of 'Slf4j'",
  """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      import java.text.MessageFormat;
      
      class Test{
        private static final Logger log = LoggerFactory.getLogger(Test.class);
      
        void foo(Object r) {
          log.info("test {} test", r);
        }
      }
      """);
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new SystemOutErrInspection();
  }
}

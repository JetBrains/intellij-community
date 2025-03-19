// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest;

import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PACKAGE_PRIVATE;

public class JavaRearrangerBlankLinesTest extends AbstractJavaRearrangerTest {
  public void testPreserveRelativeBlankLines() {
    getCommonSettings().BLANK_LINES_AROUND_CLASS = 2;
    getCommonSettings().BLANK_LINES_AROUND_FIELD = 1;
    getCommonSettings().BLANK_LINES_AROUND_METHOD = 2;
    getCommonSettings().BLANK_LINES_AROUND_FIELD_IN_INTERFACE = 2;
    getCommonSettings().BLANK_LINES_AROUND_METHOD_IN_INTERFACE = 3;
    doTest("""
             class Test {
               private void method1() {}

               public void method2() {}

               private int i;

               public int j;
               public static int k;
             }
             interface MyInterface {
               void test1();
               void test2();
               int i = 0;
               int j = 0;
             }""", """
             interface MyInterface {
               int i = 0;


               int j = 0;



               void test1();



               void test2();
             }


             class Test {
               public static int k;

               public int j;

               private int i;


               public void method2() {}


               private void method1() {}
             }""", classic);
  }

  public void testCutBlankLines() {
    getCommonSettings().BLANK_LINES_AROUND_FIELD = 0;
    getCommonSettings().BLANK_LINES_AROUND_METHOD = 1;
    doTest("""
             class Test {

                 void test1() {
                 }

                 void test2() {
                 }

                 int i;
                 int j;
             }""", """
             class Test {

                 int i;
                 int j;

                 void test1() {
                 }

                 void test2() {
                 }
             }""", List.of(AbstractRearrangerTest.rule(FIELD, PACKAGE_PRIVATE), AbstractRearrangerTest.rule(METHOD)));
  }

  public void test_blank_lines_settings_are_not_applied_to_anonymous_classes() {
    getCommonSettings().BLANK_LINES_AROUND_CLASS = 1;
    String text = """
      class Test {
        void test() {
          a(new Intf() {});
          a(new Intf() {});
        }
      }""";
    doTest(text, text, List.of(AbstractRearrangerTest.rule(CLASS)));
  }

  public void test_statements_on_the_same_line() {
    String before = """



      public enum Sender {a, b; private String value;
      }
      """;
    doTest(before, before, List.of());
  }

  public void test_keep_blank_lines_between_fields() {
    String text = """
      public class Test {
        private static final Logger LOGGER = LoggerFactory.getLogger(AddCurrentUser.class);


        private GlobalQueryService globalQueryService;
        private EventCoordinationService eventCoordinationService;
      }
      """;
    doTest(text, text, classic);
  }

  public void test_keep_blank_lines_between_fields_more_fair_test() {
    doTest("""
             public class Test {
                 private static final int t = 12;


                 public int q = 2;
                 private int e = 3;
                 public int t11 = 23;

                 private void test() {
                 }

                 public void main() {
                 }

             }
             """, """
             public class Test {
                 private static final int t = 12;


                 public int q = 2;
                 public int t11 = 23;
                 private int e = 3;

                 public void main() {
                 }

                 private void test() {
                 }

             }
             """, classic);
  }
}

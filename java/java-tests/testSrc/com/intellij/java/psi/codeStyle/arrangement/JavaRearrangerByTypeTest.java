// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest;

import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.BY_NAME;

public class JavaRearrangerByTypeTest extends AbstractJavaRearrangerTest {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    getCommonSettings().BLANK_LINES_AROUND_METHOD = 0;
    getCommonSettings().BLANK_LINES_AROUND_CLASS = 0;
  }

  public void test_fields_before_methods() {
    doTest("""
             class Test {
               public void test() {}
                private int i;
             }
             class Test2 {
             public void test() {
             }
                 private int i;
               private int j;
             }""", """
             class Test {
                private int i;
               public void test() {}
             }
             class Test2 {
                 private int i;
               private int j;
             public void test() {
             }
             }""", List.of(rule(FIELD)));
  }

  public void test_anonymous_class_at_field_initializer() {
    doTest("""
             class Test {
               private Object first = new Object() {
                 int inner1;
                 public String toString() { return "test"; }
                 int inner2;
               };
               public Object test(Object ... args) {
                 return null;
               }
               private Object second = test(test(new Object() {
                 public String toString() {
                   return "test";
                 }
                 private Object inner = new Object() {
                   public String toString() { return "innerTest"; }
                 };
               }));
             }""", """
             class Test {
               private Object first = new Object() {
                 int inner1;
                 int inner2;
                 public String toString() { return "test"; }
               };
               private Object second = test(test(new Object() {
                 private Object inner = new Object() {
                   public String toString() { return "innerTest"; }
                 };
                 public String toString() {
                   return "test";
                 }
               }));
               public Object test(Object ... args) {
                 return null;
               }
             }""", List.of(rule(FIELD)));
  }

  public void test_anonymous_class_at_method() {
    doTest("""
             class Test {
                void declaration() {
                  Object o = new Object() {
                    private int test() { return 1; }
                    String s;
                  }
                }
                double d;
                void call() {
                  test(test(1, new Object() {
                    public void test() {}
                    int i;
                  });
                }
             }""", """
             class Test {
                double d;
                void declaration() {
                  Object o = new Object() {
                    String s;
                    private int test() { return 1; }
                  }
                }
                void call() {
                  test(test(1, new Object() {
                    int i;
                    public void test() {}
                  });
                }
             }""", List.of(rule(FIELD)));
  }

  public void test_inner_class_interface_and_enum() {
    doTest("""
             class Test {
                enum E { ONE, TWO }
                class Inner {}
                interface Intf {}
             }""", """
             class Test {
                interface Intf {}
                enum E { ONE, TWO }
                class Inner {}
             }""", List.of(rule(INTERFACE), rule(ENUM), rule(CLASS)));
  }

  public void test_ranges() {
    doTest("""
             class Test {
               void outer1() {}
             <range>  String outer2() {}
               int i;</range>
               void test() {
                 method(new Object() {
                   void inner1() {}
                   Object field = new Object() {
             <range>        void inner2() {}
                     String s;</range>
                     Integer i;
                   }
                 });
               }
             }""", """
             class Test {
               void outer1() {}
               int i;
               String outer2() {}
               void test() {
                 method(new Object() {
                   void inner1() {}
                   Object field = new Object() {
                     String s;
                     void inner2() {}
                     Integer i;
                   }
                 });
               }
             }""", List.of(rule(FIELD)));
  }

  public void test_methods_and_constructors() {
    doTest("""
             class Test {
               abstract void method1();
               Test() {}
               abstract void method2();
             }""", """
             class Test {
               Test() {}
               abstract void method1();
               abstract void method2();
             }""", List.of(rule(CONSTRUCTOR), rule(METHOD)));
  }

  public void test_multiple_fields_in_one_row() {
    doTest("""
             class Test {
               private long l;
               public int i, j;
               protected float f;
             }""", """
             class Test {
               public int i, j;
               protected float f;
               private long l;
             }""", List.of(rule(PUBLIC), rule(PROTECTED)));
  }

  public void test_multiline_multiple_field_variables_declaration() {
    doTest("""
             class Test {
               private String a1,
                              a2;
               public String a3;
             }""", """
             class Test {
               public String a3;
               private String a1,
                              a2;
             }""", List.of(rule(FIELD, PUBLIC), rule(FIELD, PRIVATE)));
  }

  public void test_multiline_multiple_field_variables_declaration_with_initializers() {
    doTest("""
             class Test {
               private String a1 = "one",
                              a2 = "two";
               public String a3;
             }""", """
             class Test {
               public String a3;
               private String a1 = "one",
                              a2 = "two";
             }""", List.of(rule(FIELD, PUBLIC), rule(FIELD, PRIVATE)));
  }

  public void test_incomplete_multiple_multiline_field_() {
    doTest("""
             class Test {
               private String a1,
                              a2
               public String a3;
             }""", """
             class Test {
               public String a3;
               private String a1,
                              a2
             }""", List.of(rule(FIELD, PUBLIC), rule(FIELD, PRIVATE)));
  }

  public void test_fields_with_comments() {
    doTest("""
             class Test {
               int h1, /** h1 */
                   h2;
               int f1, // f1
                   f2; // f2
               int g1, /* g1 */
                   g2;
               int e1, e2; // ee
               int d; /* c-style
                         multi-line comment */
               int b; /* c-style single line comment */
               int c; // comment
               int a;
             }""", """
             class Test {
               int a;
               int b; /* c-style single line comment */
               int c; // comment
               int d; /* c-style
                         multi-line comment */
               int e1, e2; // ee
               int f1, // f1
                   f2; // f2
               int g1, /* g1 */
                   g2;
               int h1, /** h1 */
                   h2;
             }""", List.of(ruleWithOrder(BY_NAME, rule(FIELD))));
  }

  public void test_anonymous_class_and_siblings() {
    doTest("""
             class Test {
               void test() {
                 new MyClass(new Object() {
                   @Override
                   public String toString() {
                     return null;
                   }
                 }) {
                   @Override
                   public int hashCode() {
                     return 1;
                   }
                   private int field;
                 }
               };
             }""", """
             class Test {
               void test() {
                 new MyClass(new Object() {
                   @Override
                   public String toString() {
                     return null;
                   }
                 }) {
                   private int field;
                   @Override
                   public int hashCode() {
                     return 1;
                   }
                 }
               };
             }""",
           List.of(rule(FIELD), rule(METHOD)));
  }

  public void test_multiple_elements_at_the_same_line() {
    doTest("""
             class Test {
               int i;int getI() {
                 return i;
               }int j;int getJ() {
                 return j;
               }
             }""", """
             class Test {
               int i;int j;int getI() {
                 return i;
               }int getJ() {
                 return j;
               }
             }""",
           List.of(rule(FIELD), rule(METHOD)));
  }

  public void test_IDEA_124077_Enum_code_reformat_destroys_enum() {
    doTest("""

             public enum ErrorResponse {

                 UNHANDLED_EXCEPTION,
                 UNHANDLED_BUSINESS,
                 ACCOUNT_NOT_VALID,
                 ACCOUNT_LATE_CREATION;

                 public void test() {}
                 public int t;

                 public long l;
                 private void q() {}
             }
             """, """

             public enum ErrorResponse {

                 UNHANDLED_EXCEPTION,
                 UNHANDLED_BUSINESS,
                 ACCOUNT_NOT_VALID,
                 ACCOUNT_LATE_CREATION;

                 public void test() {}
                 private void q() {}
                 public int t;
                 public long l;
             }
             """,
           List.of(rule(METHOD), rule(FIELD)));
  }

  public void test_parameterized_class() {
    doTest("""
             public class Seq<T> {

                 public Seq(T x) {
                 }

                 public Seq() {}

                 static <T> Seq<T> nil() {
                     return new Seq<T>();
                 }

                 static <V> Seq<V> cons(V x) {
                     return new Seq<V>(x);
                 }

                 int field;
             }
             """, """
             public class Seq<T> {

                 int field;

                 public Seq(T x) {
                 }

                 public Seq() {}
                 static <T> Seq<T> nil() {
                     return new Seq<T>();
                 }
                 static <V> Seq<V> cons(V x) {
                     return new Seq<V>(x);
                 }
             }
             """, List.of(rule(FIELD)));
  }

  public void test_overridden_method_is_matched_by_overridden_rule() {
    doTest("""
             class A {
               public void test() {}
               public void run() {}
             }

             class B extends A {

               public void infer() {}

               @Override
               public void test() {}

               private void fail() {}

               @Override
               public void run() {}

               private void compute() {}

               public void adjust() {}

             }
             """, """
             class A {
               public void test() {}
               public void run() {}
             }

             class B extends A {

               public void infer() {}
               public void adjust() {}
               private void fail() {}
               private void compute() {}
               @Override
               public void test() {}
               @Override
               public void run() {}

             }
             """, List.of(rule(PUBLIC, METHOD), rule(PRIVATE, METHOD),
                          rule(OVERRIDDEN)));
  }

  public void test_overridden_method_is_matched_by_method_rule_if_no_overridden_rule_found() {
    doTest("""
             class A {
               public void test() {}
               public void run() {}
             }

             class B extends A {

               public void infer() {}

               @Override
               public void test() {}

               private void fail() {}

               @Override
               public void run() {}

               private void compute() {}

               public void adjust() {}

             }
             """, """
             class A {
               public void test() {}
               public void run() {}
             }

             class B extends A {

               public void infer() {}

               @Override
               public void test() {}
               @Override
               public void run() {}
               public void adjust() {}
               private void fail() {}
               private void compute() {}

             }
             """, List.of(rule(PUBLIC, METHOD), rule(PRIVATE, METHOD)));
  }

  public void test_initializer_block_after_fields() {
    doTest("""
             public class NewOneClass {

                 {
                     a = 1;
                 }

                 int a;

                 {
                     b = 5;
                 }

                 int b;

             }
             """, """
             public class NewOneClass {

                 int a;
                 int b;

                 {
                     a = 1;
                 }

                 {
                     b = 5;
                 }

             }
             """, List.of(rule(FIELD), rule(INIT_BLOCK)));
  }

  public void test_static_initializer_block() {
    doTest("""
             public class NewOneClass {

                 static {
                     a = 1;
                 }

                 static int a;

                 {
                     b = 5;
                 }

                 int b;

             }
             """, """
             public class NewOneClass {

                 static int a;

                 static {
                     a = 1;
                 }

                 int b;

                 {
                     b = 5;
                 }

             }
             """, List.of(rule(STATIC, FIELD), rule(STATIC, INIT_BLOCK),
                          rule(FIELD), rule(INIT_BLOCK)));
  }
}

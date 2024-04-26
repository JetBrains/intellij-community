// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens;

import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Grouping.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PUBLIC;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.BREADTH_FIRST;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Order.DEPTH_FIRST;

public class JavaRearrangerGrouperTest extends AbstractJavaRearrangerTest {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    getCommonSettings().BLANK_LINES_AROUND_METHOD = 0;
  }

  public void test_getters_and_setters() {
    getCommonSettings().BLANK_LINES_AROUND_METHOD = 1;

    doTest("""
             class Test {
               public void setValue(int i) {}
               protected void util() {}
               public int getValue() { return 1; }
             }""", """
             class Test {
               public int getValue() { return 1; }

               public void setValue(int i) {}

               protected void util() {}
             }""", List.of(rule(PUBLIC)),
           List.of(group(GETTERS_AND_SETTERS)));
  }

  public void test_getter_and_multiple_setters() {
    // Expected that setters even with the same name won't be reordered
    doTest("""
             class Test {
               public int getValue() { return 1; }
               public void setValue(int i) {}
               public void setValue(long i) {}
             }""", """
             class Test {
               public int getValue() { return 1; }
               public void setValue(int i) {}
               public void setValue(long i) {}
             }""", List.of(rule(PUBLIC)),
           List.of(group(GETTERS_AND_SETTERS)));
  }

  public void test_utility_methods_depth_first() {
    doTest("""
             class Test {
               void util1() { util11(); }
               void service1() { util1(); }
               void util2() {}
               void util11() {}
               void service2() { util2(); }
             }""", """
             class Test {
               void service1() { util1(); }
               void util1() { util11(); }
               void util11() {}
               void service2() { util2(); }
               void util2() {}
             }""", List.of(),
           List.of(group(DEPENDENT_METHODS, DEPTH_FIRST)));
  }

  public void test_utility_methods_breadth_first() {
    doTest("""
             class Test {
               void util2() { util3(); }
               void service1() { util1(); util2(); }
               void service2() { util2(); util1(); }
               void util3() {}
             }""", """
             class Test {
               void service1() { util1(); util2(); }
               void util2() { util3(); }
               void util3() {}
               void service2() { util2(); util1(); }
             }""", List.of(),
           List.of(group(DEPENDENT_METHODS, BREADTH_FIRST)));
  }

  public void test_overridden_methods() {
    doTest("""
             class Base {
               void base1() {}
               void base2() {}
             }

             <range>class Sub extends Base {
               void base2() {}
               void test1() {}
               void base1() {}</range>
               void test2() {}
             }""", """
             class Base {
               void base1() {}
               void base2() {}
             }

             class Sub extends Base {
               void test1() {}
               void base1() {}
               void base2() {}
               void test2() {}
             }""", List.of(), List.of(group(OVERRIDDEN_METHODS)));
  }

  public void test_overridden_methods_with_class() {
    doTest("""
             class C {
                 public void overridden() {}
                 public void foo() {}
             }

             class A {
                \s
                 static class X1 extends C {
                     @Override
                     public void overridden() {}
                     @Override
                     public void foo() {}
                 }
                \s
                 static class X2 extends C {
                     static class X3 {}
                    \s
                     @Override
                     public void overridden() {}
                 }
             }
             """, """
             class C {
                 public void overridden() {}
                 public void foo() {}
             }

             class A {
                \s
                 static class X1 extends C {
                     @Override
                     public void overridden() {}
                     @Override
                     public void foo() {}
                 }
                \s
                 static class X2 extends C {
                     @Override
                     public void overridden() {}
                    \s
                     static class X3 {}
                 }
             }
             """, List.of(rule(StdArrangementTokens.EntryType.METHOD),
                          rule(StdArrangementTokens.EntryType.CLASS)),
           List.of(group(OVERRIDDEN_METHODS)));
  }

  public void do_not_test_overriden_and_utility_methods() {
    doTest("""
             class Base {
               void base1() {}
               void base2() {}
             }

             <range>class Sub extends Base {
               void test3() { test4(); }
               void base2() { test3(); }
               void test2() {}
               void base1() { test1(); }
               void test4() {}
               void test1() { test2(); }</range>
             }""", """
             class Base {
               void base1() {}
               void base2() {}
             }

             class Sub extends Base {
               void base1() { test1(); }
               void test1() { test2(); }
               void test2() {}
               void base2() { test3(); }
               void test3() { test4(); }
               void test4() {}
             }""", List.of(), List.of(group(DEPENDENT_METHODS, DEPTH_FIRST), group(OVERRIDDEN_METHODS)));
  }

  public void test_that_calls_from_anonymous_class_create_a_dependency() {
    doTest("""

             class Test {
               void test2() {}
               void test1() { test2(); }
               void root() {
                 new Runnable() {
                   public void run() {
                     test1();
                   }
                 }.run();
               }
             }""", """

             class Test {
               void root() {
                 new Runnable() {
                   public void run() {
                     test1();
                   }
                 }.run();
               }
               void test1() { test2(); }
               void test2() {}
             }""", List.of(),
           List.of(group(DEPENDENT_METHODS, DEPTH_FIRST)));
  }

  public void test_keep_dependent_methods_together_multiple_times_produce_same_result() {
    List<ArrangementGroupingRule> groups =
      List.of(group(DEPENDENT_METHODS, BREADTH_FIRST));
    String before = """
      public class SuperClass {

          public void doSmth1() {
          }

          public void doSmth2() {
          }

          public void doSmth3() {
          }

          public void doSmth4() {
          }

          public void doSmth() {
              this.doSmth1();
              this.doSmth2();
              this.doSmth3();
              this.doSmth4();
          }
      }""";
    String after = """
      public class SuperClass {

          public void doSmth() {
              this.doSmth1();
              this.doSmth2();
              this.doSmth3();
              this.doSmth4();
          }
          public void doSmth1() {
          }
          public void doSmth2() {
          }
          public void doSmth3() {
          }
          public void doSmth4() {
          }
      }""";
    doTest(before, after, List.of(), groups);
    doTest(after, after, List.of(), groups);
  }

  public void test_dependent_methods_DFS() {
    doTest("""

             public class Q {

                 void E() {
                     ER();
                 }

                 void B() {
                     E();
                     F();
                 }

                 void A() {
                     B();
                     C();
                 }

                 void F() {
                 }

                 void C() {
                     G();
                 }

                 void ER() {
                 }

                 void G() {
                 }

             }
             """, """

             public class Q {

                 void A() {
                     B();
                     C();
                 }
                 void B() {
                     E();
                     F();
                 }
                 void E() {
                     ER();
                 }
                 void ER() {
                 }
                 void F() {
                 }
                 void C() {
                     G();
                 }
                 void G() {
                 }

             }
             """, List.of(), List.of(group(DEPENDENT_METHODS, DEPTH_FIRST)));
  }

  public void test_dependent_methods_BFS() {
    doTest("""

             public class Q {

                 void E() {
                     ER();
                 }

                 void B() {
                     E();
                     F();
                 }

                 void A() {
                     B();
                     C();
                 }

                 void F() {
                 }

                 void C() {
                     G();
                 }

                 void ER() {
                 }

                 void G() {
                 }

             }
             """, """

             public class Q {

                 void A() {
                     B();
                     C();
                 }
                 void B() {
                     E();
                     F();
                 }
                 void C() {
                     G();
                 }
                 void E() {
                     ER();
                 }
                 void F() {
                 }
                 void G() {
                 }
                 void ER() {
                 }

             }
             """, List.of(), List.of(group(DEPENDENT_METHODS, BREADTH_FIRST)));
  }

  public void test_method_references_dependant_methods() {
    doTest("""

             import java.util.ArrayList;

             public class Test {
                 private void top() {
                     new ArrayList<String>().stream()
                             .map(this::first)
                             .map(this::second)
                             .count();
                 }

                 private void irrelevant() {
                 }

                 private String second(String string) {
                     return string;

                 }

                 private String first(String string) {
                     return string;
                 }
             }
             """, """

             import java.util.ArrayList;

             public class Test {
                 private void top() {
                     new ArrayList<String>().stream()
                             .map(this::first)
                             .map(this::second)
                             .count();
                 }
                 private String first(String string) {
                     return string;
                 }
                 private String second(String string) {
                     return string;

                 }
                 private void irrelevant() {
                 }
             }
             """, List.of(), List.of(group(DEPENDENT_METHODS, BREADTH_FIRST)));
  }
}

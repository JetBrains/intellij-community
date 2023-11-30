// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.vararg;

import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class WrapVarargArgumentsWithExplicitArrayIntentionTest extends IPPTestCase {

  @SuppressWarnings("ConfusingArgumentToVarargsMethod")
  public void testNullArgument() {
    doTestIntentionNotAvailable("class X {" +
                                "  void a(String... ss) {}" +
                                "  void b() {" +
                                "    a(/*_Wrap vararg arguments with explicit array creation*/null);" +
                                "  }" +
                                "}");
  }

  @SuppressWarnings("RedundantArrayCreation")
  public void testEnumConstants() {
    doTest("enum X {" +
           "  A(/*_Wrap vararg arguments with explicit array creation*/1), B(1,2), C(1,2,3);" +
           "  X(int... is) {}" +
           "}",

           "enum X {" +
           "  A(new int[]{1}), B(1,2), C(1,2,3);" +
           "  X(int... is) {}" +
           "}");
  }

  @SuppressWarnings("RedundantArrayCreation")
  public void testConstructorCall() {
    doTest("class A {" +
           "  A(int... is) {}" +
           "  void a() {" +
           "    new A(1,2,3/*_Wrap vararg arguments with explicit array creation*/);" +
           "  }" +
           "}",

           "class A {" +
           "  A(int... is) {}" +
           "  void a() {" +
           "    new A(new int[]{1, 2, 3});" +
           "  }" +
           "}");
  }

  public void testCapturedWildcard1() {
    doTest(
      """
        class X {
            interface I<T> {
                String m(T... t);
            }
            public static void run() {
                I<? super Integer> i = null;
                i.m(/*_Wrap vararg arguments with explicit array creation*/1, 2, 3);
            }
        }""",

      """
        class X {
            interface I<T> {
                String m(T... t);
            }
            public static void run() {
                I<? super Integer> i = null;
                i.m(new Integer[]{1, 2, 3});
            }
        }"""
    );
  }

  public void testCapturedWildcard2() {
    doTestIntentionNotAvailable(
      """
        class Y {
            interface I<T> {
                String m(T... t);
            }
            public static void run() {
                I<?> i = null;
                i.m(/*_Wrap vararg arguments with explicit array creation*/1, 2, 3);
            }
        }""");
  }
  
  public void testNonReifiable() {
    doTestIntentionNotAvailable(
      """
        import java.util.*;
        class Y {
            <T> void m(Set<T>... t){}
            public static void run(Set<?> s) {
                m(/*_Wrap vararg arguments with explicit array creation*/s);
            }
        }""");
  }

  @SuppressWarnings("ALL")
  public void testGenericArray() {
    doTest(
      "import java.util.Set;" +
      "class Y<T> {\n" +
      "  void m(Set<String>... t){}\n" +
      "  public void run(Set<String> s) {\n" +
      "    m(/*_Wrap vararg arguments with explicit array creation*/s);\n" +
      "  }\n" +
      "}",

      "import java.util.Set;class Y<T> {\n" +
      "  void m(Set<String>... t){}\n" +
      "  public void run(Set<String> s) {\n" +
      "    m(new Set[]{s});\n" +
      "  }\n" +
      "}"
    );
  }

  @SuppressWarnings("RedundantArrayCreation")
  public void testClassWildcard() {
    doTestIntentionNotAvailable(
      """
        class Y {  void test() throws NoSuchMethodException {
            String.class.getMethod("indexOf", new Class[]/*_Wrap vararg arguments with explicit array creation*/ {int.class});
          }}"""
    );
  }

  @SuppressWarnings("RedundantArrayCreation")
  public void testEmptyArray() {
    doTest(
      "class X {" +
      "  void x() {" +
      "    java.util.Arrays.asList(/*_Wrap vararg arguments with explicit array creation*/);" +
      "  }" +
      "}",

      "class X {" +
      "  void x() {" +
      "    java.util.Arrays.asList(new Object[]{});" +
      "  }" +
      "}"
    );
  }
}

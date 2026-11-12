// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.vararg;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class WrapVarargArgumentsWithExplicitArrayIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture);

  @Test
  @SuppressWarnings("ConfusingArgumentToVarargsMethod")
  public void testNullArgument() {
    ipp.get().doTestIntentionNotAvailable("class X {" +
                                "  void a(String... ss) {}" +
                                "  void b() {" +
                                "    a(/*_Wrap vararg arguments with explicit array creation*/null);" +
                                "  }" +
                                "}");
  }

  @Test
  @SuppressWarnings("RedundantArrayCreation")
  public void testEnumConstants() {
    ipp.get().doTest("enum X {" +
           "  A(/*_Wrap vararg arguments with explicit array creation*/1), B(1,2), C(1,2,3);" +
           "  X(int... is) {}" +
           "}",

           "enum X {" +
           "  A(new int[]{1}), B(1,2), C(1,2,3);" +
           "  X(int... is) {}" +
           "}");
  }

  @Test
  @SuppressWarnings("RedundantArrayCreation")
  public void testConstructorCall() {
    ipp.get().doTest("class A {" +
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

  @Test
  public void testCapturedWildcard1() {
    ipp.get().doTest(
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

  @Test
  public void testCapturedWildcard2() {
    ipp.get().doTestIntentionNotAvailable(
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
  
  @Test
  public void testNonReifiable() {
    ipp.get().doTestIntentionNotAvailable(
      """
        import java.util.*;
        class Y {
            <T> void m(Set<T>... t){}
            public static void run(Set<?> s) {
                m(/*_Wrap vararg arguments with explicit array creation*/s);
            }
        }""");
  }

  @Test
  @SuppressWarnings("ALL")
  public void testGenericArray() {
    ipp.get().doTest(
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

  @Test
  @SuppressWarnings("RedundantArrayCreation")
  public void testClassWildcard() {
    ipp.get().doTestIntentionNotAvailable(
      """
        class Y {  void test() throws NoSuchMethodException {
            String.class.getMethod("indexOf", new Class[]/*_Wrap vararg arguments with explicit array creation*/ {int.class});
          }}"""
    );
  }

  @Test
  @SuppressWarnings("RedundantArrayCreation")
  public void testEmptyArray() {
    ipp.get().doTest(
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
  
  /// See IDEA-393105
  @Test
  public void testArrayWithComment() {
    ipp.get().doTest("""
      class X { void x() {
          java.util.Arrays.asList("one", "tw/*_Wrap vararg arguments with explicit array creation*/o", "three" // NON-NLS
          );
      }}""", """
      class X { void x() {
          java.util.Arrays.asList(
                  new String[]{"one", "two", "three" // NON-NLS
                  });
      }}"""
    );
  }
}

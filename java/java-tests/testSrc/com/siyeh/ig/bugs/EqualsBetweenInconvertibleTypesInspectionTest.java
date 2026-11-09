// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
@SuppressWarnings("ALL")
public class EqualsBetweenInconvertibleTypesInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_11);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new EqualsBetweenInconvertibleTypesInspection(), getEnvironmentClasses());

  @Test
  public void testSimple() {
    inspectionFixture.get().doMemberTest("public void foo() {\n" +
                 "    final Integer foo = new Integer(3);\n" +
                 "    final Double bar = new Double(3);\n" +
                 "    foo./*'equals' between objects of inconvertible types 'Integer' and 'Double'*/equals/**/(bar);\n" +
                 "}\n");
  }

  @Test
  public void testWithoutQualifier() {
    inspectionFixture.get().doTest("class Clazz {\n" +
           "    void foo() {\n" +
           "        boolean bar = /*'equals' between objects of inconvertible types 'Clazz' and 'String'*/equals/**/(\"differentClass\");\n" +
           "    }\n" +
           "}");
  }

  @Test
  public void testJavaUtilObjectsEquals() {
    inspectionFixture.get().doStatementTest("java.util.Objects./*'equals' between objects of inconvertible types 'Integer' and 'String'*/equals/**/(Integer.valueOf(1), \"string\");");
  }

  @Test
  public void testComGoogleCommonBaseObjects() {
    inspectionFixture.get().doStatementTest("com.google.common.base.Objects./*'equal' between objects of inconvertible types 'Integer' and 'String'*/equal/**/(Integer.valueOf(1), \"string\");");
  }

  @Test
  public void testCollection() {
    inspectionFixture.get().doTest(
      "import java.util.Collection;" +
      "class XXX {" +
      "  interface A {}" +
      "  interface B extends A {}" +
      "  boolean m(Collection<A> c1, Collection<B> c2) {" +
      "    return c2.equals(c1);" +
      "  }" +
      "" +
      "  boolean n(Collection<Integer> c1, Collection<String> c2) {" +
      "     return c1./*'equals' between objects of inconvertible types 'Collection<Integer>' and 'Collection<String>'*/equals/**/(c2);" +
      "  }" +
      "}");
  }

  @Test
  public void testRaw() {
    inspectionFixture.get().doTest(
      "import java.util.Collection;" +
      "class XXX {" +
      "  interface A {}" +
      "  boolean m(Collection c1, Collection<A> c2) {" +
      "    return c2.equals(c1);" +
      "  }" +
      "}");
  }

  @Test
  public void testMethodReference() {
    inspectionFixture.get().doTest("import java.util.Objects;\n" +
           "import java.util.function.*;\n" +
           "\n" +
           "class Test {\n" +
           "  Predicate<Integer> p = \"123\"::/*'equals' between objects of inconvertible types 'String' and 'Integer'*/equals/**/;\n" +
           "  Predicate<CharSequence> pOk = \"456\"::equals;\n" +
           "  BiPredicate<String, Integer> bp = Objects::/*'equals' between objects of inconvertible types 'String' and 'Integer'*/equals/**/;\n" +
           "  BiPredicate<Long, Double> bp2 = Object::/*'equals' between objects of inconvertible types 'Long' and 'Double'*/equals/**/;\n" +
           "  BiPredicate<Long, Long> bpOk = Object::equals;\n" +
           "}\n");
  }

  @Test
  public void testSimplePredicateIsEqualTest() {
    inspectionFixture.get().doMemberTest("public <T> void foo(T t) {\n" +
                 "    (java.util.function.Predicate./*'isEqual' between objects of inconvertible types 'String' and 'int'*/isEqual/**/(\"1\")).test(1);\n" +
                 "    java.util.function.Predicate.isEqual(\"1\").test(new Object());\n" +
                 "    java.util.function.Predicate.isEqual(new Object()).test(1);\n" +
                 "    java.util.function.Predicate.isEqual(\"1\").test(t);\n" +
                 "    java.util.function.Predicate.not(\n" +
                 "                        java.util.function.Predicate./*'isEqual' between objects of inconvertible types 'String' and 'int'*/isEqual/**/(\"1\")\n" +
                 "                )\n" +
                 "                        .or(java.util.function.Predicate./*'isEqual' between objects of inconvertible types 'String' and 'int'*/isEqual/**/(\"1\"))\n" +
                 "                        .and(java.util.function.Predicate./*'isEqual' between objects of inconvertible types 'String' and 'int'*/isEqual/**/(\"1\"))\n" +
                 "                        .negate().test(1);\n" +
                 "    java.util.function.Predicate.not(\n" +
                 "                        java.util.function.Predicate.isEqual(new Object())\n" +
                 "                )\n" +
                 "                        .or(java.util.function.Predicate.isEqual(new Object()))\n" +
                 "                        .and(java.util.function.Predicate.isEqual(new Object()))\n" +
                 "                        .negate().test(1);\n" +
                 "}\n");
  }

  @Test
  public void testStreamPredicateEqualTest() {
    inspectionFixture.get().doMemberTest("    public void foo() {\n" +
                 "        java.util.List<Integer> collect = java.util.stream.Stream.of(1)\n" +
                 "                .filter(java.util.function.Predicate./*'isEqual' between objects of inconvertible types 'String' and 'Integer'*/isEqual/**/(\"1\"))\n" +
                 "                .filter(java.util.function.Predicate.not(\n" +
                 "                        java.util.function.Predicate./*'isEqual' between objects of inconvertible types 'String' and 'Integer'*/isEqual/**/(\"1\")\n" +
                 "                )\n" +
                 "                        .or(java.util.function.Predicate./*'isEqual' between objects of inconvertible types 'String' and 'Integer'*/isEqual/**/(\"1\"))\n" +
                 "                        .and(java.util.function.Predicate./*'isEqual' between objects of inconvertible types 'String' and 'Integer'*/isEqual/**/(\"1\"))\n" +
                 "                        .negate())\n" +
                 "                .filter(java.util.function.Predicate.not(\n" +
                 "                        java.util.function.Predicate.isEqual(new Object())\n" +
                 "                )\n" +
                 "                        .or(t->t.equals(1))\n" +
                 "                        .and(java.util.function.Predicate.isEqual(new Object()))\n" +
                 "                        .negate())\n" +
                 "                .filter(java.util.function.Predicate.isEqual(new Object()))\n" +
                 "                .collect(java.util.stream.Collectors.toList());\n" +
                 "    }\n");
  }

  @Test
  public void testStreamPredicateEqualWithGenericTest() {
    inspectionFixture.get().doTest(
      "import java.util.function.Predicate;\n" +
      "\n" +
      "public class X {\n" +
      "    public static void example() {\n" +
      "        test(Predicate.isEqual(\"1\"), Predicate.isEqual(\"1\"),\n" +
      "                Predicate./*'isEqual' between objects of inconvertible types 'String' and 'Integer'*/isEqual/**/(\"1\"), " +
      "                Predicate./*'isEqual' between objects of inconvertible types 'String' and 'Number'*/isEqual/**/(\"1\"));\n" +
      "        test(Predicate.not(Predicate.isEqual(\"1\")), Predicate.not(Predicate.isEqual(\"1\")),\n" +
      "                Predicate.not(Predicate./*'isEqual' between objects of inconvertible types 'String' and 'Integer'*/isEqual/**/(\"1\")), " +
      "                Predicate.not(Predicate./*'isEqual' between objects of inconvertible types 'String' and 'Number'*/isEqual/**/(\"1\")));\n" +
      "\n" +
      "        test(Predicate.isEqual(new Object()), Predicate.isEqual(new Object()),\n" +
      "                Predicate.isEqual(new Object()), Predicate.isEqual(new Object()));\n" +
      "        test(Predicate.not(Predicate.isEqual(new Object())), Predicate.not(Predicate.isEqual(new Object())),\n" +
      "                Predicate.not(Predicate.isEqual(new Object())), Predicate.not(Predicate.isEqual(new Object())));\n" +
      "    }\n" +
      "\n" +
      "    static <T> void test(Predicate t1, Predicate<T> t2,\n" +
      "                         Predicate<? super Integer> t3, Predicate<? extends Number> t4) {}\n" +
      "}\n" +
      "\n");
  }

  @Test
  public void testNoCommonSubclass() {
    inspectionFixture.get().doTest("import java.util.Date;\n" +
           "import java.util.Map;\n" +
           "import java.util.Objects;\n" +
           "\n" +
           "class X {\n" +
           "  public static void foo(Date date, Map<String, String> map) {\n" +
           "    boolean res = Objects./*No class found which is a subtype of both 'Map<String, String>' and 'Date'*/equals/**/(map, date);\n" +
           "  }\n" +
           "}");
  }

  @Test
  public void testNoCommonSubclassEqualityComparison() {
    inspectionFixture.get().doTest("import java.util.Date;\n" +
           "import java.util.Map;\n" +
           "import java.util.Objects;\n" +
           "\n" +
           "class X {\n" +
           "  public static boolean foo(Date date, Map<String, String> map) {\n" +
           "    return map /*No class found which is a subtype of both 'Map<String, String>' and 'Date'*/==/**/ date;\n" +
           "  }\n" +
           "}");
  }

  @Test
  public void testCommonSubclass() {
    inspectionFixture.get().doTest("import java.util.Date;\n" +
           "import java.util.Map;\n" +
           "import java.util.Objects;\n" +
           "\n" +
           "class X {\n" +
           "  static abstract class Y extends Date implements Map<String, String> {}\n" +
           "  \n" +
           "  public static void foo(Date date, Map<String, String> map) {\n" +
           "    boolean res = Objects.equals(map, date);\n" +
           "  }\n" +
           "}");
  }

  @Test
  public void testListAndSet() {
    inspectionFixture.get().doTest("import java.util.*;\n" +
           "\n" +
           "class X {\n" +
           "  boolean test(Set<String> set, List<String> list) {\n" +
           "    return set./*'equals' between objects of inconvertible types 'Set<String>' and 'List<String>'*/equals/**/(list) || \n" +
           "           list./*'equals' between objects of inconvertible types 'List<String>' and 'Set<String>'*/equals/**/(set);\n" +
           "  }\n" +
           "}");
  }

  @Test
  public void testSameClassInDeepComparison() {
    inspectionFixture.get().doTest("import java.util.ArrayList;\n" +
           "class Numbers {\n" +
           "    private static <K extends Number, T extends Class<K>> void foo(T categoryClass, ArrayList<Number> numbers) {\n" +
           "        for (Number number : numbers) {\n" +
           "            if (number.getClass() == categoryClass) {\n" +
           "                System.out.println(number);\n" +
           "            }\n" +
           "        }\n" +
           "    }\n" +
           "}");
  }

  @Test
  public void testDifferentSets() {
    inspectionFixture.get().doTest("import java.util.*;\n" +
           "\n" +
           "class X {\n" +
           "  boolean test(HashSet<String> set1, TreeSet<String> set2) {\n" +
           "    return set1.equals(set2); // can be equal by content\n" +
           "  }\n" +
           "\n" +
           "  boolean test2(HashSet<String> set1, TreeSet<Integer> set2) {\n" +
           "    return set1./*'equals' between objects of inconvertible types 'HashSet<String>' and 'TreeSet<Integer>'*/equals/**/(set2);\n" +
           "  }\n" +
           "}");
  }

  @Test
  public void testGeneratedEquals() {
    inspectionFixture.get().doTest("class A {\n" +
           "    int i;\n" +
           "\n" +
           "    @Override\n" +
           "    public boolean equals(Object o) {\n" +
           "      if (this == o) return true;\n" +
           "      if (o == null || getClass() != o.getClass()) return false; // <-- a warning here is unexpected\n" +
           "      A a = (A)o;\n" +
           "      if (i != a.i) return false;\n" +
           "      return true;\n" +
           "    }\n" +
           "    @Override\n" +
           "    public int hashCode() {\n" +
           "      return i;\n" +
           "    }\n" +
           "  }");
  }

  @Test
  public void testWildcards() {
    inspectionFixture.get().doTest("import java.util.*;" +
           "class X {" +
           "  boolean x(Class<? extends Date> a, Class<? extends Map<String, String>> b) {" +
           "    return b./*No class found which is a subtype of both 'Map<String, String>' and 'Date'*/equals/**/(a);" +
           "  }" +
           "}");
  }

  @Test
  public void testAnonymousClasses() {
    inspectionFixture.get().doTest("import java.util.*;" +
           "class X {{" +
           "    boolean equals = new HashSet<String>() {\n" +
           "        }./*'equals' between objects of inconvertible types 'HashSet<String>' and 'TreeSet<Integer>'*/equals/**/(new TreeSet<Integer>());" +
           "}}");
  }

  @Test
  public void testFBounds() {
    inspectionFixture.get().doTest("class U<T extends U<T>> {\n" +
           "  void m(U<?> u1, U<?> u2) {\n" +
           "    if (u1 == u2) {\n" +
           "      System.out.println();\n" +
           "    }\n" +
           "  }\n" +
           "}");
  }

  @Test
  public void testFBoundsWrong() {
    inspectionFixture.get().doTest("class U<T extends U<T, Q>, Q> {\n" +
           "  void m(U<?, Integer> u1, U<?, String> u2) {\n" +
           "    if (u1./*'equals' between objects of inconvertible types 'U<capture of ?, Integer>' and 'U<capture of ?, String>'*/equals/**/(u2)) {\n" +
           "      System.out.println();\n" +
           "    }\n" +
           "  }\n" +
           "}");
  }

  @Test
  public void testUseDfa() {
    inspectionFixture.get().doTest("class X {\n" +
           "  void test() {\n" +
           "    Object obj = \"value\";\n" +
           "    Object obj2 = 123;\n" +
           "    if (obj./*'equals' between objects of inconvertible types 'String' and 'int'*/equals/**/(1)) {}\n" +
           "    if (obj2./*'equals' between objects of inconvertible types 'Integer' and 'String'*/equals/**/(obj)) {}\n" +
           "  }\n" +
           "}");
  }

  @Test
  public void testCapture() {
    inspectionFixture.get().doTest("class X<A, B> {\n" +
           "  static final X<?, ?> CONST = new X<>();\n" +
           "  static final X<Integer, String> CONST2 = new X<>();\n" +
           "  \n" +
           "  void test(X<?, ?>[] data) {\n" +
           "    if (data[0] == CONST) {}\n" +
           "    if (data[0] == CONST2) {}\n" +
           "  }\n" +
           "}");
  }

  private String[] getEnvironmentClasses() {
    return new String[] {
      "package com.google.common.base;" +
      "public final class Objects {" +
      "  public static boolean equal(Object a, Object b) {" +
      "    return true;" +
      "  }" +
      "}"
    };
  }
}
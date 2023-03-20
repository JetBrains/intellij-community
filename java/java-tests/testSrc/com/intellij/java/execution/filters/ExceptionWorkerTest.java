/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.execution.filters;

import com.intellij.execution.filters.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author gregsh
 */
public class ExceptionWorkerTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testParsing() {
    myFixture.addClass("""
                         package com.sample;

                         /**
                          * Created with IntelliJ IDEA.
                          * User: jetbrains
                          * Date: 11/26/12
                          * Time: 6:08 PM
                          * @noinspection ALL
                          */
                         public class RunningMain {
                           public static void main(String[] args) throws Exception {
                             try {
                               func1();
                             }
                             finally {

                             }
                           }

                           static void func1() {
                             try {
                               func();
                             }
                             finally {

                             }
                           }

                           static void func() {
                             throw new NullPointerException();
                           }
                         }
                         """);

    final String testData = """
      Exception in thread "main" java.lang.NullPointerException
      \tat com.sample.RunningMain.func(RunningMain.java:30)
      \tat com.sample.RunningMain.func1(RunningMain.java:22)
      \tat com.sample.RunningMain.main(RunningMain.java:13)
      """;
    final Document document = EditorFactory.getInstance().createDocument(testData);
    FilterMixin filter = (FilterMixin)new ExceptionExFilterFactory().create(GlobalSearchScope.projectScope(getProject()));
    final ArrayList<String> result = new ArrayList<>();
    filter.applyHeavyFilter(document, 0, 0, r -> r.getResultItems().forEach(
      highlight -> result.add(new TextRange(highlight.getHighlightStartOffset(), highlight.getHighlightEndOffset()).substring(testData))));
    assertSameElements(result, "com.sample.RunningMain.func1", "com.sample.RunningMain.main");
  }

  public void testAnomalyParenthesisParsing() {
    assertParsed("at youtrack.jetbrains.com.Issue.IDEA_125137()(FooTest.groovy:2)\n", "youtrack.jetbrains.com.Issue", "IDEA_125137()", "FooTest.groovy", 2);
    assertParsed("at youtrack.jetbrains.com.Issue.IDEA_125137()Hmm(FooTest.groovy:2)\n", "youtrack.jetbrains.com.Issue", "IDEA_125137()Hmm", "FooTest.groovy", 2);
    assertParsed("p1.Cl.mee(p1.Cl.java:87) (A MESSAGE) IDEA-133794 (BUG START WITH 1)\n", "p1.Cl", "mee", "p1.Cl.java", 87);
  }

  private static void assertParsed(String line, String className, String methodName, String fileName, int lineIndex) {
    assertTrue(line.endsWith("\n"));
    ExceptionWorker.ParsedLine trinity = ExceptionWorker.parseExceptionLine(line);
    assertNotNull(trinity);
    assertEquals(className, trinity.classFqnRange.subSequence(line));
    assertEquals(methodName, trinity.methodNameRange.subSequence(line));
    assertEquals(fileName, trinity.fileName);
    assertEquals(lineIndex, trinity.lineNumber);
  }

  public void testYourKitFormat() {
    assertParsed("com.intellij.util.concurrency.Semaphore.waitFor(long) Semaphore.java:89\n",
                 "com.intellij.util.concurrency.Semaphore", "waitFor", "Semaphore.java", 89);
  }

  public void testForcedJstackFormat() {
    assertParsed(" - java.lang.ref.ReferenceQueue.remove(long) @bci=151, line=143 (Compiled frame)\n",
                 "java.lang.ref.ReferenceQueue", "remove", null, 143);

  }

  public void testJava9ModulePrefixed() {
    String line = "at mod.name/p.A.foo(A.java:2)\n";
    assertParsed(line, "p.A", "foo", "A.java", 2);

    PsiClass psiClass = myFixture.addClass("""
                                             package p; public class A {
                                               public void foo() {}
                                             }""");
    ExceptionInfoCache cache = new ExceptionInfoCache(getProject(), GlobalSearchScope.projectScope(getProject()));
    ExceptionLineParser worker = ExceptionLineParserFactory.getInstance().create(cache);
    worker.execute(line, line.length());
    UClass aClass = worker.getUClass();
    assertNotNull(aClass);
    assertEquals(psiClass, aClass.getSourcePsi());
  }

  public void testNonClassInTheLine() {
    String line = "2016-12-20 10:58:36,617 [   5740]   INFO - llij.ide.plugins.PluginManager - Loaded bundled plugins: Android Support (10.2.2), Ant Support (1.0), Application Servers View (0.2.0), AspectJ Support (1.2), CFML Support (3.53), CSS Support (163.7743.44), CVS Integration (11), Cloud Foundry integration (1.0), CloudBees integration (1.0), Copyright (8.1), Coverage (163.7743.44), DSM Analysis (1.0.0), Database Tools and SQL (1.0), Eclipse Integration (3.0), EditorConfig (163.7743.44), Emma (163.7743.44), Flash/Flex Support (163.7743.44)";
    assertNull(ExceptionWorker.parseExceptionLine(line));
    assertNull(ExceptionWorker.parseExceptionLine(line + "\n"));
  }

  public void testNativeMethod() {
    assertParsed("at java.base/java.security.AccessController.doPrivileged(Native Method)\n",
                 "java.security.AccessController", "doPrivileged", null, -1);
  }

  public void testColumnFinder() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        class SomeClass {
          SomeClass() {
            System.out.println((new int[0])[1]);
          }
          static class Inner implements Runnable {
            int test = 4;
            public void run() {
              try {
                System.out.println(test + test() + SomeClass.test());
              } catch(Exception ex) {
                throw new RuntimeException(ex);
              }
            }
            int test() { return 0; }
          }
          private static int test() {
            new SomeClass() {};
            return 1;
          }
          public static void main(String[] args) {
            class X {
              public void run() {
                new Runnable() {
                  public void run() {
                    Runnable inner = new Inner();
                    inner.run();X.this.run();
                  }
                }.run();
              }
            }
            Runnable r = () -> new X().run();
            r.run();
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.RuntimeException: java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0\n", null, null),
      Trinity.create("\tat SomeClass$Inner.run(SomeClass.java:12)\n", 12, 15),
      Trinity.create("\tat SomeClass$1X$1.run(SomeClass.java:27)\n", 27, 19),
      Trinity.create("\tat SomeClass$1X.run(SomeClass.java:29)\n", 29, 11),
      Trinity.create("\tat SomeClass.lambda$main$0(SomeClass.java:32)\n", 32, 32),
      Trinity.create("\tat SomeClass.main(SomeClass.java:33)\n", 33, 7),
      Trinity.create("Caused by: java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0\n", null, null),
      Trinity.create("\tat SomeClass.<init>(SomeClass.java:4)\n", 4, 37),
      Trinity.create("\tat SomeClass$1.<init>(SomeClass.java:18)\n", 18, 9),
      Trinity.create("\tat SomeClass.test(SomeClass.java:18)\n", 18, 9),
      Trinity.create("\tat SomeClass.access$000(SomeClass.java:2)\n", 2, 1),
      Trinity.create("\tat SomeClass$Inner.run(SomeClass.java:10)\n", 10, 54));
    checkColumnFinder(classText, traceAndPositions);
  }
  
  public void testColumnFinderAssert() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        public class SomeClass {
          public static void main(String[] args) {
            assert false;
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.AssertionError\n", null, null),
      Trinity.create("\tat SomeClass.main(SomeClass.java:4)\n", 4, 5));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testColumnFinderArrayStore() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        public class SomeClass {
          public static void main(String[] args) {
            Object[] arr = new String[1];
            arr[0] = 1;
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.ArrayStoreException: java.lang.Integer\n", null, null),
      Trinity.create("\tat SomeClass.main(SomeClass.java:5)\n", 5, 12),
      Trinity.create("\tat SomeClass.unknown(SomeClass.java:0)\n", 1, 1),
      Trinity.create("\tat SomeClass.unknown(SomeClass.java:1)\n", 1, 1));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testArrayIndexFilter() {
    @Language("JAVA") String classText =
      """
        class Test {
          public static void main(String[] args) {
            System.out.println(args[0] + args[1]);
          }
        }
        """;
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.ArrayIndexOutOfBoundsException: 0\n", null, null),
      Trinity.create("\tat Test.main(Test.java:3)\n", 3, 29));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testClassCastGeneric() {
    @Language("JAVA") String classText =
      """
        import java.util.Collections;
        import java.util.List;

        /** @noinspection ALL*/
        class Test {
          public static void main(String[] args) {
            List<String> origList = Collections.singletonList("foo");
            List<Integer> casted = (List<Integer>) (List<?>) origList;
            System.out.println(origList.get(0).length()+casted.get(0));
          }
        }
        """;
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Integer\n", null, null),
      Trinity.create("\tat Test.main(Test.java:9)\n", 9, 56));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testClassCastGenericBound() {
    @Language("JAVA") String classText =
      """
        import java.util.Collections;
        import java.util.List;

        /** @noinspection ALL*/
        class Test {
          static <T extends Number> void test(List<T> list) {
            T t = list.isEmpty() ? null : list.get(0);
            System.out.println(list.size() + ":" + t);
          }

          public static void main(String[] args) {
            List<String> origList = Collections.singletonList("foo");
            List<Integer> casted = (List<Integer>) (List<?>) origList;
            test(casted);
          }
        }
        """;
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.ClassCastException: class java.lang.String cannot be cast to class java.lang.Number" +
                     " (java.lang.String and java.lang.Number are in module java.base of loader 'bootstrap')\n", null, null),
      Trinity.create("\tat Test.test(Test.java:7)\n", 7, 40),
      Trinity.create("\tat Test.main(Test.java:14)\n", 14, 5));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testClassCastIntersection() {
    @Language("JAVA") String classText =
      """
        import java.util.RandomAccess;

        /** @noinspection ALL*/
        class Test {
          public static void main(String[] args) {
            Object x = "foo";
            System.out.println((String & CharSequence) x + "-" + (String & RandomAccess)x);
          }
        }
        """;
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.ClassCastException: class java.lang.String cannot be cast to class java.util.RandomAccess\n", null, null),
      Trinity.create("\tat Test.main(Test.java:7)\n", 7, 59));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testClassCastIntersectionBound() {
    @Language("JAVA") String classText =
      """
        import java.util.Collections;
        import java.util.List;

        /** @noinspection ALL*/
        class Test {
          abstract class NumStr extends Number implements CharSequence {}

          public static void main(String[] args) {
            foo((List<NumStr>) (List<?>) Collections.singletonList(1));
          }

          static <T extends Number & CharSequence> void foo(List<T> list) {
            System.out.println(list.get(0).length());
          }
        }
        """;
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.ClassCastException: java.lang.Integer cannot be cast to java.lang.CharSequence\n", null, null),
      Trinity.create("\tat Test.foo(Test.java:13)\n", 13, 29),
      Trinity.create("\tat Test.main(Test.java:9)\n", 9, 5));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testClassCastPrimitive() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        class Test {
          public static void main(String[] args) {
            Object x = 123;
            System.out.println((int) x + "-" + (long) x);
          }
        }
        """;
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.ClassCastException: java.lang.Integer cannot be cast to java.lang.Long\n", null, null),
      Trinity.create("\tat Test.main(Test.java:5)\n", 5, 41));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testClassCastArray() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        class Test {
          public static void main(String[] args) {
            Object x = new int[0];
            System.out.println((int[]) x + "-" + (char[]) x);
          }
        }
        """;
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.ClassCastException: class [I cannot be cast to class [C\n", null, null),
      Trinity.create("\tat Test.main(Test.java:5)\n", 5, 43));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testClassCastNestedArray() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        class Test {
          public static void main(String[] args) {
            Object x = new int[0][0];
            System.out.println((int[][]) x + "-" + (Object[]) x + "-" + (char[][])x);
          }
        }
        """;
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.ClassCastException: class [[I cannot be cast to class [[C\n", null, null),
      Trinity.create("\tat Test.main(Test.java:5)\n", 5, 66));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testColumnFinderNegativeArraySize() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        public class SomeClass {
          public static void main(String[] args) {
            int a = -1;
            Object[] arr = new String[1][a], arr2 = new String[] {"foo"}, arr3 = new String[(2)][1];
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.NegativeArraySizeException\n", null, null),
      Trinity.create("\tat SomeClass.main(SomeClass.java:5)\n", 5, 34));
    checkColumnFinder(classText, traceAndPositions);
  }
  
  public void testColumnFinderDivisionByZero() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        public class SomeClass {
          public static void main(String[] args) {
            int a = 0;
            double b = 1.1;
            double res = 1 / a / -2 + a / 2 + b / 0;
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.ArithmeticException: / by zero\n", null, null),
      Trinity.create("\tat SomeClass.main(SomeClass.java:6)\n", 6, 22));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testNpeInvoke() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        class Test {
          public static void main(String[] args) {
            Object x = null;
            System.out.println((x.toString().trim() + "xyz").toString());
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.NullPointerException: Cannot invoke \"Object.toString()\" because \"x\" is null\n", null, null),
      Trinity.create("\tat Test.main(Test.java:5)\n", 5, 25));
    checkColumnFinder(classText, traceAndPositions);
  }
  
  public void testNpeJetBrains() {
    @Language("JAVA") String classText =
      """
        package foo.bar;
        class Test {
          void caller(String a, String b, String c) {
            callee(a, b, c);
          }

          void callee(String x, String y, String z) {}
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("java.lang.IllegalArgumentException: Argument for @NotNull parameter 'y' of foo/bar/Test.callee must not be null\n", null, null),
      Trinity.create("\tat foo.bar.Test.$$$reportNull$$$0(Test.java)\n", null, null),
      Trinity.create("\tat foo.bar.Test.callee(Test.java)\n", null, null),
      Trinity.create("\tat foo.bar.Test.caller(Test.java:4)\n", 4, 15));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testNpeSynchronized() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        class Test {
          public static void main(String[] args) {
            Object x = null;
            synchronized (x) { System.out.println(x.hashCode()); }
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.NullPointerException: Cannot enter synchronized block because \"x\" is null\n", null, null),
      Trinity.create("\tat Test.main(Test.java:5)\n", 5, 19));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testNpeThrow() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        class Test {
          public static void main(String[] args) {
            Error x = null;
            throw args.length == 0 ? x : new Error();
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.NullPointerException: Cannot throw exception\n", null, null),
      Trinity.create("\tat Test.main(Test.java:5)\n", 5, 11));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testNpeArrayRead() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        class Test {
          public static void main(String[] args) {
            int[] arr = null;
            int[] arr2 = new int[1];
            arr2[0] = arr[0];
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.NullPointerException: Cannot load from int array because \"arr\" is null\n", null, null),
      Trinity.create("\tat Test.main(Test.java:6)\n", 6, 15));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testNpeArrayWrite() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        class Test {
          public static void main(String[] args) {
            int[] arr = new int[1];
            int[] arr2 = null;
            arr2[0] = arr[0];
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.NullPointerException: Cannot store to int array because \"arr2\" is null\n", null, null),
      Trinity.create("\tat Test.main(Test.java:6)\n", 6, 5));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testNpeArrayLength() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        class Test {
          int length;

          public static void main(String[] args) {
            int[] arr = null;
            Test test = new Test();
            test.length = test.length + arr.length;
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.NullPointerException: Cannot read the array length because \"arr\" is null\n", null, null),
      Trinity.create("\tat Test.main(Test.java:8)\n", 8, 33));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testNpeFieldRead() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        class Test {
          int length;

          public static void main(String[] args) {
            int[] arr = new int[0];
            Test test = null;
            test.length = test.length + arr.length;
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.NullPointerException: Cannot read field \"length\" because \"test\" is null\n", null, null),
      Trinity.create("\tat Test.main(Test.java:8)\n", 8, 19));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testNpeFieldWrite() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        class Test {
          int length;

          public static void main(String[] args) {
            int[] arr = new int[0];
            Test test = null, test2 = new Test();
            test.length = test2.length + arr.length;
          }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.NullPointerException: Cannot assign field \"length\" because \"test\" is null\n", null, null),
      Trinity.create("\tat Test.main(Test.java:8)\n", 8, 5));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testNpeUnboxing() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        public class MainTest {
            public static void main(String[] args) {
                System.out.println(getIntegerData() + getLongData());
            }

            static Integer getIntegerData() { return Math.random() > 0.5 ? 1 : null; }
            static Long getLongData() { return Math.random() > 0.5 ? 1L : null; }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("java.lang.NullPointerException: Cannot invoke \"java.lang.Integer.intValue()\" because the return value of \"MainTest.getIntegerData()\" is null\n", null, null),
      Trinity.create("\tat MainTest.main(MainTest.java:4)\n", 4, 28));
    checkColumnFinder(classText, traceAndPositions);
  }
  
  public void testNpeUnboxing2() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        public class MainTest {
            public static void main(String[] args) {
                System.out.println(getIntegerData() + getLongData());
            }

            static Integer getIntegerData() { return Math.random() > 0.5 ? 1 : null; }
            static Long getLongData() { return Math.random() > 0.5 ? 1L : null; }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("java.lang.NullPointerException: Cannot invoke \"java.lang.Long.longValue()\" because the return value of \"MainTest.getLongData()\" is null\n", null, null),
      Trinity.create("\tat MainTest.main(MainTest.java:4)\n", 4, 47));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testNpeUnboxingArray() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        public class MainTest {
            public static void main(String[] args) {
                System.out.println(arr[0]+arr2[0]);
            }

            static Character[] arr = {null};
            static Double[] arr2 = {null};
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("java.lang.NullPointerException: Cannot invoke \"java.lang.Character.charValue()\" because \"MainTest.arr[0]\" is null\n", null, null),
      Trinity.create("\tat MainTest.main(MainTest.java:4)\n", 4, 28));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testNpeGetClassOnMethodReference() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        public class MainTest {
            public static void main(String[] args) {
                String s = null;
                Runnable r = s::trim;
            }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("java.lang.NullPointerException: Cannot invoke \"Object.getClass()\" because \"s\" is null\n", null, null),
      Trinity.create("\tat MainTest.main(MainTest.java:5)\n", 5, 22));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testNpeGetClassOnQualifiedNew() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        public class MainTest {
            public static void main(String[] args) {
                MainTest test = null;
                test.new X();
            }

            class X{}
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("java.lang.NullPointerException: Cannot invoke \"Object.getClass()\" because \"s\" is null\n", null, null),
      Trinity.create("\tat MainTest.main(MainTest.java:5)\n", 5, 9));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testNpeRequireNonNullOnSwitch() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        public class MainTest {
            public static void main(String[] args) {
                String test = null;
                switch(test) {}
            }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("Exception in thread \"main\" java.lang.NullPointerException\n", null, null),
      Trinity.create("\tat java.base/java.util.Objects.requireNonNull(Objects.java:222)\n", null, null),
      Trinity.create("\tat MainTest.main(MainTest.java:5)\n", 5, 16));
    checkColumnFinder(classText, traceAndPositions);
  }

  public void testNpePoorMan() {
    @Language("JAVA") String classText =
      """
        /** @noinspection ALL*/
        public class MainTest {
            public static void main(String[] args) {
                String s = getString().trim();
            }

            private static String getString() {
                return Math.random() > 0.5 ? "foo" : null;
            }
        }""";
    List<Trinity<String, Integer, Integer>> traceAndPositions = Arrays.asList(
      Trinity.create("java.lang.NullPointerException\n", null, null),
      Trinity.create("\tat MainTest.main(MainTest.java:4)\n", 4, 20));
    checkColumnFinder(classText, traceAndPositions);
  }
  
  public void testInternalFrames() {
    @Language("JAVA") String classText =
      """
        import java.util.stream.Collectors;

        public class InternalFrames {
          public static void main(String[] args) {
            Runnable r = () -> {
              String frames = StackWalker.getInstance(StackWalker.Option.SHOW_HIDDEN_FRAMES)
                  .walk(s -> s.map(sf -> "\\tat " + sf.toStackTraceElement() + "\\n").collect(Collectors.joining()));
              System.out.println(frames);
            };
            r.run();
          }
        }
        """;
    List<Trinity<String, Integer, Integer>> traceAndPositions = List.of(
      Trinity.create("\tat InternalFrames.lambda$main$2(InternalFrames.java:7)\n", 7, 1),
      Trinity.create("\tat InternalFrames$$Lambda$14/0x0000000800c01200.run(Unknown Source)\n", null, null),
      Trinity.create("\tat InternalFrames.main(InternalFrames.java:10)\n", 10, 7));
    checkColumnFinder(classText, traceAndPositions);
  }
  
  public void testParseExceptionLine() {
    String exceptionLine = "Caused by: java.lang.AssertionError: expected same";
    ExceptionInfo info = ExceptionInfo.parseMessage(exceptionLine, exceptionLine.length());
    assertNotNull(info);
    assertEquals("java.lang.AssertionError", info.getExceptionClassName());
  }

  private void checkColumnFinder(String classText, List<Trinity<String, Integer, Integer>> traceAndPositions) {
    myFixture.configureByText("SomeClass.java", classText);
    Editor editor = myFixture.getEditor();
    assertEquals(classText, editor.getDocument().getText());
    ExceptionFilter filter = new ExceptionFilter(myFixture.getFile().getResolveScope());
    for (Trinity<String, Integer, Integer> line : traceAndPositions) {
      String stackLine = line.getFirst();
      Filter.Result result = filter.applyFilter(stackLine, stackLine.length());
      Integer row = line.getSecond();
      Integer column = line.getThird();
      if (row != null) {
        assertNotNull(result);
        HyperlinkInfo info = result.getFirstHyperlinkInfo();
        assertNotNull(info);
        info.navigate(getProject());
        LogicalPosition actualPos = editor.getCaretModel().getLogicalPosition();
        assertEquals(new LogicalPosition(row - 1, column - 1), actualPos);
      }
    }
  }
}

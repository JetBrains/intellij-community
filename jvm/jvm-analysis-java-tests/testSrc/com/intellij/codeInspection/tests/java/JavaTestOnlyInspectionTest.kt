package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.TestOnlyInspectionTestBase

class JavaTestOnlyInspectionTest : TestOnlyInspectionTestBase() {
  fun `test @TestOnly not highlighting in javadoc`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      package test;

      import org.jetbrains.annotations.TestOnly;
      import org.jetbrains.annotations.VisibleForTesting;
      import java.util.function.Function;

      class TestOnlyDoc {
        @TestOnly
        TestOnlyDoc() { }

        @TestOnly
        static String testMethod() { return "Foo"; }

        /**
         * {@link #TestOnlyTest()}
         * {@link #TestOnlyTest#testMethod()}
         * {@link #testMethod()}
         */
        public static void docced() { }
      }
    """.trimIndent())
  }

  fun `test @TestOnly in production code`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      package test;

      import org.jetbrains.annotations.TestOnly;
      import org.jetbrains.annotations.VisibleForTesting;
      import java.util.function.Function;

      class TestOnlyTest {
        @TestOnly
        TestOnlyTest() { }

        @TestOnly
        static String someString(String someStr) { return someStr + "Foo"; }

        @TestOnly
        @<warning descr="@VisibleForTesting makes little sense on @TestOnly code">VisibleForTesting</warning>
        static String doubleAnn() { return "Foo"; }

        static class Bar {
          @TestOnly
          int aField = 0;

          @TestOnly
          void aMethod() { }
        }

        public static void main(String[] args) {
          TestOnlyTest foo = new <warning descr="Test-only class is referenced in production code">TestOnlyTest</warning>();
          Bar bar = new Bar();
          int aField = bar.<warning descr="Test-only field is referenced in production code">aField</warning>;
          bar.<warning descr="Test-only method is called in production code">aMethod</warning>();
          Function<String, String> methodRef = TestOnlyTest::<warning descr="Test-only method is called in production code">someString</warning>;
        }

        @TestOnly
        public static void testOnly() {
          TestOnlyTest foo = new TestOnlyTest();
          Bar bar = new Bar();
          int aField = bar.aField;
          bar.aMethod();
          Function<String, String> methodRef = TestOnlyTest::someString;
        }
      }
    """.trimIndent())
  }

  fun `test @VisibleForTesting in production code`() {
    myFixture.addFileToProject("VisibleForTestingTestApi.java", """
      package test;

      import org.jetbrains.annotations.VisibleForTesting;

      class VisibleForTestingTestApi {
        @VisibleForTesting
        static int foo = x;

        @VisibleForTesting
        static void bar() { }
      }
    """.trimIndent())
    myFixture.testHighlighting(ULanguage.JAVA, """
      package test;

      import org.jetbrains.annotations.VisibleForTesting;
      import test.VisibleForTestingTestApi;

      class VisibleForTestingTest {
        @VisibleForTesting
        static int fooBar = 0;

        public static void main(String[] args) {
          System.out.println(fooBar);
          System.out.println(VisibleForTestingTestApi.<warning descr="Test-only field is referenced in production code">foo</warning>);
          VisibleForTestingTestApi.<warning descr="Test-only method is called in production code">bar</warning>();
        }
      }
    """.trimIndent())
  }
}
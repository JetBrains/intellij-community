package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.JUnitRuleInspectionTestBase

class JavaJUnitRuleInspectionTest : JUnitRuleInspectionTestBase() {
  fun `test @Rule highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      package test;

      import org.junit.Rule;
      import org.junit.rules.TestRule;

      class RuleTest {
        @Rule
        private int <error descr="Fields annotated with '@org.junit.Rule' should be 'public'">x</error>;

        @Rule
        public static int <error descr="Fields annotated with '@org.junit.Rule' should be non-static">y</error>;
      }
    """.trimIndent())
  }

  fun `test @Rule make public`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      package test;

      import org.junit.Rule;
      import org.junit.rules.TestRule;

      class RuleQfTest {
        @Rule
        private int x<caret>;

        @Rule
        public int y;
      }
    """.trimIndent(), """
      package test;
      
      import org.junit.Rule;
      import org.junit.rules.TestRule;
      
      class RuleQfTest {
        @Rule
        public int x;
      
        @Rule
        public int y;
      }
    """.trimIndent(), "Make 'x' public")
  }

  fun `test @ClassRule highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      package test;

      import org.junit.rules.TestRule;
      import org.junit.ClassRule;
      import test.SomeTestRule;

      class ClassRuleTest {
        @ClassRule
        static SomeTestRule <error descr="Fields annotated with '@org.junit.ClassRule' should be 'public'">x</error> = new SomeTestRule();

        @ClassRule
        public SomeTestRule <error descr="Fields annotated with '@org.junit.ClassRule' should be 'static'">y</error> = new SomeTestRule();

        @ClassRule
        private SomeTestRule <error descr="Fields annotated with '@org.junit.ClassRule' should be 'public' and 'static'">z</error> = new SomeTestRule();

        @ClassRule
        public static int <error descr="Field type should be subtype of 'org.junit.rules.TestRule'">t</error> = 0;
      }
    """.trimIndent())
  }

  fun `test @ClassRule make public`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      package test;

      import org.junit.rules.TestRule;
      import org.junit.ClassRule;
      import test.SomeTestRule;

      class ClassRuleTest {
        @ClassRule
        static SomeTestRule x<caret> = new SomeTestRule();
      }
    """.trimIndent(), """
      package test;

      import org.junit.rules.TestRule;
      import org.junit.ClassRule;
      import test.SomeTestRule;

      class ClassRuleTest {
        @ClassRule
        public static SomeTestRule x = new SomeTestRule();
      }
    """.trimIndent(), "Make 'x' public")
  }

  fun `test @ClassRule make static`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      package test;

      import org.junit.rules.TestRule;
      import org.junit.ClassRule;
      import test.SomeTestRule;

      class ClassRuleTest {
        @ClassRule
        public SomeTestRule y<caret> = new SomeTestRule();
      }
    """.trimIndent(), """
      package test;

      import org.junit.rules.TestRule;
      import org.junit.ClassRule;
      import test.SomeTestRule;

      class ClassRuleTest {
        @ClassRule
        public static SomeTestRule y = new SomeTestRule();
      }
    """.trimIndent(), "Make 'y' static")
  }


  fun `test @ClassRule make public and static`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      package test;

      import org.junit.rules.TestRule;
      import org.junit.ClassRule;
      import test.SomeTestRule;

      class ClassRuleTest {
        @ClassRule
        private SomeTestRule z<caret> = new SomeTestRule();
      }
    """.trimIndent(), """
      package test;

      import org.junit.rules.TestRule;
      import org.junit.ClassRule;
      import test.SomeTestRule;

      class ClassRuleTest {
        @ClassRule
        public static SomeTestRule z = new SomeTestRule();
      }
    """.trimIndent(), "Make 'z' public", "Make 'z' static")
  }
}
package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.JUnitRuleInspectionTestBase
import com.intellij.codeInspection.tests.ULanguage

class JavaJUnitRuleInspectionTest : JUnitRuleInspectionTestBase() {
  fun `test field @Rule highlighting modifier`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      package test;

      import org.junit.Rule;
      import org.junit.rules.TestRule;
      import test.SomeTestRule;

      class RuleTest {
        @Rule
        private SomeTestRule <error descr="Fields annotated with '@org.junit.Rule' should be 'public'">x</error>;

        @Rule
        public static SomeTestRule <error descr="Fields annotated with '@org.junit.Rule' should be non-static">y</error>;
      }
    """.trimIndent())
  }

  fun `test field @Rule highlighting type`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      package test;

      import org.junit.Rule;
      import org.junit.rules.TestRule;
      import test.SomeTestRule;

      class RuleTest {
        @Rule
        public int <error descr="Field type should be subtype of 'org.junit.rules.TestRule' or 'org.junit.rules.MethodRule'">x</error>;
      }
    """.trimIndent())
  }

  fun `test method @Rule highlighting modifier`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      package test;

      import org.junit.Rule;
      import org.junit.rules.TestRule;
      import test.SomeTestRule;

      class RuleTest {
        @Rule
        private SomeTestRule <error descr="Methods annotated with '@org.junit.Rule' should be 'public'">x</error>() { 
          return new SomeTestRule();  
        };
        
        @Rule
        public static SomeTestRule <error descr="Methods annotated with '@org.junit.Rule' should be non-static">y</error>() { 
          return new SomeTestRule();  
        };        
      }
    """.trimIndent())
  }

  fun `test field @Rule make public`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      package test;

      import org.junit.Rule;
      import org.junit.rules.TestRule;

      class RuleQfTest {
        @Rule
        private int x<caret>;
      }
    """.trimIndent(), """
      package test;
      
      import org.junit.Rule;
      import org.junit.rules.TestRule;
      
      class RuleQfTest {
        @Rule
        public int x;
      }
    """.trimIndent(), "Make 'x' public")
  }

    fun `test method @Rule make non-static`() {
      myFixture.testQuickFix(ULanguage.JAVA, """
      package test;

      import org.junit.Rule;
      import org.junit.rules.TestRule;

      class RuleQfTest {
        @Rule
        public static int y<caret>() { return 0; }
      }
    """.trimIndent(), """
      package test;
      
      import org.junit.Rule;
      import org.junit.rules.TestRule;
      
      class RuleQfTest {
        @Rule
        public int y() { return 0; }
      }
    """.trimIndent(), "Make 'y' not static")
  }

  fun `test field @ClassRule highlighting`() {
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

  fun `test field @ClassRule make public`() {
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

  fun `test field @ClassRule make static`() {
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


  fun `test field @ClassRule make public and static`() {
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
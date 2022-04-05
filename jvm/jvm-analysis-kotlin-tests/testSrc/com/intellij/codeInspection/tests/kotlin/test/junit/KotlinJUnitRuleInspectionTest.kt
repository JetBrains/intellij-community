package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.JUnitRuleInspectionTestBase
import com.intellij.codeInspection.tests.ULanguage

class KotlinJUnitRuleInspectionTest : JUnitRuleInspectionTestBase() {
  fun `test field @Rule highlighting public`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      package test

      import org.junit.Rule
      import test.SomeTestRule

      class PrivateRule {
        @Rule
        var <error descr="Fields annotated with '@org.junit.Rule' should be 'public'">x</error> = SomeTestRule()
      }
    """.trimIndent())
  }

  fun `test object inherited TestRule`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      package test

      import org.junit.Rule
      import org.junit.rules.TestRule
      
      object OtherRule : TestRule { }
      
      object ObjRule {
        @Rule
        private var <error descr="Fields annotated with '@org.junit.Rule' should be 'public' and non-static">x</error> = SomeTestRule()
      }

      class ClazzRule {
        @Rule
        fun x() = OtherRule
        
        @Rule
        fun <error descr="Method return type should be subtype of 'org.junit.rules.TestRule' or 'org.junit.rules.MethodRule'">y</error>() = 0

        @Rule
        public fun z() = object : TestRule { }

        @Rule
        public fun <error descr="Method return type should be subtype of 'org.junit.rules.TestRule' or 'org.junit.rules.MethodRule'">a</error>() = object { }
      }  
    """.trimIndent())
  }

  fun `test method @Rule highlighting static`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      package test

      import org.junit.Rule

      class PrivateRule {
        @Rule
        private fun <error descr="Methods annotated with '@org.junit.Rule' should be 'public'">x</error>() = SomeTestRule()
      }
    """.trimIndent())
  }

  fun `test method @Rule highlighting type`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      package test

      import org.junit.Rule

      class PrivateRule {
        @Rule
        fun <error descr="Method return type should be subtype of 'org.junit.rules.TestRule' or 'org.junit.rules.MethodRule'">x</error>() = 0
      }
    """.trimIndent())
  }

  fun `test field @Rule quickFix make public`() {
    myFixture.testQuickFix(ULanguage.KOTLIN, """
      package test

      import org.junit.Rule

      class PrivateRule {
        @Rule
        var x<caret> = 0
      }
    """.trimIndent(), """
      package test

      import org.junit.Rule

      class PrivateRule {
        @kotlin.jvm.JvmField
        @Rule
        var x = 0
      }
    """.trimIndent(), "Make field 'x' public")
  }

  fun `test field @ClassRule highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      package test
      
      import org.junit.rules.TestRule
      import org.junit.ClassRule
      import test.SomeTestRule
      
      object PrivateClassRule {
        @ClassRule
        private var <error descr="Fields annotated with '@org.junit.ClassRule' should be 'public'">x</error> = SomeTestRule()
      
        @ClassRule
        private var <error descr="Field type should be subtype of 'org.junit.rules.TestRule'"><error descr="Fields annotated with '@org.junit.ClassRule' should be 'public'">y</error></error> = 0
      }
    """.trimIndent())
  }

  fun `test field @ClassRule quickfix make public`() {
    myFixture.testQuickFix(ULanguage.KOTLIN, """
      package test

      import org.junit.rules.TestRule
      import org.junit.ClassRule
      import test.SomeTestRule

      object PrivateClassRule {
        @ClassRule
        private var x<caret> = SomeTestRule()
      }
    """.trimIndent(), """
      package test

      import org.junit.rules.TestRule
      import org.junit.ClassRule
      import test.SomeTestRule

      object PrivateClassRule {
        @kotlin.jvm.JvmField
        @ClassRule
        var x = SomeTestRule()
      }
    """.trimIndent(), "Make field 'x' public")
  }
}
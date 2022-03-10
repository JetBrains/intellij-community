package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.JUnitRuleInspectionTestBase

class KotlinJUnitRuleInspectionTest : JUnitRuleInspectionTestBase() {
  fun `test @Rule highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      package test

      import org.junit.Rule

      class PrivateRule {
        @Rule
        private var <error descr="Fields annotated with '@org.junit.Rule' should be 'public'">x</error> = 0
      }
    """.trimIndent())
  }

  fun `test @Rule quickFixes`() {
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
    """.trimIndent(), "Annotate as @JvmField")
  }

  fun `test @ClassRule highlighting`() {
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

  fun `test @ClassRule quickFixes`() {
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
    """.trimIndent(), "Remove 'private' modifier", "Annotate as @JvmField")
  }
}
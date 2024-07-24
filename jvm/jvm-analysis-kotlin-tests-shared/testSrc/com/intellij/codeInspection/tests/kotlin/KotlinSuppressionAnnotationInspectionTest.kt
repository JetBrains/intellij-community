package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.SuppressionAnnotationInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinSuppressionAnnotationInspectionTest : SuppressionAnnotationInspectionTestBase(), KotlinPluginModeProvider {

  fun `test highlighting`() {
    inspection.myAllowedSuppressions.add("FreeSpeech")
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN,
      """
      @<warning descr="Annotation suppresses 'ALL' and 'SuppressionAnnotation'">Suppress</warning>("ALL", "SuppressionAnnotation")
      class A {
        @<warning descr="Annotation suppresses 'PublicField'">Suppress</warning>("PublicField")
        var s: String? = null
        @<warning descr="Annotation suppresses">Suppress</warning>
        var t: String? = null
        
        fun foo() {
          <warning descr="Comment suppresses 'HardCodedStringLiteral'">//noinspection HardCodedStringLiteral</warning>
          any("hello")
          <warning descr="Comment suppresses">// noinspection</warning>
          any()
        }
        
        @Suppress("FreeSpeech")
        fun bar() {
          // noinspection FreeSpeech
          any()
        }
      }
      
      private fun any(s: String? = null): String? = s
        """.trimIndent()
    )
  }

  fun `test quickfix - remove annotation`() {
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Supp<caret>ress("PublicField", "HardCodedStringLiteral")
            var s: String = "test"
          }
        """.trimIndent(), """
          class A {
            var s: String = "test"
          }
        """.trimIndent(), "Remove '@Suppress' annotation", testPreview = true)
  }

  fun `test quickfix - remove comment`() {
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
          class A {
            //noinspection PublicField, Hard<caret>CodedStringLiteral
            var s: String = "test"
          }
        """.trimIndent(), """
          class A {
            var s: String = "test"
          }
        """.trimIndent(), "Remove //noinspection", testPreview = true)
  }

  fun `test quickfix - allow a single suppression from annotation`() {
    testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Supp<caret>ress("PublicField")
            var s: String = "test"
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow a single suppression from annotation when array form used`() {
    testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Supp<caret>ress(["PublicField"])
            var s: String = "test"
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow a single suppression from annotation when explicit attribute name exists`() {
    testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Supp<caret>ress(names = "PublicField")
            var s: String = "test"
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow multiple suppressions from annotation`() {
    testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Supp<caret>ress("PublicField", "HardCodedStringLiteral")
            var s: String = "test"
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  fun `test quickfix - allow multiple suppressions from annotation when array form used`() {
    testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Supp<caret>ress(["PublicField", "HardCodedStringLiteral"])
            var s: String = "test"
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  fun `test quickfix - allow multiple suppressions from annotation when explicit attribute name exists`() {
    testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Supp<caret>ress(names = ["PublicField", "HardCodedStringLiteral"])
            var s: String = "test"
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  fun `test quickfix - allow multiple suppressions from annotation when constants used`() {
    testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          object Constants {
            const val PUBLIC_FIELD = "PublicField"
            const val HARD_CODED_STRING_LITERAL = "HardCodedStringLiteral"
          }
          
          class A {
            @Supp<caret>ress([Constants.PUBLIC_FIELD, Constants.HARD_CODED_STRING_LITERAL])
            var s: String = "test"
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  fun `test quickfix - allow a single suppression from comment`() {
    testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            //noinspection Public<caret>Field
            var s: String = "test"
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow multiple suppressions from comment`() {
    testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            //noinspection Public<caret>Field, Hard<caret>CodedStringLiteral
            var s: String = "test"
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }
}

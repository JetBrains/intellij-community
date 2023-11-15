package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.SuppressionAnnotationInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class KotlinSuppressionAnnotationInspectionTest : SuppressionAnnotationInspectionTestBase() {

  fun `test highlighting`() {
    inspection.myAllowedSuppressions.add("FreeSpeech")
    myFixture.testHighlighting(
      JvmLanguage.KOTLIN,
      """
        <warning descr="Inspection suppression annotation '@Suppress(\"ALL\", \"SuppressionAnnotation\")'">@Suppress("ALL", "SuppressionAnnotation")</warning>
        class A {
          <warning descr="Inspection suppression annotation '@Suppress(\"PublicField\")'">@Suppress("PublicField")</warning>
          var s: String? = null
          <warning descr="Inspection suppression annotation '@Suppress'">@Suppress</warning>
          var t: String? = null
          
          fun foo() {
            <warning descr="Inspection suppression annotation '//noinspection HardCodedStringLiteral'">//noinspection HardCodedStringLiteral</warning>
            any("hello")
            <warning descr="Inspection suppression annotation '// noinspection'">// noinspection</warning>
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
            @Suppress("PublicField", "Hard<caret>CodedStringLiteral")
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
            @Suppress("Public<caret>Field")
            var s: String = "test"
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow a single suppression from annotation when array form used`() {
    testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Suppress(["Public<caret>Field"])
            var s: String = "test"
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow a single suppression from annotation when explicit attribute name exists`() {
    testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Suppress(names = "Public<caret>Field")
            var s: String = "test"
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow multiple suppressions from annotation`() {
    testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Suppress("Public<caret>Field", "HardCodedStringLiteral")
            var s: String = "test"
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  fun `test quickfix - allow multiple suppressions from annotation when array form used`() {
    testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Suppress(["Public<caret>Field", "HardCodedStringLiteral"])
            var s: String = "test"
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  fun `test quickfix - allow multiple suppressions from annotation when explicit attribute name exists`() {
    testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Suppress(names = ["Public<caret>Field", "HardCodedStringLiteral"])
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
            @Suppress([Constants.PUBLIC_<caret>FIELD, Constants.HARD_CODED_STRING_LITERAL])
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

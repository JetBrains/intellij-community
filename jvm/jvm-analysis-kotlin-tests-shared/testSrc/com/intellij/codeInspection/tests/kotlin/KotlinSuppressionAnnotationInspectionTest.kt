package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.jvmAnnotationsProjectFixture
import com.intellij.jvm.analysis.internal.testFramework.suppressionAnnotationInspectionFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinSuppressionAnnotationInspectionTest {
  companion object {
    private val projectFixture = jvmAnnotationsProjectFixture()
  }

  protected val myFixture by suppressionAnnotationInspectionFixture(projectFixture)

  

  @Test
  fun `test highlighting`() {
    myFixture.inspection.myAllowedSuppressions.add("FreeSpeech")
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

  @Test
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

  @Test
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

  @Test
  fun `test quickfix - allow a single suppression from annotation`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Supp<caret>ress("PublicField")
            var s: String = "test"
          }
        """.trimIndent(), "PublicField")
  }

  @Test
  fun `test quickfix - allow a single suppression from annotation when array form used`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Supp<caret>ress(["PublicField"])
            var s: String = "test"
          }
        """.trimIndent(), "PublicField")
  }

  @Test
  fun `test quickfix - allow a single suppression from annotation when explicit attribute name exists`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Supp<caret>ress(names = "PublicField")
            var s: String = "test"
          }
        """.trimIndent(), "PublicField")
  }

  @Test
  fun `test quickfix - allow multiple suppressions from annotation`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Supp<caret>ress("PublicField", "HardCodedStringLiteral")
            var s: String = "test"
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  @Test
  fun `test quickfix - allow multiple suppressions from annotation when array form used`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Supp<caret>ress(["PublicField", "HardCodedStringLiteral"])
            var s: String = "test"
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  @Test
  fun `test quickfix - allow multiple suppressions from annotation when explicit attribute name exists`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            @Supp<caret>ress(names = ["PublicField", "HardCodedStringLiteral"])
            var s: String = "test"
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  @Test
  fun `test quickfix - allow multiple suppressions from annotation when constants used`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
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

  @Test
  fun `test quickfix - allow a single suppression from comment`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            //noinspection Public<caret>Field
            var s: String = "test"
          }
        """.trimIndent(), "PublicField")
  }

  @Test
  fun `test quickfix - allow multiple suppressions from comment`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.KOTLIN, """
          class A {
            //noinspection Public<caret>Field, Hard<caret>CodedStringLiteral
            var s: String = "test"
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }
}

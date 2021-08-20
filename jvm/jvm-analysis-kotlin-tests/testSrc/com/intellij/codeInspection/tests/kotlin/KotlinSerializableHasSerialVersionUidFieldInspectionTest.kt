package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.SerializableHasSerialVersionUidFieldInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil

class KotlinSerializableHasSerialVersionUidFieldInspectionTest : SerializableHasSerialVersionUidFieldInspectionTestBase() {
  override fun getBasePath() =
    KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/serializablehasserialversionuidfield"

  fun `test highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import java.io.Serializable

      class <warning descr="'Foo' does not define a 'serialVersionUID' field">Foo</warning> : Serializable { }
    """.trimIndent())
  }

  fun `test quickfix`() {
    myFixture.testQuickFix(ULanguage.KOTLIN, """
      import java.io.Serializable
      
      class Fo<caret>o : Serializable { }
    """.trimIndent(), """
      import java.io.Serializable
      
      class Foo : Serializable {
          companion object {
              private const val serialVersionUID: Long = 7429157667498829299L
          }
      }
    """.trimIndent(), "Add 'serialVersionUID' field")
  }
}
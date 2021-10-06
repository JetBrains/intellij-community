package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.SerializableHasSerialVersionUidFieldInspectionTestBase
import com.intellij.pom.java.LanguageLevel

class KotlinSerializableHasSerialVersionUidFieldInspectionJdk14Test : SerializableHasSerialVersionUidFieldInspectionTestBase() {
  override val languageLevel: LanguageLevel = LanguageLevel.JDK_14

  fun `test quickfix @Serial annotation`() {
    myFixture.testQuickFix(ULanguage.KOTLIN, """
      import java.io.Serializable
      
      class Fo<caret>o : Serializable { }
    """.trimIndent(), """
      import java.io.Serializable
      
      class Foo : Serializable {
          companion object {
              @java.io.Serial
              private const val serialVersionUID: Long = 7429157667498829299L
          }
      }
    """.trimIndent(), "Add 'const val' property 'serialVersionUID' to 'Foo'")
  }
}
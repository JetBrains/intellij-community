package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.SerializableHasSerialVersionUidFieldInspectionTestBase
import com.intellij.pom.java.LanguageLevel

class KotlinSerializableHasSerialVersionUidFieldInspectionTest : SerializableHasSerialVersionUidFieldInspectionTestBase() {
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
    """.trimIndent(), "Add 'const val' property 'serialVersionUID' to 'Foo'")
  }

  fun `test quickfix companion exists`() {
    myFixture.testQuickFix(ULanguage.KOTLIN, """
      import java.io.Serializable
      
      class Fo<caret>o : Serializable {
          companion object {
              val bar =  0
          }
      }
    """.trimIndent(), """
      import java.io.Serializable
      
      class Foo : Serializable {
          companion object {
              private const val serialVersionUID: Long = -7315889077010185135L
              val bar =  0
          }
      }
    """.trimIndent(), "Add 'const val' property 'serialVersionUID' to 'Foo'")
  }

  fun `test quickfix @Serial annotation`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_14)
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
package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.jvmSdkProjectFixture
import com.intellij.jvm.analysis.internal.testFramework.serializableHasSerialVersionUidFieldInspectionFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinSerializableHasSerialVersionUidFieldInspectionTest {
  companion object {
    private val projectFixture = jvmSdkProjectFixture()
  }

  protected val myFixture by serializableHasSerialVersionUidFieldInspectionFixture(projectFixture)

  

  abstract fun getHint(): String

  @Test
  fun `test highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.io.Serializable

      class <warning descr="'Foo' does not define a 'serialVersionUID' field">Foo</warning> : Serializable { }
    """.trimIndent())
  }

  @Test
  fun `test quickfix`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_11)
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
      import java.io.Serializable
      
      class Fo<caret>o : Serializable { }
    """.trimIndent(), """
      import java.io.Serializable
      
      class Foo : Serializable {
          companion object {
              private const val serialVersionUID: Long = 7429157667498829299L
          }
      }
    """.trimIndent(), getHint())
  }

  @Test
  fun `test quickfix companion exists`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_11)
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
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
    """.trimIndent(), getHint())
  }

  @Test
  fun `test quickfix @Serial annotation`() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_14) {
      myFixture.testQuickFix(JvmLanguage.KOTLIN, """
        import java.io.Serializable
        
        class Fo<caret>o : Serializable { }
      """.trimIndent(), """
        import java.io.Serial
        import java.io.Serializable
        
        class Foo : Serializable {
            companion object {
                @Serial
                private const val serialVersionUID: Long = 7429157667498829299L
            }
        }
      """.trimIndent(), getHint())
    }
  }
}
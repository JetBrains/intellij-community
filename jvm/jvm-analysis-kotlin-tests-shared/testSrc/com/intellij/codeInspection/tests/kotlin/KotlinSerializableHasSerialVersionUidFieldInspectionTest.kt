package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.SerializableHasSerialVersionUidFieldInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinSerializableHasSerialVersionUidFieldInspectionTest : SerializableHasSerialVersionUidFieldInspectionTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  abstract fun getHint(): String

  fun `test highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.io.Serializable

      class <warning descr="'Foo' does not define a 'serialVersionUID' field">Foo</warning> : Serializable { }
    """.trimIndent())
  }

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

  fun `test quickfix @Serial annotation`() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_14) {
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
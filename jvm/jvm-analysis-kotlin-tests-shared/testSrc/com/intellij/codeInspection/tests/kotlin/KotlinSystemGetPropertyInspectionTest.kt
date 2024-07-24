package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.SystemGetPropertyInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinSystemGetPropertyInspectionTest : SystemGetPropertyInspectionTestBase(), KotlinPluginModeProvider {
  fun `test highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
          fun foo() {
          System.<warning descr="Call 'getProperty' can be simplified for 'file.separator'">getProperty</warning>("file.separator")
          System.<warning descr="Call 'getProperty' can be simplified for 'path.separator'">getProperty</warning>("path.separator")
          System.<warning descr="Call 'getProperty' can be simplified for 'line.separator'">getProperty</warning>("line.separator")
          System.<warning descr="Call 'getProperty' can be simplified for 'file.encoding'">getProperty</warning>("file.encoding")
          }
          """.trimIndent())
  }

  fun `test quickfix file-separator`() {
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
      fun foo() {
          System.getPrope<caret>rty("file.separator")
      }
    """.trimIndent(), """
      import java.nio.file.FileSystems
      
      fun foo() {
          FileSystems.getDefault().getSeparator()
      }
    """.trimIndent(), "Replace with 'java.nio.file.FileSystems.getDefault().getSeparator()'", true)
  }

  fun `test quickfix path-separator`() {
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
      fun foo() {
          System.getPrope<caret>rty("path.separator")
      }
    """.trimIndent(), """
      import java.io.File
      
      fun foo() {
          File.pathSeparator
      }
    """.trimIndent(), "Replace with 'java.io.File.pathSeparator'", true)
  }

  fun `test quickfix line-separator`() {
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
      fun foo() {
          System.getPrope<caret>rty("line.separator")
      }
    """.trimIndent(), """
      fun foo() {
          System.lineSeparator()
      }
    """.trimIndent(), "Replace with 'java.lang.System.lineSeparator()'", true)
  }

  fun `test quickfix file-encoding`() {
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
      fun foo() {
          System.getPrope<caret>rty("file.encoding")
      }
    """.trimIndent(), """
      import java.nio.charset.Charset
      
      fun foo() {
          Charset.defaultCharset().displayName()
      }
    """.trimIndent(), "Replace with 'java.nio.charset.Charset.defaultCharset().displayName()'", true)
  }
}
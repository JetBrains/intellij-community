package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.SystemGetPropertyInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaSystemGetPropertyInspectionTest : SystemGetPropertyInspectionTestBase() {
  fun `test highlighting`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
        class Foo {
          public static void bar() {
          System.<warning descr="Call 'getProperty' can be simplified for 'file.separator'">getProperty</warning>("file.separator");
          System.<warning descr="Call 'getProperty' can be simplified for 'path.separator'">getProperty</warning>("path.separator");
          System.<warning descr="Call 'getProperty' can be simplified for 'line.separator'">getProperty</warning>("line.separator");
          System.<warning descr="Call 'getProperty' can be simplified for 'file.encoding'">getProperty</warning>("file.encoding");
          }
        }
          """.trimIndent())
  }

  fun `test quickfix file-separator`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      class Foo {
        fun bar() {
            System.getPrope<caret>rty("file.separator");
        }
      }
    """.trimIndent(), """
      import java.nio.file.FileSystems;
      
      class Foo {
        fun bar() {
            FileSystems.getDefault().getSeparator();
        }
      }
    """.trimIndent(), "Replace with 'java.nio.file.FileSystems.getDefault().getSeparator()'", true)
  }

  fun `test quickfix path-separator`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      class Foo {
        fun bar() {
          System.getPrope<caret>rty("path.separator");
        }
      }
    """.trimIndent(), """
      import java.io.File;
      
      class Foo {
        fun bar() {
          File.pathSeparator;
        }
      }
    """.trimIndent(), "Replace with 'java.io.File.pathSeparator'", true)
  }

  fun `test quickfix line-separator`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      class Foo {
        fun bar() {
          System.getPrope<caret>rty("line.separator");
        }
      }
    """.trimIndent(), """
      class Foo {
        fun bar() {
          System.lineSeparator();
        }
      }
    """.trimIndent(), "Replace with 'java.lang.System.lineSeparator()'", true)
  }

  fun `test quickfix file-encoding`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      class Foo {
        fun bar() {
          System.getPrope<caret>rty("file.encoding");
        }
      }
    """.trimIndent(), """
      import java.nio.charset.Charset;
      
      class Foo {
        fun bar() {
          Charset.defaultCharset().displayName();
        }
      }
    """.trimIndent(), "Replace with 'java.nio.charset.Charset.defaultCharset().displayName()'", true)
  }
}
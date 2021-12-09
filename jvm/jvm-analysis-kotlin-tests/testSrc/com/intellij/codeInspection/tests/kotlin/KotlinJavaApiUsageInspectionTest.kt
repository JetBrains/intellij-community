package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.JavaApiUsageInspectionTestBase
import com.intellij.pom.java.LanguageLevel

class KotlinJavaApiUsageInspectionTest : JavaApiUsageInspectionTestBase() {
  fun `test constructor`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_4)
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      fun foo() {
          throw <error descr="Usage of API documented as @since 1.5+">IllegalArgumentException</error>("", RuntimeException());
      }
    """.trimIndent())
  }

  fun `test ignored`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.addClass("""
      package java.awt.geom; 
      
      public class GeneralPath {
        public void moveTo(int x, int y) { }
      }
    """.trimIndent())
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import java.awt.geom.GeneralPath
      
      fun foo() {
        val path = GeneralPath()
        path.moveTo(0, 0)
      }
    """.trimIndent())
  }

  fun `test qualified reference`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import java.nio.charset.StandardCharsets
      
      fun main() {
        <error descr="Usage of API documented as @since 1.7+">StandardCharsets</error>.UTF_8
      }
    """.trimIndent())
  }

  fun `test annotation`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      @file:Suppress("UNUSED_PARAMETER")
      
      class Annotation {
        @<error descr="Usage of API documented as @since 1.7+">SafeVarargs</error>
        fun foo(vararg ls: List<String>) { }
      }
    """.trimIndent())
  }

  fun `test override annotation`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      @file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
      import java.util.Map

      abstract class OverrideAnnotation : Map<String, String> {
        override fun <error descr="Usage of API documented as @since 1.8+">getOrDefault</error>(key: Any?, defaultValue: String?): String {
          return ""
        }
      }
    """.trimIndent())
  }

  fun `test default methods`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      @file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNUSED_VARIABLE")
      import java.util.Iterator

      class <error descr="Default method 'remove' is not overridden. It would cause compilation problems with JDK 6">DefaultMethods</error> : Iterator<String> {
        override fun hasNext(): Boolean {
          return false
        }

        override fun next(): String {
          return ""
        }

        class T : Iterator<String> {
          override fun hasNext(): Boolean {
            return false
          }

          override fun next(): String {
            return ""
          }

          override fun remove() { }
        }

        init {
          val it = <error descr="Default method 'remove' is not overridden. It would cause compilation problems with JDK 6">object</error> : Iterator<String> {
            override fun hasNext(): Boolean {
              return false
            }

            override fun next(): String {
              return ""
            }
          }
        }
      }
    """.trimIndent())
  }

  fun `test raw inherit from newly generified`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.addClass("""
      package javax.swing;
      
      public class AbstractListModel<K> {}
    """.trimIndent())
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class RawInheritFromNewlyGenerified {
        private lateinit var myModel: AbstractCCM<String>
      }

      abstract class AbstractCCM<T> : javax.swing.AbstractListModel<String>() { }
    """.trimIndent())
  }

  fun `test generified`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.addClass("""
      package javax.swing;
      
      public interface ListModel<E> { }
    """.trimIndent())
    myFixture.addClass("""
      package javax.swing;
      
      public class AbstractListModel<K> implements ListModel<E> { }
    """.trimIndent())
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import javax.swing.AbstractListModel
      
      abstract class AbstractCCM<T> : <error descr="Usage of generified after 1.6 API which would cause compilation problems with JDK 6">AbstractListModel</error><T>() { }
    """.trimIndent())
  }
}
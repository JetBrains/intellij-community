package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.JavaApiUsageInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinJavaApiUsageInspectionTest : JavaApiUsageInspectionTestBase(), KotlinPluginModeProvider {
  fun `test constructor`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_4)
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
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
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.awt.geom.GeneralPath
      
      fun foo() {
        val path = GeneralPath()
        path.moveTo(0, 0)
      }
    """.trimIndent())
  }

  fun `test qualified reference`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.nio.charset.StandardCharsets
      
      fun main() {
        <error descr="Usage of API documented as @since 1.7+">StandardCharsets</error>.UTF_8
      }
    """.trimIndent())
  }

  fun `test reference in callable reference`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    val withErrorMessage = "\"default charset \${<error descr=\"Usage of API documented as @since 1.7+\">StandardCharsets</error>.UTF_8}\"::toString"
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.nio.charset.StandardCharsets

      fun main() {
        ${withErrorMessage}
      }
    """.trimIndent())
    ""::toString
  }

  fun `test annotation`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      @file:Suppress("UNUSED_PARAMETER")
      
      class Annotation {
        @<error descr="Usage of API documented as @since 1.7+">SafeVarargs</error>
        fun foo(vararg ls: List<String>) { }
      }
    """.trimIndent())
  }

  fun `test override annotation`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
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
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
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

  fun `test single method multiple overrides`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        class CustomList : java.util.AbstractList<Int>() {
          override val size: Int = 0

          override fun get(index: Int): Int = 0

          override fun <error descr="Usage of API documented as @since 1.8+">spliterator</error>(): java.util.<error descr="Usage of API documented as @since 1.8+">Spliterator</error><Int> =
            java.util.<error descr="Usage of API documented as @since 1.8+">Spliterators</error>.spliterator(this, 0)
        }
    """.trimIndent())
  }

  fun `test raw inherit from newly generified`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_6)
    myFixture.addClass("""
      package javax.swing;
      
      public class AbstractListModel<K> {}
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
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
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import javax.swing.AbstractListModel
      
      abstract class AbstractCCM<T> : <error descr="Usage of generified after 1.6 API which would cause compilation problems with JDK 6">AbstractListModel</error><T>() { }
    """.trimIndent())
  }

  fun `test no highlighting in kdoc`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_1_7)
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Javadoc {
        /**
         * [java.util.function.Predicate]
         */
        fun test() {
          return
        }
      }
    """.trimIndent())
  }
}
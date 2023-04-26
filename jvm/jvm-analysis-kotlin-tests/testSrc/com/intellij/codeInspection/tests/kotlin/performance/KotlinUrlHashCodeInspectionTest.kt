package com.intellij.codeInspection.tests.kotlin.performance

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.performance.UrlHashCodeInspectionTestBase

class KotlinUrlHashCodeInspectionTest : UrlHashCodeInspectionTestBase() {
  fun `test url hashcode call`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.net.URL
      
      class UrlHashCode {
          fun foo() {
              val url = URL("")
              url.<warning descr="Call to 'hashCode()' on URL object">hashCode</warning>()
          }
      }
    """.trimIndent())
  }

  fun `test url equals call`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.net.URL
      
      class UrlHashCodeEquals {
          fun foo() {
              val url1 = URL("")
              val url2 = URL("")
              url1 <warning descr="Call to 'equals()' on URL object">==</warning> url2
              url1.<warning descr="Call to 'equals()' on URL object">equals</warning>(url2)
          }
      }
    """.trimIndent())
  }

  fun `test url variable with URL maps or sets`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.net.URL
      import java.util.*

      class CollectionContainsUrl {
          val strMap: Map<String, String> = HashMap()
          val <warning descr="'urlMap' may contain URL objects">urlMap</warning>: Map<URL, String> = HashMap()
          val <warning descr="'urlMapOfMap' may contain URL objects">urlMapOfMap</warning>: Map<String, Map<URL, String>> = HashMap()
          val <warning descr="'urlSet' may contain URL objects">urlSet</warning>: Set<URL> = HashSet()
          val <warning descr="'urlSetOfMap' may contain URL objects">urlSetOfMap</warning>: Set<Map<URL, String>> = HashSet()
      }
    """.trimIndent())
  }

  fun `test url URL map operations`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.net.URL
      import java.util.*

      class CollectionContainsUrl {
          val objMap: MutableMap<Any, Any> = HashMap()
          
          fun foo() {
              <warning descr="'objMap' may contain URL objects">objMap</warning>.put(URL(""), "")
          }
      }
    """.trimIndent())
  }

  fun `test url URL set operations`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.net.URL
      import java.util.*

      class CollectionContainsUrl {
          val objSet: MutableSet<Any> = HashSet()
          
          fun foo() {
              <warning descr="'objSet' may contain URL objects">objSet</warning>.add(URL(""))
          }
      }
    """.trimIndent())
  }
}
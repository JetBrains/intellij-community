package com.intellij.codeInspection.tests.java.performance

import com.intellij.jvm.analysis.internal.testFramework.performance.UrlHashCodeInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaUrlHashCodeInspectionTest : UrlHashCodeInspectionTestBase() {
  fun `test url hashcode call`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.net.URL;
      
      class UrlHashCode {
          void foo() throws java.net.MalformedURLException {
              URL url = new URL("");
              url.<warning descr="Call to 'hashCode()' on URL object">hashCode</warning>();
          }
      }
    """.trimIndent())
  }

  fun `test url equals call`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.net.URL;
      
      class UrlHashCodeEquals {
          void foo() throws java.net.MalformedURLException {
              URL url1 = new URL("");
              URL url2 = new URL("");
              url1.<warning descr="Call to 'equals()' on URL object">equals</warning>(url2);
          }
      }
    """.trimIndent())
  }

  fun `test url variable with URL maps or sets`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.net.URL;
      import java.util.*;

      class CollectionContainsUrl {
          static final Map<String, String> strMap = new HashMap<String, String>();
          static final Map<URL, String> <warning descr="'urlMap' may contain URL objects">urlMap</warning> = new HashMap<URL, String>();
          static final Map<String, Map<URL, String>> <warning descr="'urlMapOfMap' may contain URL objects">urlMapOfMap</warning> = new HashMap<String, Map<URL, String>>();
          static final Set<URL> <warning descr="'urlSet' may contain URL objects">urlSet</warning> = new HashSet<URL>();
          static final Set<Map<URL, String>> <warning descr="'urlSetOfMap' may contain URL objects">urlSetOfMap</warning> = new HashSet<Map<URL, String>>();
      }
    """.trimIndent())
  }

  fun `test url URL map operations`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.net.URL;
      import java.util.*;

      class CollectionContainsUrl {
          static final Map<Object, Object> objMap = new HashMap<Object, Object>();
          
          static {
              try {
                <warning descr="'objMap' may contain URL objects">objMap</warning>.put(new URL(""), "");
              } catch (java.net.MalformedURLException e) {
                e.printStackTrace();
              }
          }
      }
    """.trimIndent())
  }

  fun `test url URL set operations`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.net.URL;
      import java.util.*;

      class CollectionContainsUrl {
          static final Set<Object> objSet = new HashSet<Object>();
          
          static {
              try {
                <warning descr="'objSet' may contain URL objects">objSet</warning>.add(new URL(""));
              } catch (java.net.MalformedURLException e) {
                e.printStackTrace();
              }
          }
      }
    """.trimIndent())
  }

  fun `test URL doesn't highlight when comparing with null`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.net.URL;
      
      class Foo {
          static {
              try {
                  var url = new URL("");
                  if (url.equals(null)) {
                  }
              } catch (Exception e) {}
          }
      }
    """.trimIndent())
  }
}
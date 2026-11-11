// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.JavaImplicitTypeDeclarativeInlayHintsProvider
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestUtil.doTestProvider
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

@TestApplication
class JavaImplicitTypeInlayProviderTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_11)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun `test implicit int not shown`() {
    val text = """
class Demo {
  private static void main() {
    var x = 12;
  }
}"""
    testAnnotations(text)
  }

  @Test
  fun `test call result`() {
    val text = """
class Demo {
  static String foo() {}
  private static void pure(int x, int y) {
    var x/*<# : |[java.lang.String:java.fqn.class]String #>*/ = foo();
  }
}"""
    testAnnotations(text)
  }

  @Test
  fun `test cast`() {
    val text = """
class Demo {
  private static void pure(Object object) {
    var x = (String)object;
  }
}"""
    testAnnotations(text)
  }

  @Test
  fun `test unknown type`() {
    val text = """
class Demo {
  private static void pure(Object object) {
    var x = unresolved;
  }
}"""
    testAnnotations(text)
  }

  @Test
  fun `test long`() {
    val text = """
class Demo {
  class GenericLongClass<T1, T2> {}

  private static void pure(GenericLongClass<Integer, GenericLongClass<String, Integer>> object) {
        var x/*<# : |[Demo.GenericLongClass:java.fqn.class]GenericLongClass|<|[java.lang.Integer:java.fqn.class]Integer|, |[Demo.GenericLongClass:java.fqn.class]GenericLongClass|<...>|> #>*/ = object;
  }
}"""
    testAnnotations(text)
  }

  @Test
  fun `test sdk name`() {
    val text = """
import java.util.HashMap;

class Demo {
  private static void main() {
    var map = new HashMap<String, Integer>();
    var l/*<# : |[java.util.HashMap:java.fqn.class]HashMap|<|[java.lang.String:java.fqn.class]String|, |[java.lang.Integer:java.fqn.class]Integer|> #>*/ = map;
  }
}"""
    testAnnotations(text)
  }

  private fun testAnnotations(@Language("Java") text: String) {
    doTestProvider(myFixture, "A.java", text, JavaImplicitTypeDeclarativeInlayHintsProvider())
  }
}
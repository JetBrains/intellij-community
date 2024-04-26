// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.JavaImplicitTypeDeclarativeInlayHintsProvider
import com.intellij.lang.java.JavaLanguage
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import org.intellij.lang.annotations.Language

class JavaImplicitTypeInlayProviderTest : DeclarativeInlayHintsProviderTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = LightJavaCodeInsightFixtureTestCase.JAVA_11


  fun `test implicit int not shown`() {
    val text = """
class Demo {
  private static void main() {
    var x = 12;
  }
}"""
    testAnnotations(text)
  }

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

  fun `test cast`() {
    val text = """
class Demo {
  private static void pure(Object object) {
    var x = (String)object;
  }
}"""
    testAnnotations(text)
  }

  fun `test unknown type`() {
    val text = """
class Demo {
  private static void pure(Object object) {
    var x = unresolved;
  }
}"""
    testAnnotations(text)
  }

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

  fun testPreview() {
    doTestPreview("""
class ImplicitType {
  void test() {
    var x/*<# : |[ImplicitType:java.fqn.class]ImplicitType #>*//*<# : ImplicitType #>*/ = someMethod();
  }

  ImplicitType someMethod() {
    return null;
  }
}
    """.trimIndent(), JavaImplicitTypeDeclarativeInlayHintsProvider.PROVIDER_ID, JavaImplicitTypeDeclarativeInlayHintsProvider(), JavaLanguage.INSTANCE)
  }


  private fun testAnnotations(@Language("Java") text: String) {
    doTestProvider("A.java", text, JavaImplicitTypeDeclarativeInlayHintsProvider())
  }
}
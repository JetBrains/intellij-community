// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.ImplicitTypeInlayProvider
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_11
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase

class ImplicitTypeInlayProviderTest : InlayHintsProviderTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_11

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
    var x<# [:  [jar://rt.jar!/java/lang/String.class:744]String] #> = foo();
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
    var x<# [:  [[[temp:///src/test.java:1]Demo . [temp:///src/test.java:16]GenericLongClass] [< [[jar://rt.jar!/java/lang/Integer.class:229]Integer ,  [[[temp:///src/test.java:1]Demo . [temp:///src/test.java:16]GenericLongClass] [< ... >]]] >]]] #> = object;
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
    var l<# [:  [[jar://rt.jar!/java/util/HashMap.class:555]HashMap [< [[jar://rt.jar!/java/lang/String.class:744]String ,  [jar://rt.jar!/java/lang/Integer.class:229]Integer] >]]] #> = map;
  }
}"""
    testAnnotations(text)
  }

  private fun testAnnotations(
    text: String
  ) {
    testProvider(
      "test.java",
      text,
      ImplicitTypeInlayProvider(),
      NoSettings()
    )
  }
}
/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor

/**
 * @author Pavel.Dolgov
 */
class JavaReflectionParametersCompletionTest : LightFixtureCompletionTestCase() {
  override fun getBasePath(): String = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/reflectionParameters/"

  override fun getProjectDescriptor(): LightProjectDescriptor = com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase.JAVA_8

  fun testAnnotation() = doTest(0, "Bar.class", "Foo.class")

  fun testInheritedAnnotation() = doTest(1, "Bar.class", "Foo.class")

  fun testDeclaredAnnotation() = doTest(0, "Foo.class", "Bar.class")

  fun testInheritedDeclaredAnnotation() = doTest(1, "Foo.class", "Bar.class")

  fun testAnnotationsByType() = doTest(0, "Bar.class", "Foo.class")

  fun testDeclaredAnnotationsByType() = doTest(0, "Foo.class", "Bar.class")

  fun testConstructor() {
    addConstructors()
    doTest(3, "Construct()", "Construct(int n)", "Construct(java.lang.String s)", "Construct(int n,java.lang.String s)")
  }

  fun testDeclaredConstructor() {
    addConstructors()
    doTest(0, "Construct()", "Construct(int n,java.lang.String s)", "Construct(int n)", "Construct(java.lang.String s)")
  }

  fun testImports() {
    addMoreClasses()
    doTest(1, "Bar.class", "Baz.class")
  }

  fun testVariable() {
    addMoreClasses()
    doTest(2, "Bar.class", "Foo.class", "aType")
  }

  private fun doTest(index: Int, vararg expected: String) {
    addClasses()
    configureByFile(getTestName(false) + ".java")

    val lookupItems = lookup.items
    val texts = lookupFirstItemsTexts(lookupItems, expected.size)
    assertOrderedEquals(texts, *expected)
    selectItem(lookupItems[index])
    myFixture.checkResultByFile(getTestName(false) + "_after.java")
  }

  private fun addClasses() {
    myFixture.addClass("package foo.bar; public @interface Foo {}")
    myFixture.addClass("package foo.bar; public @interface Bar {}")
    myFixture.addClass("package foo.bar; @Bar class Parent {}")
    myFixture.addClass("package foo.bar; @Foo class Test extends Parent {}")
  }

  private fun addMoreClasses() {
    myFixture.addClass("package foo.baz; public @interface Baz {}")
    myFixture.addClass("package foo.bar; @foo.baz.Baz class More extends Parent {}")
  }

  private fun addConstructors() {
    myFixture.addClass("""package foo.bar;
public class Construct {
  public Construct(int n, String s) {}
  Construct(int n) {}
  Construct(String s) {}
  public Construct() {}
}""")
  }

}
fun lookupFirstItemsTexts(lookupItems: List<LookupElement?>, maxSize: Int): List<String> =
  lookupItems.subList(0, Math.min(lookupItems.size, maxSize)).map {
    val obj = it?.`object`
    when (obj) {
      is PsiMethod -> {
        obj.name + obj.parameterList.parameters.map { it.type.canonicalText + " " + it.name }
          .joinToString(",", prefix = "(", postfix = ")")
      }
      else -> {
        val presentation = LookupElementPresentation()
        it?.renderElement(presentation)
        (presentation.itemText ?: "") + (presentation.tailText ?: "")
      }
    }
  }

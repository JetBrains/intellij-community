// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.


package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.indexing.DumbModeAccessType
import kotlin.math.min

@NeedsIndex.Full
class JavaReflectionParametersCompletionTest : LightFixtureCompletionTestCase() {
  override fun getBasePath(): String = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/reflectionParameters/"

  override fun getProjectDescriptor(): LightProjectDescriptor = LightJavaCodeInsightFixtureTestCase.JAVA_8

  fun testAnnotation() = doTest(0, "Bar.class", "Foo.class")

  fun testInheritedAnnotation() = doTest(1, "Bar.class", "Foo.class")

  fun testDeclaredAnnotation() = doTest(0, "Foo.class", "Bar.class")

  fun testInheritedDeclaredAnnotation() = doTest(1, "Foo.class", "Bar.class")

  fun testAnnotationsByType() = doTest(0, "Bar.class", "Foo.class")

  fun testDeclaredAnnotationsByType() = doTest(0, "Foo.class", "Bar.class")

  @NeedsIndex.SmartMode(reason = "Ordering requires smart mode")
  fun testConstructor() {
    addConstructors()
    doTest(1, "Construct()", "Construct(int n,java.lang.String s)", "Construct(int n)", "Construct(java.lang.String s)")
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
  // PsiJavaCodeReferenceElementImpl.getCanonicalText needs resolve()
  // see JavaReflectionParametersCompletionTest.testConstructor and JavaReflectionParametersCompletionTest.testDeclaredConstructor
  DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable<List<String>, RuntimeException> {
    lookupItems.subList(0, min(lookupItems.size, maxSize)).map {
      when (val obj = it?.`object`) {
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
  })

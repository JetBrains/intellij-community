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
package com.intellij.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.LightProjectDescriptor

/**
 * @author Pavel.Dolgov
 */
class JavaReflectionParametersCompletionTest : LightFixtureCompletionTestCase() {
  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/reflectionParameters/"

  override fun getProjectDescriptor(): LightProjectDescriptor = com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase.JAVA_8

  fun testAnnotation() = doTest(0, "Bar.class", "Foo.class", "aType")

  fun testInheritedAnnotation() = doTest(1, "Bar.class", "Foo.class", "aType")

  fun testDeclaredAnnotation() = doTest(0, "Bar.class", "Foo.class")

  fun testInheritedDeclaredAnnotation() = doTest(1, "Bar.class", "Foo.class")

  fun testAnnotationsByType() = doTest(0, "Bar.class", "Foo.class")

  fun testDeclaredAnnotationsByType() = doTest(1, "Bar.class", "Foo.class")

  fun testConstructor() {
    addConstructors()
    doTest(2, "Construct()", "Construct(int)", "Construct(int,java.lang.String)", "Construct(java.lang.String)")
  }

  fun testDeclaredConstructor() {
    addConstructors()
    doTest(0, "Construct()", "Construct(int)", "Construct(int,java.lang.String)", "Construct(java.lang.String)")
  }

  private fun doTest(index: Int, vararg expected: String) {
    addClasses()
    configureByFile(getTestName(false) + ".java")

    val lookupItems = lookup.items
    val texts = lookupItems.subList(0, Math.min(lookupItems.size, expected.size)).map {
      val presentation = LookupElementPresentation()
      it?.renderElement(presentation)
      presentation.itemText ?: ""
    }
    assertOrderedEquals(texts, *expected)
    selectItem(lookupItems[index])
    myFixture.checkResultByFile(getTestName(false) + "_after.java")
  }

  private fun addClasses() {
    myFixture.addClass("package foo.bar; public @interface Foo {}")
    myFixture.addClass("package foo.bar; public @interface Bar {}")
    myFixture.addClass("package foo.bar; public @interface Baz {}")
    myFixture.addClass("package foo.bar; @Foo class Parent {}")
    myFixture.addClass("package foo.bar; @Bar class Test extends Parent {}")
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

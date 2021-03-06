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
import com.intellij.testFramework.NeedsIndex

/**
 * @author Pavel.Dolgov
 */
@NeedsIndex.ForStandardLibrary
class JavaReflectionCompletionOverloadTest : LightFixtureCompletionTestCase() {

  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/reflectionOverload/"

  fun testOverloadMethods() = doTest(2,
                                     "method()", "method(C)", "method(A, B)",
                                     "equals(Object)")

  fun testJavaLangObjectMethods() = doTest(5,
                                           "method()",
                                           "equals(Object)", "getClass()", "hashCode()",
                                           "notify()", "notifyAll()", "toString()",
                                           "wait()", "wait(long)", "wait(long, int)"
  )

  fun testJavaLangObjectOwnMethods() = doTest(10,
                                              "clone()", "equals(Object)",
                                              "finalize()", "getClass()", "hashCode()",
                                              "notify()", "notifyAll()", "registerNatives()", "toString()",
                                              "wait()", "wait(long)", "wait(long, int)")

  fun testOverriddenMethod() = doTest(2,
                                      "gpMethod(A, B)", "method()", "pMethod(C)",
                                      "equals(Object)")

  fun testShadowedMethod() = doTest(0,
                                    "shadowed()",
                                    "equals(Object)")

  fun testOverloadedMethod() = doTest(1,
                                      "overloaded()", "overloaded(int)",
                                      "equals(Object)")

  fun testOverloadedMethodPrefix() = doTest(2,
                                            "foo()", "foo(int)", "foo(String)")

  fun testOverloadedInheritedMethod() = doTest(1,
                                               "overloaded(int)", "overloaded(String)",
                                               "equals(Object)")


  private fun doTest(index: Int, vararg expected: String) {
    configureByFile(getTestName(false) + ".java")

    val lookupItems = lookup.items
    val texts = lookupFirstItemsTexts(lookupItems, expected.size)
    assertOrderedEquals(texts, *expected)
    if (index >= 0) selectItem(lookupItems[index])
    myFixture.checkResultByFile(getTestName(false) + "_after.java")
  }
}

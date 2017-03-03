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

/**
 * @author Pavel.Dolgov
 */
class JavaReflectionCompletionOverloadTest : LightFixtureCompletionTestCase() {

  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/reflectionOverload/"

  fun testOverloadMethods() = doTest(2,
                                     "method()", "method(C c)", "method(A a,B b)",
                                     "equals(java.lang.Object obj)")

  fun testJavaLangObjectMethods() = doTest(6,
                                           "method()",
                                           "equals(java.lang.Object obj)", "hashCode()", "toString()",
                                           "getClass()", "notify()", "notifyAll()",
                                           "wait()", "wait(long timeout)", "wait(long timeout,int nanos)"
  )

  fun testJavaLangObjectOwnMethods() = doTest(9,
                                              "clone()", "equals(java.lang.Object obj)", "hashCode()",
                                              "toString()", "finalize()", "getClass()",
                                              "notify()", "notifyAll()",
                                              "wait()", "wait(long timeout)", "wait(long timeout,int nanos)")

  fun testOverriddenMethod() = doTest(2,
                                      "gpMethod(A a,B b)", "method()", "pMethod(C c)",
                                      "equals(java.lang.Object obj)")

  fun testShadowedMethod() = doTest(0,
                                    "shadowed()",
                                    "equals(java.lang.Object obj)")

  fun testOverloadedMethod() = doTest(1,
                                      "overloaded()", "overloaded(int n)",
                                      "equals(java.lang.Object obj)")

  fun testOverloadedInheritedMethod() = doTest(1,
                                               "overloaded(int n)", "overloaded(java.lang.String s)",
                                               "equals(java.lang.Object obj)")


  private fun doTest(index: Int, vararg expected: String) {
    configureByFile(getTestName(false) + ".java")

    val lookupItems = lookup.items
    val texts = lookupFirstItemsTexts(lookupItems, expected.size)
    assertOrderedEquals(texts, *expected)
    if (index >= 0) selectItem(lookupItems[index])
    myFixture.checkResultByFile(getTestName(false) + "_after.java")
  }
}

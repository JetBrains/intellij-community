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
import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.openapi.application.EDT
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test

@NeedsIndex.ForStandardLibrary
@TestApplication
@IndexingModeTemplate
class JavaReflectionCompletionOverloadTest {
  companion object {
    private val projectFixture = lightCompletionProjectFixture()
  }

  private val completion by lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/reflectionOverload/")
  private val myFixture get() = completion.fixture

  @Test
  fun testOverloadMethods(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(2,
                                     "method()", "method(C)", "method(A, B)",
                                     "equals(Object)") }

  @Test
  fun testJavaLangObjectMethods(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(5,
                                           "method()",
                                           "equals(Object)", "getClass()", "hashCode()",
                                           "notify()", "notifyAll()", "toString()",
                                           "wait()", "wait(long)", "wait(long, int)"
  ) }

  @Test
  fun testJavaLangObjectOwnMethods(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(10,
                                              "clone()", "equals(Object)",
                                              "finalize()", "getClass()", "hashCode()",
                                              "notify()", "notifyAll()", "registerNatives()", "toString()",
                                              "wait()", "wait(long)", "wait(long, int)") }

  @Test
  fun testOverriddenMethod(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(2,
                                      "gpMethod(A, B)", "method()", "pMethod(C)",
                                      "equals(Object)") }

  @Test
  fun testShadowedMethod(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(0,
                                    "shadowed()",
                                    "equals(Object)") }

  @Test
  fun testOverloadedMethod(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(1,
                                      "overloaded()", "overloaded(int)",
                                      "equals(Object)") }

  @Test
  fun testOverloadedMethodPrefix(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(2,
                                            "foo()", "foo(int)", "foo(String)") }

  @Test
  fun testOverloadedInheritedMethod(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) { doTest(1,
                                               "overloaded(int)", "overloaded(String)",
                                               "equals(Object)") }

  private fun doTest(index: Int, vararg expected: String) {
    completion.configureByFile(myFixture.getTestName(false) + ".java")

    val lookupItems = completion.getLookup()!!.items
    val texts = lookupFirstItemsTexts(lookupItems, expected.size)
    assertIterableEquals(expected.toList(), texts)
    if (index >= 0) completion.selectItem(lookupItems[index])
    myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java")
  }
}

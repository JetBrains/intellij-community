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
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.reflectiveAccess.JavaLangInvokeHandleSignatureInspection
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */

class JavaLangReflectHandleInvocationTest : JavaLangReflectHandleInvocationTestBase(LanguageLevel.JDK_1_7,
                                                                                    LightCodeInsightFixtureTestCase.JAVA_8) {
  fun testVirtual() = doTest()
  fun testStatic() = doTest()
  fun testConstructor() = doTest()

  fun testGetter() = doTest()
  fun testSetter() = doTest()

  fun testStaticGetter() = doTest()
  fun testStaticSetter() = doTest()
}

class Java9LangReflectHandleInvocationTest : JavaLangReflectHandleInvocationTestBase(LanguageLevel.JDK_1_9,
                                                                                     LightCodeInsightFixtureTestCase.JAVA_9) {
  fun testVarHandle() = doTest()
  fun testStaticVarHandle() = doTest()
  fun testArrayVarHandle() = doTest()
}

abstract class JavaLangReflectHandleInvocationTestBase(val languageLevel: LanguageLevel,
                                                       val descriptor: LightProjectDescriptor) : LightCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    LanguageLevelProjectExtension.getInstance(project).languageLevel = languageLevel
    myFixture.enableInspections(JavaLangInvokeHandleSignatureInspection())
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = descriptor

  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/javaLangReflectHandleInvocation"

  protected fun doTest() {
    myFixture.testHighlighting("${getTestName(false)}.java")
  }
}

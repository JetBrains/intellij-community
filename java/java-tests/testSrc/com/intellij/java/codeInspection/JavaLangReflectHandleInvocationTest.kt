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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class JavaLangReflectHandleInvocationTest : LightJavaCodeInsightFixtureTestCase() {
  fun testVirtual() = doTest()
  fun testStatic() = doTest()
  fun testConstructor() = doTest()

  fun testGetter() = doTest()
  fun testSetter() = doTest()

  fun testStaticGetter() = doTest()
  fun testStaticSetter() = doTest()

  fun testVarHandle() = doTest()
  fun testStaticVarHandle() = doTest()
  fun testArrayVarHandle() = doTest()

  override fun setUp() {
    super.setUp()
    LanguageLevelProjectExtension.getInstance(project).languageLevel = LanguageLevel.JDK_1_9
    myFixture.enableInspections(JavaLangInvokeHandleSignatureInspection())
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_9

  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/javaLangReflectHandleInvocation"

  private fun doTest() {
    myFixture.testHighlighting("${getTestName(false)}.java")
  }
}

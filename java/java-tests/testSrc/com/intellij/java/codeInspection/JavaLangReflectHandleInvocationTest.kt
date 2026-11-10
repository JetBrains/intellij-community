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
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_9
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JavaLangReflectHandleInvocationTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_9)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/inspection/javaLangReflectHandleInvocation"
  }

  @Test
  fun testVirtual(): Unit = doTest()
  @Test
  fun testStatic(): Unit = doTest()
  @Test
  fun testConstructor(): Unit = doTest()

  @Test
  fun testGetter(): Unit = doTest()
  @Test
  fun testSetter(): Unit = doTest()

  @Test
  fun testStaticGetter(): Unit = doTest()
  @Test
  fun testStaticSetter(): Unit = doTest()

  @Test
  fun testVarHandle(): Unit = doTest()
  @Test
  fun testStaticVarHandle(): Unit = doTest()
  @Test
  fun testArrayVarHandle(): Unit = doTest()

  @BeforeEach
  fun setUp() {
    IdeaTestUtil.setProjectLanguageLevel(myFixture.project, LanguageLevel.JDK_1_9)
    myFixture.enableInspections(JavaLangInvokeHandleSignatureInspection())
  }

  private fun doTest() {
    myFixture.testHighlighting("${myFixture.getTestName(false)}.java")
  }
}

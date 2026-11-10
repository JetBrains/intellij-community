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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_9
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JavaLangInvokeHandleSignatureTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_9)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = "${JavaTestUtil.getJavaTestDataPath()}/inspection/invokeHandleSignature"
  }

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(JavaLangInvokeHandleSignatureInspection())
  }

  @Test
  fun testGenericMethod(): Unit = doTest()

  @Test
  fun testOverloadedMethod(): Unit = doTest()

  @Test
  fun testStaticMethod(): Unit = doTest()

  @Test
  fun testConstructor(): Unit = doTest()

  @Test
  fun testVarHandle(): Unit = doTest()

  @Test
  fun testGetter(): Unit = doTest()

  @Test
  fun testSetter(): Unit = doTest()

  @Test
  fun testSpecial(): Unit = doTest()

  @Test
  fun testVarArgMethodHandle(): Unit = doTest()

  @Test
  fun testSignaturePolymorphic(): Unit = doTest()

  private fun doTest() {
    myFixture.testHighlighting("${myFixture.getTestName(false)}.java")
  }
}

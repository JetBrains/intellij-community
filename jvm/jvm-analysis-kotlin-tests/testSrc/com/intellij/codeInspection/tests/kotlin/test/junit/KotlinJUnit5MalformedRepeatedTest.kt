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
package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnit5MalformedRepeatedTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import java.io.File

class KotlinJUnit5MalformedRepeatedTest : JUnit5MalformedRepeatedTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(sdkLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val jar = File(PathUtil.getJarPathForClass(JvmStatic::class.java))
      PsiTestUtil.addLibrary(model, "kotlin-stdlib", jar.parent, jar.name)
    }
  }

  fun testMalformed() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.api.*
      
      class WithRepeatedInfoAndTest {
        @BeforeEach
        fun beforeEach(<warning descr="RepetitionInfo won't be injected for @Test methods">repetitionInfo</warning>: RepetitionInfo) { }

        @Test
        fun nonRepeated(<warning descr="RepetitionInfo is injected for @RepeatedTest only">repetitionInfo</warning>: RepetitionInfo) { }
      }

      object WithRepeated {
        @RepeatedTest(1)
        fun repeatedTestNoParams() { }

        @RepeatedTest(1)
        fun repeatedTestWithRepetitionInfo(repetitionInfo: RepetitionInfo) { }  

        @BeforeAll
        @JvmStatic
        fun beforeAllWithRepetitionInfo(<warning descr="RepetitionInfo is injected for @BeforeEach/@AfterEach only, but not for BeforeAll">repetitionInfo</warning>: RepetitionInfo) { }

        @BeforeEach
        fun config(repetitionInfo: RepetitionInfo) { }
      }

      class WithRepeatedAndTests {
        <warning descr="Suspicious combination @Test and @RepeatedTest">@Test</warning>
        @RepeatedTest(1)
        fun repeatedTestAndTest() { }
      }

      class WithParameterized {
        @ParameterizedTest
        fun testAccidentalRepetitionInfo(s: Any, <warning descr="RepetitionInfo is injected for @RepeatedTest only">repetitionInfo</warning>: RepetitionInfo) { }
      }
      
      class WithRepeatedAndCustomNames {
        @RepeatedTest(value = 1, name = "{displayName} {currentRepetition}/{totalRepetitions}")
        fun repeatedTestWithCustomName() { }
      }

      class WithRepeatedAndTestInfo {
        @BeforeEach
        fun beforeEach(testInfo: TestInfo, repetitionInfo: RepetitionInfo) {}

        @RepeatedTest(1)
        fun repeatedTestWithTestInfo(testInfo: TestInfo) { }

        @AfterEach
        fun afterEach(testInfo: TestInfo, repetitionInfo: RepetitionInfo) {}
      }

      class WithRepeatedAndTestReporter {
        @BeforeEach
        fun beforeEach(testReporter: TestReporter, repetitionInfo: RepetitionInfo) {}

        @RepeatedTest(1)
        fun repeatedTestWithTestInfo(testReporter: TestReporter) { }

        @AfterEach
        fun afterEach(testReporter: TestReporter, repetitionInfo: RepetitionInfo) {}
      }
    """.trimIndent())
  }

  fun testPositiveRepetitions() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.jupiter.api.*

      class WithRepeated {
        @RepeatedTest(<warning descr="The number of repetitions must be greater than zero">-1</warning>)
        fun repeatedTestNegative() { }

        @RepeatedTest(<warning descr="The number of repetitions must be greater than zero">0</warning>)
        fun repeatedTestBoundaryZero() { }
      }
    """.trimIndent())
  }
}
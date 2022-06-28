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
package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnit5MalformedRepeatedTestBase

class JavaJUnit5MalformedRepeatedTest : JUnit5MalformedRepeatedTestBase() {
  fun testMalformed() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.api.*;
      
      class WithRepeatedInfoAndTest {
        @BeforeEach
        void beforeEach(RepetitionInfo <warning descr="RepetitionInfo won't be injected for @Test methods">repetitionInfo</warning>) { }

        @Test
        void nonRepeated(RepetitionInfo <warning descr="RepetitionInfo is injected for @RepeatedTest only">repetitionInfo</warning>) { }
      }

      class WithRepeated {
        @RepeatedTest(1)
        void repeatedTestNoParams() { }

        @RepeatedTest(1)
        void repeatedTestWithRepetitionInfo(RepetitionInfo repetitionInfo) { }

        @BeforeAll
        void beforeAllWithRepetitionInfo(RepetitionInfo <warning descr="RepetitionInfo is injected for @BeforeEach/@AfterEach only, but not for BeforeAll">repetitionInfo</warning>) { }

        @BeforeEach
        void config(RepetitionInfo repetitionInfo) { }
      }

      class WithRepeatedAndTests {
        <warning descr="Suspicious combination @Test and @RepeatedTest">@Test</warning>
        @RepeatedTest(1)
        void repeatedTestAndTest() { }
      }

      class WithParameterized {
        @ParameterizedTest
        void testaccidentalRepetitionInfo(Object s, RepetitionInfo <warning descr="RepetitionInfo is injected for @RepeatedTest only">repetitionInfo</warning>) { }
      }
      class WithRepeatedAndCustomNames {
        @RepeatedTest(value = 1, name = "{displayName} {currentRepetition}/{totalRepetitions}")
        void repeatedTestWithCustomName() { }
      }

      class WithRepeatedAndTestInfo {
        @BeforeEach
        void beforeEach(TestInfo testInfo, RepetitionInfo repetitionInfo) {}

        @RepeatedTest(1)
        void repeatedTestWithTestInfo(TestInfo testInfo) { }

        @AfterEach
        void afterEach(TestInfo testInfo, RepetitionInfo repetitionInfo) {}
      }

      class WithRepeatedAndTestReporter {
        @BeforeEach
        void beforeEach(TestReporter testReporter, RepetitionInfo repetitionInfo) {}

        @RepeatedTest(1)
        void repeatedTestWithTestInfo(TestReporter testReporter) { }

        @AfterEach
        void afterEach(TestReporter testReporter, RepetitionInfo repetitionInfo) {}
      }
    """.trimIndent())
  }

  fun testPositiveRepetitions() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.jupiter.api.*;

      class WithRepeated {
        @RepeatedTest(<warning descr="The number of repetitions must be greater than zero">-1</warning>)
        void repeatedTestNegative() { }

        @RepeatedTest(<warning descr="The number of repetitions must be greater than zero">0</warning>)
        void repeatedTestBoundaryZero() { }
      }
    """.trimIndent())
  }
}
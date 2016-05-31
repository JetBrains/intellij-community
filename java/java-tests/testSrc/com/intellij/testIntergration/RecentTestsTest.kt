/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.testIntergration

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.testIntegration.RecentTestsData
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.*

fun String.suite() = "java:suite://$this"
fun String.test() = "java:test://$this"

class RecentTestsStepTest: LightIdeaTestCase() {

  lateinit var data: RecentTestsData
  lateinit var allTests: RunnerAndConfigurationSettings
  lateinit var now: Date

  override fun setUp() {
    super.setUp()
    data = RecentTestsData()
    allTests = mock(RunnerAndConfigurationSettings::class.java)
    `when`(allTests.uniqueID).thenAnswer { "JUnit.all tests" }
    `when`(allTests.name).thenAnswer { "all tests" }
    now = Date()
  }
  
  fun `test show suites without run configuration`() {
    data.addTest("Test.x".test(), TestStateInfo.Magnitude.PASSED_INDEX, now, null)
    data.addSuite("Test".suite(), TestStateInfo.Magnitude.PASSED_INDEX, now, null)
    
    val tests = data.getTestsToShow()
    assertThat(tests).hasSize(1)
  }

  fun `test all tests passed`() {
    data.addTest("java:test://Test.textXXX", TestStateInfo.Magnitude.PASSED_INDEX, now, allTests)
    data.addSuite("java:suite://Test", TestStateInfo.Magnitude.PASSED_INDEX, now, allTests)
    data.addSuite("java:suite://JavaFormatterSuperDuperTest", TestStateInfo.Magnitude.PASSED_INDEX, now, allTests)
    data.addTest("java:test://Test.textYYY", TestStateInfo.Magnitude.PASSED_INDEX, now, allTests)
    data.addTest("java:test://JavaFormatterSuperDuperTest.testItMakesMeSadToFixIt", TestStateInfo.Magnitude.PASSED_INDEX, now, allTests)
    data.addTest("java:test://Test.textZZZ", TestStateInfo.Magnitude.PASSED_INDEX, now, allTests)
    data.addTest("java:test://Test.textQQQ", TestStateInfo.Magnitude.PASSED_INDEX, now, allTests)
    data.addTest("java:test://JavaFormatterSuperDuperTest.testUnconditionalAlignmentErrorneous", TestStateInfo.Magnitude.PASSED_INDEX, now, allTests)

    val tests = data.getTestsToShow()
    assertThat(tests).hasSize(1)
    assertThat(tests[0].presentation).isEqualTo("all tests")
  }


  fun `test if one failed in run configuration show failed suite`() {
    data.addSuite("java:suite://JavaFormatterSuperDuperTest", TestStateInfo.Magnitude.FAILED_INDEX, now, allTests)
    data.addSuite("java:suite://Test", TestStateInfo.Magnitude.PASSED_INDEX, now, allTests)

    data.addTest("java:test://JavaFormatterSuperDuperTest.testItMakesMeSadToFixIt", TestStateInfo.Magnitude.FAILED_INDEX, now, allTests)
    data.addTest("java:test://JavaFormatterSuperDuperTest.testUnconditionalAlignmentErrorneous", TestStateInfo.Magnitude.PASSED_INDEX, now, allTests)
    
    data.addTest("java:test://Test.textXXX", TestStateInfo.Magnitude.PASSED_INDEX, now, allTests)
    
    val tests = data.getTestsToShow()

    assertThat(tests).hasSize(2)
    assertThat(tests[0].presentation).isEqualTo("JavaFormatterSuperDuperTest")
    assertThat(tests[1].presentation).isEqualTo("all tests")
  }
  
  
  fun `test if configuration with single test show failed test`() {
    data.addSuite("java:suite://JavaFormatterSuperDuperTest", TestStateInfo.Magnitude.FAILED_INDEX, now, allTests)
    data.addTest("java:test://JavaFormatterSuperDuperTest.testItMakesMeSadToFixIt", TestStateInfo.Magnitude.FAILED_INDEX, now, allTests)
    data.addTest("java:test://JavaFormatterSuperDuperTest.testUnconditionalAlignmentErrorneous", TestStateInfo.Magnitude.PASSED_INDEX, now, allTests)
    
    val tests = data.getTestsToShow()
    assertThat(tests).hasSize(2)
    assertThat(tests[0].presentation).isEqualTo("JavaFormatterSuperDuperTest.testItMakesMeSadToFixIt")
    assertThat(tests[1].presentation).isEqualTo("JavaFormatterSuperDuperTest")
  }
  
  
}
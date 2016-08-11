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
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude.FAILED_INDEX
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude.PASSED_INDEX
import com.intellij.testFramework.LightIdeaTestCase
import com.intellij.testIntegration.*
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.*

fun String.suite() = "java:suite://$this"
fun String.test() = "java:test://$this"

fun mockConfiguration(name: String, uniqueID: String): RunnerAndConfigurationSettings {
  val settings = mock(RunnerAndConfigurationSettings::class.java)
  `when`(settings.uniqueID).thenAnswer { uniqueID }
  `when`(settings.name).thenAnswer { name }
  return settings
}

class RecentTestsStepTest: LightIdeaTestCase() {

  lateinit var data: RecentTestsData
  lateinit var allTests: RunnerAndConfigurationSettings
  lateinit var now: Date

  override fun setUp() {
    super.setUp()
    data = RecentTestsData()
    allTests = mockConfiguration("all tests", "JUnit.all tests")
    now = Date()
  }
  
  fun `test all tests passed`() {
    
    data.addTest(SingleTestEntry("Test.textXXX".test(), now, allTests, PASSED_INDEX))
    
    
    data.addSuite(SuiteEntry("Test".suite(), now, allTests))
    
    data.addSuite(SuiteEntry("JFSDTest".suite(), now, allTests))
    
    data.addTest(SingleTestEntry("Test.textYYY".test(), now, allTests, PASSED_INDEX))
    data.addTest(SingleTestEntry("JFSDTest.testItMakesMeSadToFixIt".test(), now, allTests, PASSED_INDEX))
    data.addTest(SingleTestEntry("Test.textZZZ".test(), now, allTests, PASSED_INDEX))
    data.addTest(SingleTestEntry("Test.textQQQ".test(), now, allTests, PASSED_INDEX))
    data.addTest(SingleTestEntry("JFSDTest.testUnconditionalAlignmentErrorneous".test(), now, allTests, PASSED_INDEX))

    val tests = data.getTestsToShow()
    assertThat(tests).hasSize(1)
    assertThat(tests[0].presentation).isEqualTo("all tests")
  }


  fun `test if one failed in run configuration show failed suite`() {
    data.addSuite(SuiteEntry("JFSDTest".suite(), now, allTests))
    data.addSuite(SuiteEntry("Test".suite(), now, allTests))

    data.addTest(SingleTestEntry("JFSDTest.testItMakesMeSadToFixIt".test(), now, allTests, FAILED_INDEX))
    data.addTest(SingleTestEntry("JFSDTest.testUnconditionalAlignmentErrorneous".test(), now, allTests, PASSED_INDEX))
    
    data.addTest(SingleTestEntry("Test.textXXX".test(), now, allTests, PASSED_INDEX))
    
    val tests = data.getTestsToShow()

    assertThat(tests).hasSize(1)
    
    assertThat(tests[0].presentation).isEqualTo("JFSDTest.testItMakesMeSadToFixIt")
    assertThat(tests[0].failed).isEqualTo(true)
  }
  
  
  fun `test if configuration with single test show failed test`() {
    data.addSuite(SuiteEntry("JFSDTest".suite(), now, allTests))
    data.addTest(SingleTestEntry("JFSDTest.testItMakesMeSadToFixIt".test(), now, allTests, FAILED_INDEX))
    data.addTest(SingleTestEntry("JFSDTest.testUnconditionalAlignmentErrorneous".test(), now, allTests, PASSED_INDEX))
    
    val tests = data.getTestsToShow()
    assertThat(tests).hasSize(1)
    assertThat(tests[0].presentation).isEqualTo("JFSDTest.testItMakesMeSadToFixIt")
  }


  fun `test show test without suite`() {
    data.addTest(SingleTestEntry("Test.sssss".test(), now, allTests, FAILED_INDEX))
    val testsToShow = data.getTestsToShow()
    assertThat(testsToShow).hasSize(1)
  }


  fun `test additional entries`() {
    data.addSuite(SuiteEntry("Test2".suite(), now, allTests))
    data.addSuite(SuiteEntry("Test".suite(), now, allTests))
    data.addTest(SingleTestEntry("Test.sss".test(), now, allTests, FAILED_INDEX))

    val tests = data.getTestsToShow()
    assertThat(tests).hasSize(1)

    val failedTest = tests[0]

    val collector = TestConfigurationCollector()
    failedTest.accept(collector)
    val configs = collector.getEnclosingConfigurations()
    
    assertThat(configs).hasSize(2)
    assertThat(configs[0]).isInstanceOf(SuiteEntry::class.java)
    assertThat(configs[1]).isInstanceOf(RunConfigurationEntry::class.java)
  }

  fun `test if configuration consists of single test show only configuration`() {
    data.addSuite(SuiteEntry("Test".suite(), now, allTests))
    data.addTest(SingleTestEntry("Test.sss".test(), now, allTests, FAILED_INDEX))
    val tests = data.getTestsToShow()
    
    assertThat(tests).hasSize(1)
    
    val collector = TestConfigurationCollector()
    tests[0].accept(collector)
    val configs = collector.getEnclosingConfigurations()
    
    assertThat(configs).hasSize(1)
    assertThat(configs[0]).isInstanceOf(RunConfigurationEntry::class.java)
  }

  
  
}
/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.testIntegration.RecentTestsData
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito
import java.util.*

class RecentTestsOrderTest: LightIdeaTestCase() {

  lateinit var data: RecentTestsData
  lateinit var allTests: RunnerAndConfigurationSettings
  lateinit var now: Date
  
  
  override fun setUp() {
    super.setUp()
    data = RecentTestsData()
    allTests = Mockito.mock(RunnerAndConfigurationSettings::class.java)
    Mockito.`when`(allTests.uniqueID).thenAnswer { "JUnit.all tests" }
    Mockito.`when`(allTests.name).thenAnswer { "all tests" }
    now = Date()
  }
  
  fun addPassedSuite(suiteUrl: String, date: Date = Date(), runConfiguration: RunnerAndConfigurationSettings = allTests) {
    data.addSuite(suiteUrl, PASSED_INDEX, date, runConfiguration)
  }
  
  fun addFailedSuite(suiteUrl: String, date: Date = Date(), runConfiguration: RunnerAndConfigurationSettings = allTests) {
    data.addSuite(suiteUrl, FAILED_INDEX, date, runConfiguration)
  }
  
  fun addPassedTest(testUrl: String, date: Date = Date(), runConfiguration: RunnerAndConfigurationSettings = allTests) {
    data.addTest(testUrl, PASSED_INDEX, date, runConfiguration)
  }
  
  fun addFailedTest(testUrl: String, date: Date = Date(), runConfiguration: RunnerAndConfigurationSettings = allTests) {
    data.addTest(testUrl, FAILED_INDEX, date, runConfiguration)
  }
  
  fun `test run configuration with one suite shows only suite`() {
    val suite = "MySingleTest".suite()
    val test1 = "MySingleTest.test1".test()
    
    data.addTest(test1, PASSED_INDEX, now, allTests)
    data.addSuite(suite, PASSED_INDEX, now, allTests)
    
    val testsToShow = data.getTestsToShow()
    assertThat(testsToShow).hasSize(1)
    assertThat(testsToShow[0].presentation).isEqualTo("MySingleTest")
  }
  
  fun `test run configuration with multiple suites shows run configuration name`() {
    val suite1 = "MyFirstTest".suite()
    val test1 = "MyFirstTest.test1".test()
    
    val suite2 = "MySecondTest".suite()
    val test2 = "MySecondTest.test1".test()
    
    addPassedSuite(suite1, now)
    addPassedTest(test1, now)
    
    addPassedSuite(suite2)
    addPassedTest(test2)

    val tests = data.getTestsToShow()
    assertThat(tests).hasSize(1)
    assertThat(tests[0].presentation).isEqualTo("all tests")
  }
  
  fun `test show failed suite in test`() {
    val suite = "SingleTest".suite()
    val test = "SingleTest.test".test()
    
    addPassedSuite(suite)
    addFailedTest(test)
    
    val tests = data.getTestsToShow()
    assertThat(tests).hasSize(2)
    
    assertThat(tests[0].presentation).isEqualTo("SingleTest.test")
    assertThat(tests[0].magnitude).isEqualTo(FAILED_INDEX)
    
    assertThat(tests[1].presentation).isEqualTo("SingleTest")
    assertThat(tests[1].magnitude).isEqualTo(FAILED_INDEX)
  }
  
  fun `test show failed suite and test in run configuration`() {
    val suite = "SingleTest".suite()
    val test = "SingleTest.test".test()
    val test2 = "SingleTest.test2".test()
    
    addPassedSuite(suite)
    addFailedTest(test)
    addPassedTest(test2)
    
    addPassedSuite("PassedSuite".suite())
    
    val tests = data.getTestsToShow()
    assertThat(tests).hasSize(3)
  }
  
  
}
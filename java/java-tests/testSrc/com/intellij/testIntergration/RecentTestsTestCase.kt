/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.testIntergration

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.testIntegration.RecentTestsData
import com.intellij.testIntegration.SingleTestEntry
import com.intellij.testIntegration.SuiteEntry
import org.mockito.Mockito
import java.util.*

abstract class RecentTestsTestCase {
  private val data = RecentTestsData()
  private val allTests: RunnerAndConfigurationSettings = mockConfiguration("all tests", "JUnit.all tests")
  protected val now = Date()

  protected fun mockConfiguration(name: String, uniqueID: String): RunnerAndConfigurationSettings {
    val settings = Mockito.mock(RunnerAndConfigurationSettings::class.java)
    Mockito.`when`(settings.uniqueID).thenAnswer { uniqueID }
    Mockito.`when`(settings.name).thenAnswer { name }
    return settings
  }

  protected fun addSuite(suiteName: String, date: Date, runConfiguration: RunnerAndConfigurationSettings = allTests) =
    data.addSuite(SuiteEntry("java:suite://$suiteName", date, runConfiguration))

  protected fun addPassedTest(testName: String, date: Date, runConfiguration: RunnerAndConfigurationSettings = allTests) =
    data.addTest(SingleTestEntry("java:test://$testName", date, runConfiguration, TestStateInfo.Magnitude.PASSED_INDEX))

  protected fun addFailedTest(testName: String, date: Date, runConfiguration: RunnerAndConfigurationSettings = allTests) =
    data.addTest(SingleTestEntry("java:test://$testName", date, runConfiguration, TestStateInfo.Magnitude.FAILED_INDEX))

  protected fun getTestsToShow() = data.getTestsToShow()
}
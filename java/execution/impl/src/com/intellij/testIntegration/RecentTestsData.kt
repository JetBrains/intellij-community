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
package com.intellij.testIntegration

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.containers.ContainerUtil
import java.util.*

class RecentTestsData {

  private val suitePacks = hashMapOf<String, SuitePackInfo>()
  private var testsWithoutSuites: MutableList<TestInfo> = ContainerUtil.newArrayList<TestInfo>()

  fun addSuite(url: String,
               magnitude: TestStateInfo.Magnitude,
               runDate: Date,
               runConfiguration: RunnerAndConfigurationSettings) 
  {

    val suiteInfo = SuiteInfo(url, magnitude, runDate)

    val suitePack = suitePacks[runConfiguration.uniqueID]
    if (suitePack != null) {
      suitePack.addSuite(suiteInfo)
      return
    }
    
    suitePacks[runConfiguration.uniqueID] = SuitePackInfo(runConfiguration, suiteInfo)
  }

  fun addTest(url: String, magnitude: TestStateInfo.Magnitude, runDate: Date) {
    val testInfo = TestInfo(url, magnitude, runDate)

    val suite = findSuite(url)
    if (suite != null) {
      suite.addTest(testInfo)
      return
    }

    testsWithoutSuites.add(testInfo)
  }

  private fun findSuite(url: String): SuiteInfo? {
    val testName = VirtualFileManager.extractPath(url)

    suitePacks.values.forEach {
      it.suites.forEach {
        if (testName.startsWith(it.suiteName)) {
          return it
        }
      }
    }
    
    return null
  }

  
  fun getTestsToShow(): List<RecentTestsPopupEntry> {
    distributeUnmatchedTests()
    val packsByDate = suitePacks.values.sortedByDescending { it.runDate }
    return packsByDate
  }
  
  private fun suiteToTestList(suite: SuiteInfo): List<RecentTestsPopupEntry> {
    val result = ContainerUtil.newArrayList<RecentTestsPopupEntry>()

    if (suite.canTrustSuiteMagnitude() && suite.isPassed) {
      result.add(suite)
      return result
    }

    val failedTests = suite.failedTests
    //sortTestsByRecent(failedTests)

    if (failedTests.size == suite.totalTestsCount) {
      result.add(suite)
    }
    else if (failedTests.size < 3) {
      result.addAll(failedTests)
      result.add(suite)
    }
    else {
      result.add(suite)
      result.addAll(failedTests)
    }

    return result
  }


  private fun distributeUnmatchedTests() {
    val noSuites = ContainerUtil.newSmartList<TestInfo>()

    for (test in testsWithoutSuites) {
      val url = test.url
      val suite = findSuite(url)
      if (suite != null) {
        suite.addTest(test)
      }
      else {
        noSuites.add(test)
      }
    }

    testsWithoutSuites = noSuites
  }
}
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
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import com.intellij.openapi.vfs.VirtualFileManager
import java.util.*

fun SuiteEntry.isMyTest(test: SingleTestEntry): Boolean {
  val testName = VirtualFileManager.extractPath(test.url)
  return testName.startsWith(this.suiteName)
}

class RecentTestsData {

  private val runConfigurationSuites = hashMapOf<String, RunConfigurationEntry>()

  private var unmatchedRunConfigurationTests: MutableList<SingleTestEntry> = arrayListOf<SingleTestEntry>()

  private val urlSuites = mutableListOf<SuiteEntry>()
  private var unmatchedUrlTests = mutableListOf<SingleTestEntry>()

  fun addUrlSuite(url: String, magnitude: Magnitude, runDate: Date) {
    val suite = SuiteEntry(url, magnitude, runDate)

    unmatchedUrlTests.filter { suite.isMyTest(it) }.forEach { suite.addTest(it) }
    unmatchedUrlTests.filterTo(arrayListOf(), { !suite.isMyTest(it) })
    
    urlSuites.add(suite)
  }

  fun addRunConfigurationSuite(url: String,
                               magnitude: Magnitude,
                               runDate: Date,
                               runConfiguration: RunnerAndConfigurationSettings) 
  {

    val suite = SuiteEntry(url, magnitude, runDate)

    unmatchedRunConfigurationTests.filter { suite.isMyTest(it) }.forEach { suite.addTest(it) }
    unmatchedRunConfigurationTests = unmatchedRunConfigurationTests.filterTo(arrayListOf(), { !suite.isMyTest(it) })

    val configurationId = runConfiguration.uniqueID
    val suitePack = runConfigurationSuites[configurationId]
    if (suitePack != null) {
      suitePack.addSuite(suite)
      return
    }

    runConfigurationSuites[configurationId] = RunConfigurationEntry(runConfiguration, suite)
  }

  fun addUrlTest(url: String, magnitude: Magnitude, runDate: Date) {
    val test = SingleTestEntry(url, magnitude, runDate)
    findUrlSuite(url)?.addTest(test) ?: unmatchedUrlTests.add(test)
  }

  fun addRunConfigurationTest(url: String, magnitude: Magnitude, runDate: Date, runConfiguration: RunnerAndConfigurationSettings) {
    val test = SingleTestEntry(url, magnitude, runDate)
    findRunConfigurationTest(url, runConfiguration)?.addTest(test) ?: unmatchedRunConfigurationTests.add(test)
  }

  private fun findUrlSuite(url: String) = urlSuites.find {
    val testName = VirtualFileManager.extractPath(url)
    testName.startsWith(it.suiteName)
  }

  private fun findRunConfigurationTest(url: String, runConfiguration: RunnerAndConfigurationSettings): SuiteEntry? {
    val pack: RunConfigurationEntry = runConfigurationSuites[runConfiguration.uniqueID] ?: return null
    val testName = VirtualFileManager.extractPath(url)

    pack.suites.forEach {
      if (testName.startsWith(it.suiteName)) {
        return it
      }
    }
    
    return null
  }

  fun getTestsToShow(): List<RecentTestsPopupEntry> {
    assert(unmatchedRunConfigurationTests.isEmpty())
    
    val packsByDate = runConfigurationSuites.values.sortedByDescending { it.runDate }
    return packsByDate.fold(listOf(), { list, pack -> list + pack.entriesToShow() })
  }
  
}

fun RunConfigurationEntry.entriesToShow(): List<RecentTestsPopupEntry> {
  if (suites.size == 1) {
    return suites[0].entriesToShow()
  }

  val failedSuites = suites.filter { it.failedTests.size > 0 }
  if (failedSuites.size == 0) {
    return listOf(this)
  }
  return failedSuites + this
}

fun SuiteEntry.entriesToShow(): List<RecentTestsPopupEntry> {
  val failed = failedTests
  if (failed.size > 0) {
    return failed.sortedByDescending { it.runDate } + this
  }
  return listOf(this)
}
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

  private var unmatchedRunConfigurationTests = arrayListOf<SingleTestEntry>()

  private val urlSuites = mutableListOf<SuiteEntry>()
  private var unmatchedUrlTests = mutableListOf<SingleTestEntry>()

  
  fun addSuite(url: String, magnitude: Magnitude, runDate: Date, runConfiguration: RunnerAndConfigurationSettings?) {
    val suite = SuiteEntry(url, magnitude, runDate)
    if (runConfiguration != null) {
      addRunConfigurationSuite(suite, runConfiguration)
    }
    else {
      addUrlSuite(suite)
    }
  }

  fun addTest(url: String, magnitude: Magnitude, runDate: Date, runConfiguration: RunnerAndConfigurationSettings?) {
    val test = SingleTestEntry(url, magnitude, runDate)
    if (runConfiguration != null) {
      addRunConfigurationTest(test, runConfiguration)
    }
    else {
      addUrlTest(test)
    }
  }
  
  private fun addUrlSuite(suite: SuiteEntry) {
    val suiteTests = unmatchedUrlTests.filter { suite.isMyTest(it) }
    suiteTests.forEach { suite.addTest(it) }
    
    unmatchedUrlTests = unmatchedUrlTests.filterTo(arrayListOf(), { !suite.isMyTest(it) })
    
    urlSuites.add(suite)
  }

  private fun addRunConfigurationSuite(suite: SuiteEntry, config: RunnerAndConfigurationSettings) {
    val suiteTests = unmatchedRunConfigurationTests.filter { suite.isMyTest(it) }
    suiteTests.forEach { suite.addTest(it) }
    
    unmatchedRunConfigurationTests = unmatchedRunConfigurationTests.filterTo(arrayListOf(), { !suite.isMyTest(it) })

    val id = config.uniqueID
    runConfigurationSuites[id]?.addSuite(suite) ?: runConfigurationSuites.put(id, RunConfigurationEntry(config, suite))
  }

  private fun addUrlTest(test: SingleTestEntry) {
    findUrlSuite(test.url)?.addTest(test) ?: unmatchedUrlTests.add(test)
  }

  private fun addRunConfigurationTest(test: SingleTestEntry, runConfiguration: RunnerAndConfigurationSettings) {
    findRunConfigurationSuite(test.url, runConfiguration)?.addTest(test) ?: unmatchedRunConfigurationTests.add(test)
  }

  private fun findUrlSuite(url: String) = urlSuites.find {
    val testName = VirtualFileManager.extractPath(url)
    testName.startsWith(it.suiteName)
  }

  private fun findRunConfigurationSuite(url: String, runConfiguration: RunnerAndConfigurationSettings): SuiteEntry? {
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
    val allEntries: List<RecentTestsPopupEntry> = runConfigurationSuites.values + urlSuites
    return allEntries
        .sortedByDescending { it.runDate }
        .fold(listOf(), { popupList, currentEntry -> 
          when (currentEntry) {
            is RunConfigurationEntry -> popupList + currentEntry.entriesToShow()
            else -> popupList + currentEntry
          } 
        })
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
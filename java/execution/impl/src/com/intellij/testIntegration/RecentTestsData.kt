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
import com.intellij.openapi.vfs.VirtualFileManager

class RecentTestsData {

  private val runConfigurationSuites = hashMapOf<String, RunConfigurationEntry>()
  private var testsWithoutSuites = arrayListOf<SingleTestInfo>()
  
  fun addSuite(suite: SuiteEntry) {
    moveSuiteTestsToSuite(suite)
    
    val id = suite.runConfiguration.uniqueID
    val entry = runConfigurationSuites[id]
    if (entry != null) {
      entry.addSuite(suite)
    }
    else {
      val configurationEntry = RunConfigurationEntry(suite.runConfiguration)
      configurationEntry.addSuite(suite)
      runConfigurationSuites.put(id, configurationEntry)
    }
  }

  private fun moveSuiteTestsToSuite(suite: SuiteEntry) {
    val suiteConfiguration = suite.runConfiguration
    val filteredTests = arrayListOf<SingleTestInfo>()
    
    testsWithoutSuites.forEach {
      if (suite.isMyTest(it.test) && suiteConfiguration.name == it.runConfigurationName) {
        suite.addTest(it.test)
      }
      else {
        filteredTests.add(it)
      }
    }
    
    testsWithoutSuites = filteredTests
  }

  fun addTest(test: SingleTestEntry) {
    val suiteEntry = findRunConfigurationSuite(test.url, test.runConfiguration)
    if (suiteEntry != null) {
      suiteEntry.addTest(test)
    }
    else {
      testsWithoutSuites.add(SingleTestInfo(test, test.runConfiguration.name))
    }
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
    val allConfigurations = runConfigurationSuites.values

    val failedTests = getFailedTests(allConfigurations)

    val configsCollector = ConfigurationsCollector()
    allConfigurations.forEach { it.accept(configsCollector) }
    val passedConfigurations = configsCollector.entries.filter { !it.failed }
    
    val entriesToShow = failedTests + passedConfigurations + testsWithoutSuites.map { it.test }.filter { it.failed }
    
    return entriesToShow.sortedByDescending { it.runDate }
  }

  private fun getFailedTests(allConfigurations: MutableCollection<RunConfigurationEntry>): List<SingleTestEntry> {
    val testsCollector = SingleTestCollector()
    allConfigurations.forEach { it.accept(testsCollector) }
    val failedTests = testsCollector.tests.filter { it.failed }
    return failedTests
  }
}


fun SuiteEntry.isMyTest(test: SingleTestEntry): Boolean {
  val testName = VirtualFileManager.extractPath(test.url)
  return testName.startsWith(this.suiteName)
}

data class SingleTestInfo(val test: SingleTestEntry, val runConfigurationName: String)
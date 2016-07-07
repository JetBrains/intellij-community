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

data class SingleTestInfo(val test: SingleTestEntry, val runConfigurationName: String)

class RecentTestsData {

  private val runConfigurationSuites = hashMapOf<String, RunConfigurationEntry>()
  private var testsWithoutSuites = arrayListOf<SingleTestInfo>()
  
  fun addSuite(url: String, runDate: Date, runConfiguration: RunnerAndConfigurationSettings) {
    val suite = SuiteEntry(url, runDate)
    addRunConfigurationSuite(suite, runConfiguration)
  }

  fun addTest(url: String, magnitude: Magnitude, runDate: Date, runConfiguration: RunnerAndConfigurationSettings) {
    val test = SingleTestEntry(url, runDate, magnitude)
    addRunConfigurationTest(test, runConfiguration)
  }
  
  private fun addRunConfigurationSuite(suite: SuiteEntry, config: RunnerAndConfigurationSettings) {
    moveSuiteTestsToSuite(suite, config)
    
    val id = config.uniqueID
    val entry = runConfigurationSuites[id]
    if (entry != null) {
      entry.addSuite(suite)
    }
    else {
      runConfigurationSuites.put(id, RunConfigurationEntry(config, suite))
    }
  }

  private fun moveSuiteTestsToSuite(suite: SuiteEntry, config: RunnerAndConfigurationSettings) {
    val filteredTests = arrayListOf<SingleTestInfo>()
    
    testsWithoutSuites.forEach {
      if (suite.isMyTest(it.test) && config.name == it.runConfigurationName) {
        suite.addTest(it.test)
      }
      else {
        filteredTests.add(it)
      }
    }
    
    testsWithoutSuites = filteredTests
  }

  private fun addRunConfigurationTest(test: SingleTestEntry, runConfiguration: RunnerAndConfigurationSettings) {
    val suiteEntry = findRunConfigurationSuite(test.url, runConfiguration)
    if (suiteEntry != null) {
      suiteEntry.addTest(test)
    }
    else {
      testsWithoutSuites.add(SingleTestInfo(test, runConfiguration.name))
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

    val allSuites = allConfigurations.fold(arrayListOf<SuiteEntry>(), { list: List<SuiteEntry>, entry -> list + entry.suites })
    testsWithoutSuites.forEach {
      val info = it
      allSuites.find { it.isMyTest(info.test) }?.let { info.test.suite = it }
    }
    
    val testsCollector = SingleTestCollector()
    allConfigurations.forEach { it.accept(testsCollector) }
    val failedTests = testsCollector.tests.filter { it.failed }
    
    val configsCollector = ConfigurationsCollector()
    allConfigurations.forEach { it.accept(configsCollector) }
    val passedConfigurations = configsCollector.entries.filter { !it.failed }
    
    val entriesToShow = failedTests + passedConfigurations + testsWithoutSuites.map { it.test }.filter { it.suite != null && it.failed }
    
    return entriesToShow.sortedByDescending { it.runDate }
  }
}

class UrlsCollector: TestEntryVisitor() {
  val urls = mutableListOf<String>()

  override fun visitSuite(suite: SuiteEntry) {
    urls.add(suite.suiteUrl)
    suite.tests.forEach { urls.add(it.url) }
  }

  override fun visitRunConfiguration(configuration: RunConfigurationEntry) {
    configuration.suites.forEach { visitSuite(it) }
  }
}


class SingleTestCollector : TestEntryVisitor() {
  val tests = mutableListOf<SingleTestEntry>()
  
  override fun visitTest(test: SingleTestEntry) {
    tests.add(test)
  }

  override fun visitSuite(suite: SuiteEntry) {
    suite.tests.forEach { it.accept(this) }
  }

  override fun visitRunConfiguration(configuration: RunConfigurationEntry) {
    configuration.suites.forEach { it.accept(this) }
  }
}  


class ConfigurationsCollector : TestEntryVisitor() {
  val entries = mutableListOf<RecentTestsPopupEntry>()
  
  override fun visitRunConfiguration(configuration: RunConfigurationEntry) {
    entries.add(configuration)
  }

  override fun visitSuite(suite: SuiteEntry) {
    entries.add(suite)
  }
}
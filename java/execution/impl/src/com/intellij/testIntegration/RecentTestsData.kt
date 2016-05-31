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

  private val runConfigurationSuites = hashMapOf<String, RunConfigurationEntry>()
  private var testsWithoutSuites: MutableList<SingleTestEntry> = ContainerUtil.newArrayList<SingleTestEntry>()

  fun addSuite(url: String,
               magnitude: TestStateInfo.Magnitude,
               runDate: Date,
               runConfiguration: RunnerAndConfigurationSettings) 
  {

    val suiteInfo = SuiteEntry(url, magnitude, runDate, runConfiguration)

    val configurationId = runConfiguration.uniqueID
    val suitePack = runConfigurationSuites[configurationId]
    if (suitePack != null) {
      suitePack.addSuite(suiteInfo)
      return
    }
    
    runConfigurationSuites[configurationId] = RunConfigurationEntry(runConfiguration, suiteInfo)
  }


  fun addTest(url: String,
              magnitude: TestStateInfo.Magnitude,
              runDate: Date,
              runConfiguration: RunnerAndConfigurationSettings) {

    val testInfo = SingleTestEntry(url, magnitude, runDate, runConfiguration)

    val suite = findSuite(url, runConfiguration)
    if (suite != null) {
      suite.addTest(testInfo)
      return
    }

    testsWithoutSuites.add(testInfo)
  }

  private fun findSuite(url: String, runConfiguration: RunnerAndConfigurationSettings): SuiteEntry? {
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
    testsWithoutSuites.forEach {
      val url = it.url
      findSuite(url, it.runConfiguration)?.addTest(it)
    }
    
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
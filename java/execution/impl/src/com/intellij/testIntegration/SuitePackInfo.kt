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
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude.*
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.containers.ContainerUtil
import java.util.*

interface RecentTestsPopupEntry {
  val runDate: Date
  val magnitude: TestStateInfo.Magnitude
  val presentation: String

  val testsUrls: List<String>

  fun run(runner: RecentTestRunner)
}

open class TestInfo(val url: String, override val magnitude: TestStateInfo.Magnitude, override val runDate: Date) : RecentTestsPopupEntry {
  override val presentation = VirtualFileManager.extractPath(url)
  override val testsUrls = listOf(url)

  override fun run(runner: RecentTestRunner) {
    runner.run(url)
  }
}

class SuiteInfo(url: String, magnitude: TestStateInfo.Magnitude, runDate: Date) : TestInfo(url, magnitude, runDate) {

  private val tests = ContainerUtil.newHashSet<TestInfo>()

  override val testsUrls: List<String>
    get() = tests.fold(listOf<String>(), { acc, testEntry -> acc + testEntry.testsUrls })

  
  val suiteName = VirtualFileManager.extractPath(url)

  val mostRecentRunDate: Date
    get() {
      var mostRecent = runDate
      for (test in tests) {
        val testDate = test.runDate
        if (testDate.compareTo(mostRecent) > 0) {
          mostRecent = testDate
        }
      }
      return mostRecent
    }


  fun canTrustSuiteMagnitude(): Boolean {
    val suiteRunDate = runDate
    for (test in tests) {
      if (test.runDate.time > suiteRunDate.time) {
        return false
      }
    }
    return true
  }
  
  val isPassed: Boolean
    get() = magnitude == IGNORED_INDEX || magnitude == PASSED_INDEX || magnitude == COMPLETE_INDEX
  
  val failedTests: List<TestInfo>
    get() {
      val failed = ContainerUtil.newSmartList<TestInfo>()
      for (test in tests) {
        if (test.magnitude == FAILED_INDEX || test.magnitude == ERROR_INDEX) {
          failed.add(test)
        }
      }
      return failed
    }

  fun addTest(info: TestInfo) {
    tests.add(info)
  }

  val totalTestsCount: Int
    get() = tests.size

  override val presentation = VirtualFileManager.extractPath(url)

}


class SuitePackInfo(val runSettings: RunnerAndConfigurationSettings, initial: SuiteInfo) : RecentTestsPopupEntry {

  val suites = ContainerUtil.newArrayList<SuiteInfo>()

  init {
    addSuite(initial)
  }

  fun addSuite(s: SuiteInfo) = suites.add(s)

  override val runDate = suites.map { it.runDate }.min()!!

  override val magnitude = COMPLETE_INDEX

  override val presentation = runSettings.name

  override val testsUrls: List<String>
    get() = suites.fold(listOf<String>(), { list, suite -> list + suite.testsUrls })
  
  override fun run(runner: RecentTestRunner) {
    runner.run(runSettings)
  }
}

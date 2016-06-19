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
import com.intellij.psi.PsiElement
import java.util.*

interface RecentTestsPopupEntry {
  val runDate: Date
  val magnitude: TestStateInfo.Magnitude
  val presentation: String

  val testsUrls: List<String>

  fun run(runner: RecentTestRunner)

  open fun navigatableElement(locator: TestLocator): PsiElement? = null
  
  fun getEntriesToShow(): List<RecentTestsPopupEntry>
}

open class SingleTestEntry(val url: String, 
                           override val magnitude: TestStateInfo.Magnitude, 
                           override val runDate: Date) : RecentTestsPopupEntry 
{

  override val presentation = VirtualFileManager.extractPath(url)
  override val testsUrls = listOf(url)

  override fun run(runner: RecentTestRunner) {
    runner.run(url)
  }

  override fun navigatableElement(locator: TestLocator) = locator.getLocation(url)?.psiElement

  override fun getEntriesToShow(): List<RecentTestsPopupEntry> = listOf(this)
  
}

class SuiteEntry(url: String, magnitude: TestStateInfo.Magnitude, runDate: Date) : SingleTestEntry(url, magnitude, runDate) {
  
  private val tests = hashSetOf<SingleTestEntry>()

  override val testsUrls: List<String>
    get() = tests.fold(listOf<String>(), { acc, testEntry -> acc + testEntry.testsUrls })
  
  val suiteName = VirtualFileManager.extractPath(url)
  
  val failedTests: List<SingleTestEntry>
    get() = tests.filter { it.magnitude == FAILED_INDEX || it.magnitude == ERROR_INDEX }
  
  fun addTest(info: SingleTestEntry) = tests.add(info)

  override val presentation = suiteName

  override fun getEntriesToShow(): List<RecentTestsPopupEntry> {
    val failed = failedTests
    if (failed.size > 0) {
      return failed.sortedByDescending { it.runDate } + this
    }
    return listOf(this)
  }
  
  override val magnitude: TestStateInfo.Magnitude by lazy {
    tests.find { it.magnitude != PASSED_INDEX && it.magnitude != COMPLETE_INDEX }?.magnitude ?: PASSED_INDEX
  }
  
}


class RunConfigurationEntry(val runSettings: RunnerAndConfigurationSettings, initial: SuiteEntry) : RecentTestsPopupEntry {

  val suites = arrayListOf<SuiteEntry>()

  init {
    addSuite(initial)
  }

  fun addSuite(s: SuiteEntry) = suites.add(s)

  override val runDate = suites.map { it.runDate }.min()!!

  override val magnitude: TestStateInfo.Magnitude by lazy {
    suites.find { it.magnitude != PASSED_INDEX && it.magnitude != COMPLETE_INDEX }?.magnitude ?: PASSED_INDEX
  }

  override val presentation = runSettings.name

  override val testsUrls: List<String>
    get() = suites.fold(listOf<String>(), { list, suite -> list + suite.testsUrls })
  
  override fun run(runner: RecentTestRunner) {
    runner.run(runSettings)
  }

  override fun getEntriesToShow(): List<RecentTestsPopupEntry> {
    if (suites.size == 1) {
      return suites[0].getEntriesToShow()
    }
    
    return suites
        .filter { it.failedTests.size > 0}
        .sortedByDescending { it.runDate }
        .fold(listOf<RecentTestsPopupEntry>(), { popupList, currentEntry ->
          popupList + currentEntry.getEntriesToShow()
        }) + this
  }
  
}

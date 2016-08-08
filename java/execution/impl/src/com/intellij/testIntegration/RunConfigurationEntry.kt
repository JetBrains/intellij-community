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
import com.intellij.execution.testframework.TestIconMapper
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude.ERROR_INDEX
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude.FAILED_INDEX
import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.VirtualFileManager
import java.util.*
import javax.swing.Icon


interface RecentTestsPopupEntry {
  val icon: Icon?
  val presentation: String
  val runDate: Date
  
  val failed: Boolean

  fun accept(visitor: TestEntryVisitor)
}

abstract class TestEntryVisitor {
  open fun visitTest(test: SingleTestEntry) = Unit
  open fun visitSuite(suite: SuiteEntry) = Unit
  open fun visitRunConfiguration(configuration: RunConfigurationEntry) = Unit
}

private fun String.toClassName(allowedDots: Int): String {
  val fqn = VirtualFileManager.extractPath(this)
  var dots = 0
  return fqn.takeLastWhile { 
    if (it == '.') dots++
    dots <= allowedDots
  }
}

class SingleTestEntry(val url: String,
                      override val runDate: Date,
                      val runConfiguration: RunnerAndConfigurationSettings,
                      magnitude: TestStateInfo.Magnitude) : RecentTestsPopupEntry 
{

  override val presentation = url.toClassName(1)
  override val icon = TestIconMapper.getIcon(magnitude)
  
  override val failed = magnitude == ERROR_INDEX || magnitude == FAILED_INDEX
  
  var suite: SuiteEntry? = null
  
  override fun accept(visitor: TestEntryVisitor) {
    visitor.visitTest(this)
  }
  
}


class SuiteEntry(val suiteUrl: String, 
                 override val runDate: Date,
                 var runConfiguration: RunnerAndConfigurationSettings) : RecentTestsPopupEntry {

  val tests = hashSetOf<SingleTestEntry>()
  val suiteName = VirtualFileManager.extractPath(suiteUrl)
  
  var runConfigurationEntry: RunConfigurationEntry? = null

  override val presentation = suiteUrl.toClassName(0)
  override val icon: Icon? = AllIcons.RunConfigurations.Junit
  
  override val failed: Boolean
    get() {
      return tests.find { it.failed } != null
    }
  
  fun addTest(test: SingleTestEntry) {
    tests.add(test)
    test.suite = this
  }

  override fun accept(visitor: TestEntryVisitor) {
    visitor.visitSuite(this)
  }

}


class RunConfigurationEntry(val runSettings: RunnerAndConfigurationSettings) : RecentTestsPopupEntry {

  val suites = arrayListOf<SuiteEntry>()
  
  override val runDate: Date
    get() {
      return suites.minBy { it.runDate }!!.runDate
    }
  
  
  override val failed: Boolean
    get() {
      return suites.find { it.failed } != null
    }

  fun addSuite(suite: SuiteEntry) {
    suites.add(suite)
    suite.runConfigurationEntry = this
  }

  override val presentation: String = runSettings.name

  override val icon: Icon? = AllIcons.RunConfigurations.Junit

  override fun accept(visitor: TestEntryVisitor) {
    visitor.visitRunConfiguration(this)
  }
  
}
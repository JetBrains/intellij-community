// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  open fun visitTest(test: SingleTestEntry): Unit = Unit
  open fun visitSuite(suite: SuiteEntry): Unit = Unit
  open fun visitRunConfiguration(configuration: RunConfigurationEntry): Unit = Unit
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

  override val presentation: String = url.toClassName(1)
  override val icon: Icon? = TestIconMapper.getIcon(magnitude)
  
  override val failed: Boolean = magnitude == ERROR_INDEX || magnitude == FAILED_INDEX
  
  var suite: SuiteEntry? = null
  
  override fun accept(visitor: TestEntryVisitor) {
    visitor.visitTest(this)
  }
  
}


class SuiteEntry(val suiteUrl: String, 
                 override val runDate: Date,
                 var runConfiguration: RunnerAndConfigurationSettings) : RecentTestsPopupEntry {

  val tests: HashSet<SingleTestEntry> = hashSetOf()
  val suiteName: String = VirtualFileManager.extractPath(suiteUrl)
  
  var runConfigurationEntry: RunConfigurationEntry? = null

  override val presentation: String = suiteUrl.toClassName(0)
  override val icon: Icon = AllIcons.RunConfigurations.Junit
  
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

  val suites: ArrayList<SuiteEntry> = arrayListOf()
  
  override val runDate: Date
    get() {
      return suites.minByOrNull { it.runDate }!!.runDate
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

  override val icon: Icon = AllIcons.RunConfigurations.Junit

  override fun accept(visitor: TestEntryVisitor) {
    visitor.visitRunConfiguration(this)
  }
  
}
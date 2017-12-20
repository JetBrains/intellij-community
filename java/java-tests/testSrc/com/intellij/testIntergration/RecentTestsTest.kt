/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.testIntergration

import com.intellij.testIntegration.RecentTestsPopupEntry
import com.intellij.testIntegration.RunConfigurationEntry
import com.intellij.testIntegration.SuiteEntry
import com.intellij.testIntegration.TestConfigurationCollector
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class RecentTestsStepTest : RecentTestsTestCase() {
  @Test fun `test all tests passed`() {
    addPassedTest("Test.textXXX", now)
    addSuite("Test", now)
    addSuite("JFSDTest", now)
    addPassedTest("Test.textYYY", now)
    addPassedTest("JFSDTest.testItMakesMeSadToFixIt", now)
    addPassedTest("Test.textZZZ", now)
    addPassedTest("Test.textQQQ", now)
    addPassedTest("JFSDTest.testUnconditionalAlignmentErroneous", now)

    val tests = getTestsToShow()
    assertThat(tests).hasSize(1)
    assertThat(tests[0].presentation).isEqualTo("all tests")
  }

  @Test fun `test if one failed in run configuration show failed suite`() {
    addSuite("JFSDTest", now)
    addSuite("Test", now)
    addFailedTest("JFSDTest.testItMakesMeSadToFixIt", now)
    addPassedTest("JFSDTest.testUnconditionalAlignmentErroneous", now)
    addPassedTest("Test.textXXX", now)

    val tests = getTestsToShow()
    assertThat(tests).hasSize(1)
    assertThat(tests[0].presentation).isEqualTo("JFSDTest.testItMakesMeSadToFixIt")
    assertThat(tests[0].failed).isEqualTo(true)
  }

  @Test fun `test if configuration with single test show failed test`() {
    addSuite("JFSDTest", now)
    addFailedTest("JFSDTest.testItMakesMeSadToFixIt", now)
    addPassedTest("JFSDTest.testUnconditionalAlignmentErroneous", now)

    val tests = getTestsToShow()
    assertThat(tests).hasSize(1)
    assertThat(tests[0].presentation).isEqualTo("JFSDTest.testItMakesMeSadToFixIt")
  }

  @Test fun `test show test without suite`() {
    addFailedTest("Test.someTest", now)
    val testsToShow = getTestsToShow()
    assertThat(testsToShow).hasSize(1)
  }

  @Test fun `test additional entries`() {
    addSuite("Test2", now)
    addSuite("Test", now)
    addFailedTest("Test.sss", now)

    val tests = getTestsToShow()
    assertThat(tests).hasSize(1)
    val configs = getConfigs(tests[0])
    assertThat(configs).hasSize(2)
    assertThat(configs[0]).isInstanceOf(SuiteEntry::class.java)
    assertThat(configs[1]).isInstanceOf(RunConfigurationEntry::class.java)
  }

  @Test fun `test if configuration consists of single test show only configuration`() {
    addSuite("Test", now)
    addFailedTest("Test.sss", now)

    val tests = getTestsToShow()
    assertThat(tests).hasSize(1)
    val configs = getConfigs(tests[0])
    assertThat(configs).hasSize(1)
    assertThat(configs[0]).isInstanceOf(RunConfigurationEntry::class.java)
  }

  private fun getConfigs(test: RecentTestsPopupEntry): List<RecentTestsPopupEntry> {
    val collector = TestConfigurationCollector()
    test.accept(collector)
    return collector.getEnclosingConfigurations()
  }
}
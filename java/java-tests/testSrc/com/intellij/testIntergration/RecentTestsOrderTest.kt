/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.testIntergration

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.*

class RecentTestsOrderTest : RecentTestsTestCase() {
  @Test fun `test run configuration with one suite shows only suite`() {
    addPassedTest("MySingleTest.test1", now)
    addSuite("MySingleTest", now)

    val testsToShow = getTestsToShow()
    assertThat(testsToShow).hasSize(1)
    assertThat(testsToShow[0].presentation).isEqualTo("all tests")
  }

  @Test fun `test run configuration with multiple suites shows run configuration name`() {
    addSuite("MyFirstTest", now)
    addPassedTest("MyFirstTest.test1", now)

    addSuite("MySecondTest", Date())
    addPassedTest("MySecondTest.test1", Date())

    val tests = getTestsToShow()
    assertThat(tests).hasSize(1)
    assertThat(tests[0].presentation).isEqualTo("all tests")
  }

  @Test fun `test show failed suite in test`() {
    addSuite("SingleTest", Date())
    addFailedTest("SingleTest.test", Date())

    val tests = getTestsToShow()
    assertThat(tests).hasSize(1)
    assertThat(tests[0].presentation).isEqualTo("SingleTest.test")
    assertThat(tests[0].failed).isEqualTo(true)
  }

  @Test fun `test show failed suite and test in run configuration`() {
    addSuite("SingleTest", Date())
    addFailedTest("SingleTest.test", Date())
    addPassedTest("SingleTest.test2", Date())
    addSuite("PassedSuite", Date())

    val tests = getTestsToShow()
    assertThat(tests).hasSize(1)
    assertThat(tests[0].presentation).isEqualTo("SingleTest.test")
    assertThat(tests[0].failed).isEqualTo(true)
  }

  @Test fun `test single test run doesn't override suite status`() {
    val singleTestConfig = mockConfiguration("single test", "Junit.single test")
    val newNow = Date(now.time + 100000)

    //previous all suite run
    addFailedTest("Test.testFailed", now)

    //current single test run
    addSuite("Test", newNow, singleTestConfig)
    addPassedTest("Test.testOK", newNow, singleTestConfig)

    val tests = getTestsToShow()
    assertThat(tests).hasSize(2)
    assertThat(tests[0].presentation).isEqualTo("single test")
    assertThat(tests[1].presentation).isEqualTo("Test.testFailed")
  }

  @Test fun `test single test run doesn't override suite status, independent from registering order`() {
    val singleTestConfig = mockConfiguration("single test", "Junit.single test")
    val newNow = Date(now.time + 100000)

    //previous all suite run
    addPassedTest("Test.testOK", now)

    //current single test run
    addSuite("Test", newNow, singleTestConfig)
    addFailedTest("Test.testFailed", newNow, singleTestConfig)

    val tests = getTestsToShow()
    assertThat(tests).hasSize(1)
    assertThat(tests[0].presentation).isEqualTo("Test.testFailed")
  }
}
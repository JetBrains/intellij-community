// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures

import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import org.junit.Rule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.junit.rules.TestRule

/**
 * A wrapper around [LightJavaCodeInsightFixtureTestCase] that is JUnit 4-compatible.
 * 
 * @see LightJavaCodeInsightFixtureTestCase
 * @see LightJavaCodeInsightFixtureTestCase5
 */
@TestDataPath("\$CONTENT_ROOT/testData")
abstract class LightJavaCodeInsightFixtureTestCase4(
  projectDescriptor: LightProjectDescriptor? = null,
  protected val testDataPath: String? = null
) {

  private val testNameRule = TestName()

  protected val testName: String
    get() {
      return testNameRule.methodName
        .split(" ")
        .joinToString(separator = "") { it.capitalize() }
        .decapitalize()
    }

  private val testCase = object : LightJavaCodeInsightFixtureTestCase() {

    override fun getProjectDescriptor(): LightProjectDescriptor = projectDescriptor ?: super.getProjectDescriptor()

    override fun getTestDataPath(): String = this@LightJavaCodeInsightFixtureTestCase4.testDataPath ?: super.getTestDataPath()

    public override fun setUp() = super.setUp()

    public override fun tearDown() = super.tearDown()

    val fixture: JavaCodeInsightTestFixture get() = myFixture
  }

  private val fixtureRule = object : ExternalResource() {

    override fun before(): Unit = testCase.setUp()

    override fun after(): Unit = testCase.tearDown()
  }

  protected val fixture: JavaCodeInsightTestFixture get() = testCase.fixture

  @Rule
  @JvmField
  val testRule: TestRule = RuleChain.outerRule(testNameRule).around(fixtureRule).around(EdtRule())
}

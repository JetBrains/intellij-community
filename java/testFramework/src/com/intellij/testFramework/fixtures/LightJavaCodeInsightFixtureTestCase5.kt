// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures

import com.intellij.JavaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.rules.TestNameExtension
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * A wrapper around [LightJavaCodeInsightFixtureTestCase] that is JUnit 5-compatible.
 * 
 * @see LightJavaCodeInsightFixtureTestCase
 * @see LightJavaCodeInsightFixtureTestCase4
 */
@TestDataPath("\$CONTENT_ROOT/testData")
abstract class LightJavaCodeInsightFixtureTestCase5 (projectDescriptor: LightProjectDescriptor? = null) {

  protected open fun getRelativePath() : String = JavaTestUtil.getRelativeJavaTestDataPath()
  protected open fun getTestDataPath() : String? = null

  @RegisterExtension
  protected val testNameRule: TestNameExtension = TestNameExtension()

  protected fun getTestName(lowercaseFirstLetter: Boolean): String {
    return PlatformTestUtil.getTestName(testNameRule.methodName, lowercaseFirstLetter)
  }
  
  @RegisterExtension
  private val testCase = object : LightJavaCodeInsightFixtureTestCase(), BeforeEachCallback, AfterEachCallback {

    override fun getProjectDescriptor(): LightProjectDescriptor = projectDescriptor ?: JAVA_LATEST_WITH_LATEST_JDK

    override fun getTestDataPath(): String = this@LightJavaCodeInsightFixtureTestCase5.getTestDataPath() ?: super.getTestDataPath()

    override fun getBasePath(): String = this@LightJavaCodeInsightFixtureTestCase5.getRelativePath() 

    override fun beforeEach(context: ExtensionContext?) {
      setUp()
    }

    override fun afterEach(context: ExtensionContext?) {
      tearDown()
    }

    val fixture: JavaCodeInsightTestFixture get() = myFixture
  }

  protected val fixture: JavaCodeInsightTestFixture get() = testCase.fixture
}

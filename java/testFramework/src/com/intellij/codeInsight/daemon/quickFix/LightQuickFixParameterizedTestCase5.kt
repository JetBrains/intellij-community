// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix

import com.intellij.testFramework.FileBasedArgumentProvider
import com.intellij.testFramework.FileBasedTestCaseHelperEx
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsSource

/**
 * @deprecated Use [lightQuickFixFixture] with a parameterized test method and [lightQuickFixTestFiles] in a JUnit 5 test.
 */
@Deprecated("Use lightQuickFixFixture with a parameterized test method and lightQuickFixTestFiles")
abstract class LightQuickFixParameterizedTestCase5(projectDescriptor: LightProjectDescriptor? = null)
  : LightJavaCodeInsightFixtureTestCase5(projectDescriptor), FileBasedTestCaseHelperEx {
  abstract override fun getTestDataPath(): String

  override fun getRelativeBasePath(): String {
    return ""
  }

  override fun getFileSuffix(fileName: String): String? {
    return if (!fileName.startsWith(LightQuickFixTestCase.BEFORE_PREFIX)) null
    else fileName.substring(LightQuickFixTestCase.BEFORE_PREFIX.length)
  }

  override fun getBaseName(fileAfterSuffix: String): String? {
    return if (!fileAfterSuffix.startsWith(LightQuickFixTestCase.AFTER_PREFIX)) null
    else fileAfterSuffix.substring(LightQuickFixTestCase.AFTER_PREFIX.length)
  }

  @ParameterizedTest(name = ParameterizedTest.ARGUMENTS_WITH_NAMES_PLACEHOLDER)
  @ArgumentsSource(FileBasedArgumentProvider::class)
  @Throws(Throwable::class)
  fun parameterized(fileName: String) {
    doLightQuickFixFileTest(fixture, getTestDataPath(), fileName)
  }

}

/**
 * Checks one test: the `before` file [fileName] of [testDataPath] with the action hint in it, as [LightQuickFixFixture.doTest] does.
 * When [exactMatch] is false, the hint text is a prefix of the action text.
 * It runs off the EDT.
 */
@TestOnly
@JvmOverloads
fun doLightQuickFixFileTest(fixture: JavaCodeInsightTestFixture, testDataPath: String, fileName: String, exactMatch: Boolean = true) {
  val options = if (exactMatch) LightQuickFixOptions.DEFAULT else LightQuickFixOptions.DEFAULT.withPrefixActionHintMatch()
  LightQuickFixFixture(fixture, testDataPath, options).doTest(fileName)
}

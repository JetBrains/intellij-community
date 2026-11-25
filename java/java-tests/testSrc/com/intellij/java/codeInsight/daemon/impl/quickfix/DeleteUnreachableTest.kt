// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.quickFix.lightQuickFixFixture
import com.intellij.codeInsight.daemon.quickFix.lightQuickFixTestFiles
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.fixtures.lightJavaTestDescriptor
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@TestApplication
class DeleteUnreachableTest {
  companion object {
    private val TEST_DATA_PATH = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/deleteUnreachable"
    private val projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor())

    @JvmStatic
    fun files(): List<String> = lightQuickFixTestFiles(TEST_DATA_PATH)
  }

  private val quickFixFixture = lightQuickFixFixture(projectFixture, TEST_DATA_PATH)

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  fun parameterized(fileName: String) {
    quickFixFixture.get().doTest(fileName)
  }
}

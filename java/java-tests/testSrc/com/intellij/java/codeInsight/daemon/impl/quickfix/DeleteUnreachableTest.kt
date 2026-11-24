// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFileBasedTest
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.fixtures.lightJavaTestDescriptor
import com.intellij.testFramework.junit5.TestApplication

@TestApplication
class DeleteUnreachableTest : LightQuickFixFileBasedTest() {
  companion object {
    private val projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor())
  }

  private val codeInsightFixture = lightJavaCodeInsightFixture(projectFixture)

  override fun getFixture(): JavaCodeInsightTestFixture = codeInsightFixture.get()

  override fun getTestDataPath(): String = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/deleteUnreachable"
}
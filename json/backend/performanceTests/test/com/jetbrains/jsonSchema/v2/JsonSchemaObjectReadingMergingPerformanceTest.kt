// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JsonSchemaCompliance")

package com.jetbrains.jsonSchema.v2

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.ex.PathManagerEx.TestDataLookupStrategy
import com.intellij.testFramework.PerformanceUnitTest
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

@PerformanceUnitTest
class JsonSchemaObjectReadingMergingPerformanceTest : BasePlatformTestCase() {
  fun `test measure performance here`() {
    myFixture.configureByText("openapi.yaml", yamlspec8k())
    myFixture.checkHighlighting()
  }

  override fun getTestDataPath(): String {
    val strategy = PathManagerEx.guessTestDataLookupStrategy()
    if (strategy == TestDataLookupStrategy.COMMUNITY) {
      return PathManager.getHomePath() + "/json/backend/tests/testData/jsonSchema/v2"
    }
    return PathManager.getHomePath() + "/community/json/backend/tests/testData/jsonSchema/v2"
  }

  private fun yamlspec8k(): String {
    return File("$testDataPath/specs/largespec8k.yaml").readText()
  }
}

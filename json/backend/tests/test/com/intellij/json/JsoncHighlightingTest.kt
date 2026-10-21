// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.codeinsight.JsonStandardComplianceInspection
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/json/backend/tests/testData")
class JsoncHighlightingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val codeInsightFixture = codeInsightModuleFixture(projectFixture)
  private val highlightingFixture by jsonHighlightingFixture(codeInsightFixture, "jsonc")

  @Test
  fun testJSONC() {
    highlightingFixture.fixture.enableInspections(JsonStandardComplianceInspection())
    highlightingFixture.doTestHighlighting(false, true, true)
  }

  @Test
  fun testJsoncComplianceProblems() {
    highlightingFixture.fixture.enableInspections(JsonStandardComplianceInspection())
    highlightingFixture.doTestHighlighting(false, true, true)
  }
}

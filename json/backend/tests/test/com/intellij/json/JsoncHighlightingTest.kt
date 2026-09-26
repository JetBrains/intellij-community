// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.json.codeinsight.JsonStandardComplianceInspection

class JsoncHighlightingTest : JsonHighlightingTestBase() {
  override fun getExtension(): String {
    return "jsonc"
  }

  fun testJSONC() {
    myFixture.enableInspections(JsonStandardComplianceInspection())
    doTestHighlighting(false, true, true)
  }

  fun testJsoncComplianceProblems() {
    myFixture.enableInspections(JsonStandardComplianceInspection())
    doTestHighlighting(false, true, true)
  }
}
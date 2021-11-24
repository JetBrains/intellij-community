// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath

import com.intellij.jsonpath.inspections.JsonPathUnknownFunctionInspection
import com.intellij.jsonpath.inspections.JsonPathUnknownOperatorInspection
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import org.junit.Test

class JsonPathInspectionTest : LightPlatformCodeInsightFixture4TestCase() {

  override fun setUp() {
    super.setUp()

    myFixture.enableInspections(
      JsonPathUnknownFunctionInspection::class.java,
      JsonPathUnknownOperatorInspection::class.java
    )
  }

  @Test
  fun sizeIsKnownFunction() {
    myFixture.configureByText(JsonPathFileType.INSTANCE, "$.size()")
    myFixture.checkHighlighting()
  }

  @Test
  fun unknownFunction() {
    myFixture.configureByText(JsonPathFileType.INSTANCE, "\$.<warning descr=\"Unknown function name 'unknown'\">unknown</warning>()")
    myFixture.checkHighlighting()
  }

  @Test
  fun unknownOperator() {
    myFixture.configureByText(JsonPathFileType.INSTANCE, "$[?(@.id <warning descr=\"Unknown operator 'unknown_operator'\">unknown_operator</warning> 2)]")
    myFixture.checkHighlighting()
  }
}
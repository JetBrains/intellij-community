// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly

/**
 * Creates a [JsonHighlightingTestFixture] on [codeInsightFixture].
 * It is the JUnit 5 replacement of the JUnit 3 base `JsonHighlightingTestBase`.
 *
 * Declare the result as an instance property, so that each test gets its own fixture.
 * The test data path of the code insight fixture must be `$PROJECT_ROOT/community/json/backend/tests/testData`.
 *
 * @param extension the extension of the test data file that [JsonHighlightingTestFixture.doTestHighlighting] checks
 */
@TestOnly
fun jsonHighlightingFixture(
  codeInsightFixture: TestFixture<CodeInsightTestFixture>,
  extension: String,
): TestFixture<JsonHighlightingTestFixture> = testFixture("jsonHighlightingFixture") { context ->
  initialized(JsonHighlightingTestFixture(codeInsightFixture.init(), context.testName, extension)) {}
}

/**
 * Checks the highlighting of the test data file `highlighting/<test name>.<extension>`.
 * The test name is the name of the test method without the `test` prefix.
 *
 * Call a helper off the EDT.
 */
@TestOnly
class JsonHighlightingTestFixture internal constructor(
  val fixture: CodeInsightTestFixture,
  private val testName: String,
  private val extension: String,
) {
  fun doTest() {
    doTestHighlighting(true, true, true)
  }

  fun doTestHighlighting(checkInfo: Boolean, checkWeakWarning: Boolean, checkWarning: Boolean) {
    fixture.testHighlighting(checkWarning, checkInfo, checkWeakWarning, "/highlighting/$testName.$extension")
  }

  fun doTestHighlightingForJsonLines(checkInfo: Boolean, checkWeakWarning: Boolean, checkWarning: Boolean) {
    fixture.testHighlighting(checkWarning, checkInfo, checkWeakWarning, "/highlighting/$testName.jsonl")
  }
}

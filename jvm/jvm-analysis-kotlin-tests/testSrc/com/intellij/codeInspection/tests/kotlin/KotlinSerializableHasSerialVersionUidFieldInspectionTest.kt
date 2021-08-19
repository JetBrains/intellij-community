package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.SerializableHasSerialVersionUidFieldInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/serializablehasserialversionuidfield"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinSerializableHasSerialVersionUidFieldInspectionTest : SerializableHasSerialVersionUidFieldInspectionTestBase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test highlighting`() {
    myFixture.testHighlighting("SerializableHasSerialVersionUidField.kt")
  }

  fun `test quickfix`() {
    myFixture.testQuickFix("SerializableHasSerialVersionUidFieldQf.kt", "Add 'serialVersionUID' field")
  }
}
package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.SerializableHasSerialVersionUidFieldInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/serializablehasserialversionuidfield"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinSerializableHasSerialVersionUidFieldInspectionTest : SerializableHasSerialVersionUidFieldInspectionTestBase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  override val fileExt: String = "kt"

  fun `test highlighting`() = testHighlighting("SerializableHasSerialVersionUidField")

  // TODO enable when quickfix for Kotlin is enabled
  //fun `test quickfix`() = testQuickFix("SerializableHasSerialVersionUidFieldQf", "Add 'serialVersionUID' field")
}
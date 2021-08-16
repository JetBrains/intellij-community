package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.SerializableHasSerialVersionUidFieldInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/serializablehasserialversionuidfield"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class JavaSerializableHasSerialVersionUidFieldInspectionTest : SerializableHasSerialVersionUidFieldInspectionTestBase() {
  override fun getBasePath() = JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  override val fileExt: String = "java"

  fun `test highlighting`() = testHighlighting("SerializableHasSerialVersionUidField")

  fun `test quickfix`() = testQuickFix("SerializableHasSerialVersionUidFieldQf", "Add 'serialVersionUID' field")
}
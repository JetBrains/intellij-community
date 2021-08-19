package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.SerializableHasSerialVersionUidFieldInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/serializablehasserialversionuidfield"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class JavaSerializableHasSerialVersionUidFieldInspectionTest : SerializableHasSerialVersionUidFieldInspectionTestBase() {
  override fun getBasePath() = JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test highlighting`() {
    myFixture.testHighlighting("SerializableHasSerialVersionUidField.java")
  }

  fun `test quickfix`() {
    myFixture.testQuickFix("SerializableHasSerialVersionUidFieldQf.java", "Add 'serialVersionUID' field")
  }
}
package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.SerializableHasSerialVersionUidFieldInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil

class JavaSerializableHasSerialVersionUidFieldInspectionTest : SerializableHasSerialVersionUidFieldInspectionTestBase() {
  override fun getBasePath() =
    JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/serializablehasserialversionuidfield"

  fun `test highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import java.io.Serializable;
      
      class <warning descr="'Foo' does not define a 'serialVersionUID' field">Foo</warning> implements Serializable { }
    """.trimIndent())
  }

  fun `test quickfix`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      import java.io.Serializable;

      class Fo<caret>o implements Serializable { }
    """.trimIndent(), """
      import java.io.Serializable;

      class Foo implements Serializable {
          private static final long serialVersionUID = -4454552974617678229L;
      }
    """.trimIndent(), "Add 'serialVersionUID' field")
  }
}
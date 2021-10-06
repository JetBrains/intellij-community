package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.SerializableHasSerialVersionUidFieldInspectionTestBase
import com.intellij.pom.java.LanguageLevel

class JavaSerializableHasSerialVersionUidFieldInspectionJdk14Test : SerializableHasSerialVersionUidFieldInspectionTestBase() {
  override val languageLevel: LanguageLevel = LanguageLevel.JDK_14

  fun `test quickfix @Serial annotation`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      import java.io.Serializable;

      class Fo<caret>o implements Serializable { }
    """.trimIndent(), """
      import java.io.Serializable;

      class Foo implements Serializable {
          @java.io.Serial
          private static final long serialVersionUID = -4454552974617678229L;
      }
    """.trimIndent(), "Create constant field 'serialVersionUID' in 'Foo'")
  }
}
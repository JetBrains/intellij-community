package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.SerializableHasSerialVersionUidFieldInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.pom.java.LanguageLevel

class JavaSerializableHasSerialVersionUidFieldInspectionTest : SerializableHasSerialVersionUidFieldInspectionTestBase() {
  fun `test highlighting`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.io.Serializable;
      
      class <warning descr="'Foo' does not define a 'serialVersionUID' field">Foo</warning> implements Serializable { }
    """.trimIndent())
  }

  fun `test quickfix`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_11) // no @Serial annotation for any JDK lower than 14
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      import java.io.Serializable;

      class Fo<caret>o implements Serializable { }
    """.trimIndent(), """
      import java.io.Serializable;

      class Foo implements Serializable {
          private static final long serialVersionUID = -4454552974617678229L;
      }
    """.trimIndent(), "Create constant field 'serialVersionUID' in 'Foo'")
  }

  fun `test quickfix @Serial annotation`() {
    myFixture.setLanguageLevel(LanguageLevel.JDK_14) // @Serial was introduced in JDK 14
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      import java.io.Serializable;

      class Fo<caret>o implements Serializable { }
    """.trimIndent(), """
      import java.io.Serial;
      import java.io.Serializable;

      class Foo implements Serializable {
          @Serial
          private static final long serialVersionUID = -4454552974617678229L;
      }
    """.trimIndent(), "Create constant field 'serialVersionUID' in 'Foo'")
  }
}
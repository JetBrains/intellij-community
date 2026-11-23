package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.jvmAnnotationsProjectFixture
import com.intellij.jvm.analysis.internal.testFramework.suppressionAnnotationInspectionFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaSuppressionAnnotationInspectionTest {
  companion object {
    private val projectFixture = jvmAnnotationsProjectFixture()
  }

  private val myFixture by suppressionAnnotationInspectionFixture(projectFixture)

  @Test
  fun `test highlighting`() {
    myFixture.inspection.myAllowedSuppressions.add("FreeSpeech")
    myFixture.testHighlighting(
      JvmLanguage.JAVA,
      """
      @<warning descr="Annotation suppresses 'ALL' and 'SuppressionAnnotation'">SuppressWarnings</warning>({"ALL", "SuppressionAnnotation"})
      public class A {
        @<warning descr="Annotation suppresses 'PublicField'">SuppressWarnings</warning>("PublicField")
        public String s;
        @<warning descr="Annotation suppresses">SuppressWarnings</warning>({})
        public String t;
      
        void foo() {
          <warning descr="Comment suppresses 'HardCodedStringLiteral'">//noinspection HardCodedStringLiteral</warning>
          System.out.println("hello");
          <warning descr="Comment suppresses">// noinspection</warning>
          System.out.println();
        }
      
        @SuppressWarnings("FreeSpeech")
        void bar() {
          //noinspection FreeSpeech
          System.out.println();
        }
      }
      """.trimIndent(),
      fileName = "A"
    )
  }

  @Test
  fun `test quickfix - remove annotation`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
          public class A {
            @Suppress<caret>Warnings("PublicField", "HardCodedStringLiteral")
            public String s = "test";
          }
        """.trimIndent(), """
          public class A {
            public String s = "test";
          }
        """.trimIndent(), "Remove '@SuppressWarnings' annotation", testPreview = true)
  }

  @Test
  fun `test quickfix - remove comment`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
          public class A {
            //noinspection PublicField, Hard<caret>CodedStringLiteral
            public String s = "test";
          }
        """.trimIndent(), """
          public class A {
            public String s = "test";
          }
        """.trimIndent(), "Remove //noinspection", testPreview = true)
  }

  @Test
  fun `test quickfix - allow a single suppression from annotation`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @Suppress<caret>Warnings("PublicField")
            public String s = "test";
          }
        """.trimIndent(), "PublicField")
  }

  @Test
  fun `test quickfix - allow a single suppression from annotation when array form used`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @Suppress<caret>Warnings({"PublicField"})
            public String s = "test";
          }
        """.trimIndent(), "PublicField")
  }

  @Test
  fun `test quickfix - allow a single suppression from annotation when explicit attribute name exists`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @Suppress<caret>Warnings(value = "PublicField")
            public String s = "test";
          }
        """.trimIndent(), "PublicField")
  }

  @Test
  fun `test quickfix - allow multiple suppressions from annotation when array form used`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @Suppress<caret>Warnings({"PublicField", "HardCodedStringLiteral"})
            public String s = "test";
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  @Test
  fun `test quickfix - allow multiple suppressions from annotation when explicit attribute name exists`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @Suppress<caret>Warnings(value = {"PublicField", "HardCodedStringLiteral"})
            public String s = "test";
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  @Test
  fun `test quickfix - allow multiple suppressions from annotation when constants used`() {
    myFixture.addClass("""
        public final class Constants {
          public static final String PUBLIC_FIELD = "PublicField";
          public static final String HARD_CODED_STRING_LITERAL = "HardCodedStringLiteral";
        }
        """.trimIndent())
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @Suppress<caret>Warnings({Constants.PUBLIC_FIELD, Constants.HARD_CODED_STRING_LITERAL})
            public String s = "test";
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  @Test
  fun `test quickfix - allow a single suppression from comment`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            //noinspection Public<caret>Field
            public String s = "test";
          }
        """.trimIndent(), "PublicField")
  }

  @Test
  fun `test quickfix - allow multiple suppressions from comment`() {
    myFixture.testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            //noinspection Public<caret>Field, Hard<caret>CodedStringLiteral
            public String s = "test";
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }
}

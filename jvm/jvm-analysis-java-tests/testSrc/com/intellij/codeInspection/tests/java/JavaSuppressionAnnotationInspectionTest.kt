package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.SuppressionAnnotationInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaSuppressionAnnotationInspectionTest : SuppressionAnnotationInspectionTestBase() {

  fun `test highlighting`() {
    inspection.myAllowedSuppressions.add("FreeSpeech")
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

  fun `test quickfix - allow a single suppression from annotation`() {
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @Suppress<caret>Warnings("PublicField")
            public String s = "test";
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow a single suppression from annotation when array form used`() {
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @Suppress<caret>Warnings({"PublicField"})
            public String s = "test";
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow a single suppression from annotation when explicit attribute name exists`() {
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @Suppress<caret>Warnings(value = "PublicField")
            public String s = "test";
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow multiple suppressions from annotation when array form used`() {
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @Suppress<caret>Warnings({"PublicField", "HardCodedStringLiteral"})
            public String s = "test";
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  fun `test quickfix - allow multiple suppressions from annotation when explicit attribute name exists`() {
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @Suppress<caret>Warnings(value = {"PublicField", "HardCodedStringLiteral"})
            public String s = "test";
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  fun `test quickfix - allow multiple suppressions from annotation when constants used`() {
    myFixture.addClass("""
        public final class Constants {
          public static final String PUBLIC_FIELD = "PublicField";
          public static final String HARD_CODED_STRING_LITERAL = "HardCodedStringLiteral";
        }
        """.trimIndent())
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            @Suppress<caret>Warnings({Constants.PUBLIC_FIELD, Constants.HARD_CODED_STRING_LITERAL})
            public String s = "test";
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }

  fun `test quickfix - allow a single suppression from comment`() {
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            //noinspection Public<caret>Field
            public String s = "test";
          }
        """.trimIndent(), "PublicField")
  }

  fun `test quickfix - allow multiple suppressions from comment`() {
    testAllowSuppressionQuickFix(JvmLanguage.JAVA, """
          public class A {
            //noinspection Public<caret>Field, Hard<caret>CodedStringLiteral
            public String s = "test";
          }
        """.trimIndent(), "PublicField", "HardCodedStringLiteral")
  }
}
